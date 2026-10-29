package fr.nrobeveille.tvlauncher.party

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.max
import kotlin.math.sqrt

/** Une façon d'ouvrir le micro : source Android + entrée audio préférée (facultative). */
data class MicConfig(
    val source: Int,
    val sourceName: String,
    val deviceType: Int? = null,
    val deviceName: String? = null,
) {
    val label: String get() = if (deviceName == null) sourceName else "$sourceName · $deviceName"
}

class ProbeResult(
    val config: MicConfig,
    val error: String?,
    val routedTo: String?,
    val gotData: Boolean,
    val peak: Float,
    val average: Float,
) {
    val hasSound: Boolean get() = error == null && gotData && peak >= MicTester.SOUND_THRESHOLD

    val summary: String
        get() {
            val via = routedTo?.let { " → $it" }.orEmpty()
            return when {
                error != null -> "✗ ${config.label} : $error"
                !gotData -> "✗ ${config.label} : aucune donnée reçue$via"
                !hasSound -> "• ${config.label} : silence (${MicTester.level(peak)})$via"
                else -> "✓ ${config.label} : son capté (${MicTester.level(peak)})$via"
            }
        }
}

/**
 * Teste les différentes façons d'ouvrir un micro sur l'appareil (micro de la télécommande,
 * USB, etc.) et mesure si le son tient dans la durée.
 */
class MicTester(context: Context) {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    fun inputDevices(): List<AudioDeviceInfo> =
        audioManager?.getDevices(AudioManager.GET_DEVICES_INPUTS)?.toList().orEmpty()

    /** Toutes les configurations à essayer. */
    fun configs(): List<MicConfig> {
        val list = SOURCES.map { (source, name) -> MicConfig(source, name) }.toMutableList()
        for (device in inputDevices()) {
            list += MicConfig(MediaRecorder.AudioSource.MIC, "MIC", device.type, describe(device))
        }
        return list
    }

    /** Ouvre et démarre l'enregistrement. Renvoie l'AudioRecord ou un message d'erreur. */
    @SuppressLint("MissingPermission") // vérifiée par l'activité
    fun open(config: MicConfig): Pair<AudioRecord?, String?> {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return null to "format audio non pris en charge"
        val record = try {
            AudioRecord.Builder()
                .setAudioSource(config.source)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build()
                )
                .setBufferSizeInBytes(max(minBuffer, 8_192))
                .build()
        } catch (e: Exception) {
            return null to "refusé (${e.javaClass.simpleName})"
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return null to "initialisation impossible"
        }
        if (config.deviceType != null) {
            val device = inputDevices().firstOrNull { it.type == config.deviceType && describe(it) == config.deviceName }
                ?: inputDevices().firstOrNull { it.type == config.deviceType }
            if (device != null) record.setPreferredDevice(device)
        }
        try {
            record.startRecording()
        } catch (e: Exception) {
            record.release()
            return null to "démarrage impossible (${e.javaClass.simpleName})"
        }
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            record.release()
            return null to "démarrage impossible"
        }
        return record to null
    }

    /** Écoute [durationMs] ms avec une configuration et mesure le niveau. */
    suspend fun probe(config: MicConfig, durationMs: Long, onLevel: (Float) -> Unit): ProbeResult =
        withContext(Dispatchers.IO) {
            val (record, error) = open(config)
            if (record == null) return@withContext ProbeResult(config, error, null, false, 0f, 0f)
            val routed = record.routedDevice?.let { describe(it) }
            val buffer = ShortArray(1024)
            var gotData = false
            var peak = 0f
            var sum = 0.0
            var chunks = 0
            val end = SystemClock.uptimeMillis() + durationMs
            try {
                while (SystemClock.uptimeMillis() < end) {
                    ensureActive()
                    val read = record.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                    if (read < 0) break
                    if (read == 0) {
                        Thread.sleep(15)
                        continue
                    }
                    gotData = true
                    val level = rms(buffer, read)
                    peak = max(peak, level)
                    sum += level
                    chunks++
                    onLevel(level)
                }
            } finally {
                runCatching { record.stop() }
                record.release()
                onLevel(0f)
            }
            ProbeResult(config, null, routed, gotData, peak, if (chunks > 0) (sum / chunks).toFloat() else 0f)
        }

    /**
     * Écoute en continu pendant [seconds] secondes. Si le son disparaît plus de 4 s,
     * le micro est rouvert (comme le fera le Mode soirée). Renvoie les lignes du rapport.
     */
    suspend fun endurance(config: MicConfig, seconds: Int, onTick: (second: Int, level: Float) -> Unit): List<String> =
        withContext(Dispatchers.IO) {
            var (record, error) = open(config)
            if (record == null) return@withContext listOf("✗ Écoute continue impossible : $error")
            val perSecond = FloatArray(seconds)
            val events = mutableListOf<String>()
            val buffer = ShortArray(1024)
            val start = SystemClock.uptimeMillis()
            var lastSound = start
            var lastReopen = start
            var lastSecond = -1
            var isOpen = true
            try {
                while (true) {
                    ensureActive()
                    val now = SystemClock.uptimeMillis()
                    val second = ((now - start) / 1000).toInt()
                    if (second >= seconds) break
                    val read = record!!.read(buffer, 0, buffer.size, AudioRecord.READ_NON_BLOCKING)
                    if (read > 0) {
                        val level = rms(buffer, read)
                        perSecond[second] = max(perSecond[second], level)
                        if (level >= SOUND_THRESHOLD) lastSound = now
                    } else {
                        Thread.sleep(15)
                    }
                    if (second != lastSecond) {
                        lastSecond = second
                        onTick(second + 1, if (second > 0) perSecond[second - 1] else 0f)
                    }
                    if (now - lastSound > 4_000 && now - lastReopen > 4_000) {
                        events += "↻ micro rouvert à $second s"
                        runCatching { record.stop() }
                        record.release()
                        isOpen = false
                        val reopened = open(config)
                        record = reopened.first
                        if (record == null) {
                            events += "✗ réouverture impossible : ${reopened.second}"
                            break
                        }
                        isOpen = true
                        lastReopen = now
                    }
                }
            } finally {
                if (isOpen) record?.let {
                    runCatching { it.stop() }
                    it.release()
                }
            }

            val withSound = perSecond.count { it >= SOUND_THRESHOLD }
            val lastWithSound = perSecond.indexOfLast { it >= SOUND_THRESHOLD }
            val timeline = perSecond.joinToString("") { if (it >= SOUND_THRESHOLD) "█" else "·" }
            val verdict = when {
                withSound == 0 -> "✗ Aucun son pendant l'écoute continue"
                perSecond.takeLast(10).count { it >= SOUND_THRESHOLD } >= 7 ->
                    "✓ Le son tient sur $seconds s ($withSound s avec du son)"
                else -> "✗ Plus de son après ${lastWithSound + 1} s ($withSound s avec du son)"
            }
            listOf(verdict, timeline) + events
        }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val SOUND_THRESHOLD = 0.005f

        val DEFAULT_CONFIG = MicConfig(MediaRecorder.AudioSource.MIC, "MIC")

        private val SOURCES = listOf(
            MediaRecorder.AudioSource.MIC to "MIC",
            MediaRecorder.AudioSource.VOICE_RECOGNITION to "VOICE_RECOGNITION",
            MediaRecorder.AudioSource.DEFAULT to "DEFAULT",
            MediaRecorder.AudioSource.UNPROCESSED to "UNPROCESSED",
            MediaRecorder.AudioSource.CAMCORDER to "CAMCORDER",
        )

        fun rms(buffer: ShortArray, count: Int): Float {
            var sum = 0.0
            for (i in 0 until count) {
                val x = buffer[i] / 32_768.0
                sum += x * x
            }
            return sqrt(sum / count).toFloat()
        }

        fun describe(device: AudioDeviceInfo): String {
            val type = when (device.type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "micro intégré"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth SCO"
                AudioDeviceInfo.TYPE_BLE_HEADSET -> "Bluetooth LE"
                AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "casque filaire"
                AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "remote submix"
                AudioDeviceInfo.TYPE_HDMI -> "HDMI"
                AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI ARC"
                AudioDeviceInfo.TYPE_TV_TUNER -> "tuner TV"
                AudioDeviceInfo.TYPE_FM_TUNER -> "tuner FM"
                AudioDeviceInfo.TYPE_LINE_ANALOG -> "entrée ligne"
                AudioDeviceInfo.TYPE_TELEPHONY -> "téléphonie"
                else -> "type ${device.type}"
            }
            val product = device.productName?.toString()?.trim().orEmpty()
            return if (product.isEmpty()) type else "$type ($product)"
        }

        fun level(value: Float): String = String.format(Locale.FRANCE, "niveau %.3f", value)
    }
}
