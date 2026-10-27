package fr.nrobeveille.tvlauncher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

/** Garde un FocusRequester par clé (paquet, entrée de menu…) pour redonner le focus. */
class FocusRegistry {
    private val requesters = HashMap<String, FocusRequester>()

    fun requester(key: String): FocusRequester = requesters.getOrPut(key) { FocusRequester() }

    /** Donne le focus à l'élément ; renvoie false s'il n'est pas (encore) affiché. */
    fun request(key: String): Boolean {
        val requester = requesters[key] ?: return false
        return runCatching { requester.requestFocus() }.isSuccess
    }
}

/** Attend que l'écran ait été dessiné (les listes paresseuses composent leurs éléments). */
suspend fun awaitLayout() {
    withFrameNanos { }
    withFrameNanos { }
}

fun KeyEvent.isSelectKey(): Boolean =
    key == Key.DirectionCenter || key == Key.Enter || key == Key.NumPadEnter

/**
 * Ignore la touche OK tant qu'elle n'a pas été relâchée puis réappuyée.
 *
 * Utile pour les menus ouverts par un appui long : sans ça, la touche encore
 * enfoncée (ou son relâchement) validerait immédiatement le premier choix.
 */
@Composable
fun Modifier.ignoreHeldSelectKey(): Modifier {
    val freshPress = remember { BooleanArray(1) }
    return this.onPreviewKeyEvent { event ->
        if (!event.isSelectKey()) return@onPreviewKeyEvent false
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (event.nativeKeyEvent.repeatCount == 0) {
                    freshPress[0] = true
                    false
                } else {
                    !freshPress[0]
                }
            }
            KeyEventType.KeyUp -> {
                if (freshPress[0]) {
                    freshPress[0] = false
                    false
                } else {
                    true
                }
            }
            else -> false
        }
    }
}
