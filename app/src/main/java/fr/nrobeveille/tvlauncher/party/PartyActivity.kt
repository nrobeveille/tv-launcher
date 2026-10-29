package fr.nrobeveille.tvlauncher.party

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import fr.nrobeveille.tvlauncher.ui.theme.LauncherTheme

/**
 * Mode soirée : l'écran s'illumine au rythme de la musique. Les couleurs sont
 * concentrées sur les bords pour que le boîtier LED les reprenne.
 */
class PartyActivity : ComponentActivity() {

    private val engine = PartyAudioEngine()
    private val prefs by lazy { getSharedPreferences("party", MODE_PRIVATE) }

    private var source by mutableStateOf(PartySource.STICK)
    private var effect by mutableStateOf(PartyEffect.RAINBOW)
    private var message by mutableStateOf<String?>(null)
    private var permissionAsked = false

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { startEngine() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        source = enumOrNull<PartySource>(prefs.getString("source", null)) ?: PartySource.STICK
        effect = enumOrNull<PartyEffect>(prefs.getString("effect", null)) ?: PartyEffect.RAINBOW

        setContent {
            LauncherTheme {
                PartyScreen(
                    engine = engine,
                    source = source,
                    effect = effect,
                    message = message,
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
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        startEngine()
    }

    override fun onStop() {
        engine.stop()
        super.onStop()
    }

    private fun startEngine() {
        val wanted = source
        if (wanted.needsAudio && !hasAudioPermission()) {
            if (!permissionAsked) {
                permissionAsked = true
                engine.start(PartySource.TEMPO)
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                return
            }
            engine.start(PartySource.TEMPO)
            message = "Accès au son refusé : tempo manuel en attendant"
            return
        }
        val error = engine.start(wanted)
        message = error?.let { "$it : tempo manuel en attendant" }
    }

    private fun hasAudioPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private inline fun <reified T : Enum<T>> enumOrNull(name: String?): T? =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
}
