package fr.nrobeveille.tvlauncher.ui.components

import android.media.MediaPlayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import fr.nrobeveille.tvlauncher.R
import kotlinx.coroutines.delay

/** Durée d'affichage de la surprise. */
private const val EASTER_EGG_DURATION_MS = 4_000L

/**
 * Surprise plein écran : l'image s'affiche pendant 4 secondes et le son est joué une fois
 * (coupé au bout des 4 secondes s'il est plus long). Retour ferme plus tôt.
 */
@Composable
fun EasterEggOverlay(onDone: () -> Unit) {
    val context = LocalContext.current
    val currentOnDone by rememberUpdatedState(onDone)

    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.easter_egg),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    DisposableEffect(Unit) {
        val player = runCatching { MediaPlayer.create(context, R.raw.easter_egg) }.getOrNull()
        player?.start()
        onDispose {
            player?.let {
                runCatching { it.stop() }
                it.release()
            }
        }
    }

    LaunchedEffect(Unit) {
        delay(EASTER_EGG_DURATION_MS)
        currentOnDone()
    }
}
