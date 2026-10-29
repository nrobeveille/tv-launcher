package fr.nrobeveille.tvlauncher.party

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import fr.nrobeveille.tvlauncher.lights.WizPartySync
import fr.nrobeveille.tvlauncher.ui.theme.LauncherTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Affichage du test du micro. */
data class MicTestUi(val lines: List<String>, val status: String, val running: Boolean)

/**
 * Mode soirée : l'écran s'illumine au rythme de la musique. Les couleurs sont
 * concentrées sur les bords pour que le boîtier LED les reprenne.
 */
class PartyActivity : ComponentActivity() {

    private val engine by lazy { PartyAudioEngine(applicationContext) }
    private val wizSync by lazy { WizPartySync(applicationContext) }
    private var wizCount by mutableIntStateOf(0)
    private val prefs by lazy { getSharedPreferences("party", MODE_PRIVATE) }

    private var source by mutableStateOf(PartySource.MIC)
    private var effect by mutableStateOf(PartyEffect.RAINBOW)
    private var message by mutableStateOf<String?>(null)
    private var permissionAsked = false

    private var micTest by mutableStateOf<MicTestUi?>(null)
    private var micTestLevel by mutableFloatStateOf(0f)
    private var micTestJob: Job? = null
    private var testAfterPermission = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && testAfterPermission) {
                testAfterPermission = false
                startMicTest()
            } else {
                startEngine()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        source = enumOrNull<PartySource>(prefs.getString("source", null)) ?: PartySource.MIC
        effect = enumOrNull<PartyEffect>(prefs.getString("effect", null)) ?: PartyEffect.RAINBOW
        engine.micConfig = loadMicConfig()

        setContent {
            LauncherTheme {
                PartyScreen(
                    engine = engine,
                    source = source,
                    effect = effect,
                    message = message,
                    micTest = micTest,
                    micTestLevel = micTestLevel,
                    onChangeSource = { delta ->
                        source = PartySource.entries[(source.ordinal + delta).mod(PartySource.entries.size)]
                        prefs.edit().putString("source", source.name).apply()
                        permissionAsked = false
                        startEngine()
                    },
                    onChangeEffect = { delta ->
                        effect = PartyEffect.entries[(effect.ordinal + delta).mod(PartyEffect.entries.size)]
                        prefs.edit().putString("effect", effect.name).apply()
                    },
                    wizCount = wizCount,
                    onBeat = { rgb -> wizSync.onBeat(rgb) },
                    onStartMicTest = { startMicTest() },
                    onCloseMicTest = {
                        micTestJob?.cancel()
                        micTest = null
                        startEngine()
                    },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        wizSync.start()
        wizCount = wizSync.activeCount
        startEngine()
    }

    override fun onStop() {
        micTestJob?.cancel()
        micTest = null
        engine.stop()
        wizSync.stop() // remet les ampoules comme avant
        super.onStop()
    }

    private fun startEngine() {
        if (micTestJob?.isActive == true) return
        val wanted = source
        if (wanted.needsAudio && !hasAudioPermission()) {
            engine.start(PartySource.TEMPO)
            if (!permissionAsked) {
                permissionAsked = true
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                message = "Accès au micro refusé : tempo manuel en attendant"
            }
            return
        }
        val error = engine.start(wanted)
        message = error?.let { "$it : tempo manuel en attendant" }
    }

    /** Essaie toutes les entrées audio, puis vérifie que la meilleure tient dans la durée. */
    private fun startMicTest() {
        if (!hasAudioPermission()) {
            testAfterPermission = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        micTestJob?.cancel()
        engine.stop()
        micTestJob = lifecycleScope.launch {
            val tester = MicTester(applicationContext)
            val lines = mutableListOf<String>()
            fun show(status: String, running: Boolean = true) {
                micTest = MicTestUi(lines.toList(), status, running)
            }
            fun add(line: String) {
                lines += line
                Log.i(LOG_TAG, line)
            }

            val devices = tester.inputDevices().joinToString { MicTester.describe(it) }
            add("Entrées audio : ${devices.ifEmpty { "aucune" }}")

            val results = mutableListOf<ProbeResult>()
            for (config in tester.configs()) {
                show("Essai : ${config.label}")
                val result = tester.probe(config, PROBE_MS) { micTestLevel = it }
                results += result
                add(result.summary)
            }

            val best = results.filter { it.hasSound }.maxByOrNull { it.average }
            if (best == null) {
                add("Aucune entrée ne capte de son.")
                show("Aucun micro utilisable. Retour pour fermer.", running = false)
                return@launch
            }

            add("Meilleure entrée : ${best.config.label}")
            val report = tester.endurance(best.config, ENDURANCE_SECONDS) { second, level ->
                micTestLevel = level
                show("Écoute continue : $second / $ENDURANCE_SECONDS s")
            }
            report.forEach { add(it) }

            saveMicConfig(best.config)
            engine.micConfig = best.config
            source = PartySource.MIC
            prefs.edit().putString("source", source.name).apply()
            show("Terminé : le Mode soirée utilisera « ${best.config.label} ». Retour pour fermer.", running = false)
        }
    }

    private fun loadMicConfig(): MicConfig? {
        if (!prefs.contains("mic_source")) return null
        val type = prefs.getInt("mic_device_type", -1)
        return MicConfig(
            source = prefs.getInt("mic_source", 1),
            sourceName = prefs.getString("mic_source_name", "MIC") ?: "MIC",
            deviceType = type.takeIf { it >= 0 },
            deviceName = prefs.getString("mic_device_name", null),
        )
    }

    private fun saveMicConfig(config: MicConfig) {
        prefs.edit()
            .putInt("mic_source", config.source)
            .putString("mic_source_name", config.sourceName)
            .putInt("mic_device_type", config.deviceType ?: -1)
            .putString("mic_device_name", config.deviceName)
            .apply()
    }

    private fun hasAudioPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

    private companion object {
        const val LOG_TAG = "MicTest"
        const val PROBE_MS = 2_500L
        const val ENDURANCE_SECONDS = 45
    }
}
