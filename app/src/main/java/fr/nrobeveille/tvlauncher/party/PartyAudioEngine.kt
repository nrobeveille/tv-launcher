package fr.nrobeveille.tvlauncher.party

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.Visualizer
import android.os.SystemClock
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/** D'où vient le rythme. */
enum class PartySource(val label: String, val needsAudio: Boolean) {
    STICK("Son du stick", true),
    MIC("Micro", true),
    TEMPO("Tempo manuel", false),
}

/**
 * Analyse le son en continu et en tire :
 * - [level] : volume relatif, entre 0 et 1 (gain automatique) ;
 * - [beatCount] : incrémenté à chaque temps fort détecté (basses).
 *
 * Les temps forts sont limités à 2,5 par seconde (pas de clignotement trop rapide).
 */
class PartyAudioEngine {

    @Volatile var level: Float = 0f
        private set

    /** Volume brut, pour savoir si un son est capté. */
    @Volatile var rawLevel: Float = 0f
        private set

    @Volatile var beatCount: Int = 0
        private set

    @Volatile var bpm: Float = 120f
        private set

    @Volatile var activeSource: PartySource = PartySource.TEMPO
        private set

    private var visualizer: Visualizer? = null
    private var recordThread: Thread? = null
    @Volatile private var recording = false

    // Détection (protégée par synchronized)
    private var averageBass = 0f
    private var peak = 0f
    private var lastBeatMs = 0L

    // Tempo manuel
    private var tempoStartMs = SystemClock.uptimeMillis()
    private var lastTempoBeat = -1L
    private val taps = ArrayDeque<Long>()

    /**
     * Démarre la source demandée. Renvoie un message d'erreur si elle n'est pas disponible
     * (le moteur passe alors en tempo manuel).
     */
    fun start(source: PartySource): String? {
        stop()
        resetDetection()
        val error = when (source) {
            PartySource.STICK -> startVisualizer()
            PartySource.MIC -> startMicrophone()
            PartySource.TEMPO -> null
        }
        activeSource = if (error == null) source else PartySource.TEMPO
        if (activeSource == PartySource.TEMPO) {
            tempoStartMs = SystemClock.uptimeMillis()
            lastTempoBeat = -1L
        }
        return error
    }

    fun stop() {
        recording = false
        recordThread?.join(300)
        recordThread = null
        visualizer?.let {
            runCatching { it.setEnabled(false) }
            it.release()
        }
        visualizer = null
        level = 0f
        rawLevel = 0f
    }

    /** À appeler à chaque image : génère les temps du tempo manuel. */
    fun tick(nowMs: Long) {
        if (activeSource != PartySource.TEMPO) return
        var period = 60_000f / bpm
        // Au-delà de 150 BPM, on éclaire un temps sur deux.
        if (period < MIN_BEAT_INTERVAL_MS) period *= 2f
        val elapsed = (nowMs - tempoStartMs).coerceAtLeast(0L)
        val index = (elapsed / period).toLong()
        if (index != lastTempoBeat) {
            lastTempoBeat = index
            beatCount++
        }
        val phase = (elapsed % period) / period
        level = exp(-phase * 4f)
        rawLevel = level
    }

    /** Appui sur OK au rythme de la musique : calcule le tempo et cale les temps sur l'appui. */
    fun tap(nowMs: Long) {
        if (taps.isNotEmpty() && nowMs - taps.last() > 2_000L) taps.clear()
        taps.addLast(nowMs)
        while (taps.size > 5) taps.removeFirst()
        if (taps.size >= 2) {
            val interval = (taps.last() - taps.first()).toFloat() / (taps.size - 1)
            bpm = (60_000f / interval).coerceIn(60f, 200f)
        }
        tempoStartMs = nowMs
        lastTempoBeat = -1L
    }

    // --- Son joué par le stick ----------------------------------------------

    private fun startVisualizer(): String? {
        val v = try {
            Visualizer(0) // session 0 = mixage de sortie du stick
        } catch (e: Exception) {
            return "Son du stick inaccessible sur cet appareil"
        } catch (e: UnsatisfiedLinkError) {
            return "Son du stick inaccessible sur cet appareil"
        }
        return try {
            v.setEnabled(false)
            val range = Visualizer.getCaptureSizeRange()
            v.setCaptureSize(range[1].coerceAtMost(1024).coerceAtLeast(range[0]))
            v.setScalingMode(Visualizer.SCALING_MODE_NORMALIZED)
            v.setDataCaptureListener(
                object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(vis: Visualizer?, waveform: ByteArray?, samplingRate: Int) = Unit

                    override fun onFftDataCapture(vis: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft != null) analyzeFft(fft, samplingRate)
                    }
                },
                Visualizer.getMaxCaptureRate(),
                false,
                true,
            )
            v.setEnabled(true)
            visualizer = v
            null
        } catch (e: Exception) {
            v.release()
            "Son du stick inaccessible sur cet appareil"
        }
    }

    private fun analyzeFft(fft: ByteArray, samplingRateMilliHz: Int) {
        val bins = fft.size / 2
        if (bins < 2) return
        val binHz = samplingRateMilliHz / 1000f / fft.size
        var total = 0f
        var bass = 0f
        var bassBins = 0
        for (k in 1 until bins) {
            val re = fft[2 * k].toFloat()
            val im = fft[2 * k + 1].toFloat()
            val power = (re * re + im * im) / (128f * 128f)
            total += power
            if (k * binHz < BASS_LIMIT_HZ) {
                bass += power
                bassBins++
            }
        }
        onAnalysis(
            bass = sqrt(bass / max(bassBins, 1)),
            total = sqrt(total / (bins - 1)),
            minSignal = 0.004f,
        )
    }

    // --- Micro ---------------------------------------------------------------

    @SuppressLint("MissingPermission") // permission vérifiée par l'activité avant le démarrage
    private fun startMicrophone(): String? {
        val rate = 44_100
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return "Aucun micro disponible"
        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                max(minBuffer, 8_192),
            )
        } catch (e: Exception) {
            return "Aucun micro disponible"
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return "Aucun micro disponible"
        }
        try {
            record.startRecording()
        } catch (e: Exception) {
            record.release()
            return "Le micro n'a pas pu démarrer"
        }

        recording = true
        recordThread = Thread({
            val buffer = ShortArray(1024)
            var lowPass = 0f
            val alpha = (2.0 * Math.PI * BASS_LIMIT_HZ / rate).toFloat()
            while (recording) {
                val read = record.read(buffer, 0, buffer.size)
                if (read < 0) break
                if (read == 0) {
                    Thread.sleep(10)
                    continue
                }
                var sum = 0.0
                var bassSum = 0.0
                for (i in 0 until read) {
                    val x = buffer[i] / 32_768f
                    lowPass += alpha * (x - lowPass)
                    sum += x * x
                    bassSum += lowPass * lowPass
                }
                onAnalysis(
                    bass = sqrt(bassSum / read).toFloat(),
                    total = sqrt(sum / read).toFloat(),
                    minSignal = 0.002f,
                )
            }
            runCatching { record.stop() }
            record.release()
        }, "party-mic").apply { start() }
        return null
    }

    // --- Détection commune ---------------------------------------------------

    @Synchronized
    private fun resetDetection() {
        averageBass = 0f
        peak = 0f
        lastBeatMs = 0L
    }

    @Synchronized
    private fun onAnalysis(bass: Float, total: Float, minSignal: Float) {
        val now = SystemClock.uptimeMillis()
        rawLevel = total
        // Gain automatique : le pic redescend lentement.
        peak = max(total, peak * 0.995f)
        level = if (peak > minSignal) (total / peak).coerceIn(0f, 1f) else 0f

        averageBass = if (averageBass == 0f) bass else averageBass * 0.92f + bass * 0.08f
        if (bass > minSignal && bass > averageBass * BEAT_RATIO && now - lastBeatMs >= MIN_BEAT_INTERVAL_MS) {
            lastBeatMs = now
            beatCount++
        }
    }

    private companion object {
        const val BASS_LIMIT_HZ = 160f
        const val BEAT_RATIO = 1.35f
        /** 400 ms minimum entre deux temps forts (2,5 par seconde au plus). */
        const val MIN_BEAT_INTERVAL_MS = 400L
    }
}
