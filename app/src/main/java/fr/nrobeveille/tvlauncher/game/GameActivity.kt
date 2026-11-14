package fr.nrobeveille.tvlauncher.game

import android.content.Context
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext

/** Debug Road : un jeu de traversée à la sauce informatique, jouable à la télécommande. */
class GameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent { DebugRoadScreen() }
    }
}

@Composable
private fun DebugRoadScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("debug_road", Context.MODE_PRIVATE) }
    var best by remember { mutableIntStateOf(prefs.getInt("best", 0)) }
    var newRecord by remember { mutableStateOf(false) }
    var game by remember { mutableStateOf(DebugRoadGame()) }
    var frame by remember { mutableLongStateOf(0L) }
    val renderer = remember { GameRenderer() }
    val focus = remember { FocusRequester() }

    LaunchedEffect(game) {
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                val dt = ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                last = now
                game.update(dt)
                if (game.justDied) {
                    game.justDied = false
                    newRecord = game.score > best
                    if (newRecord) best = game.score
                    prefs.edit()
                        .putInt("best", best)
                        .putInt("coffees", prefs.getInt("coffees", 0) + game.coffees)
                        .apply()
                }
                frame++
            }
        }
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { focus.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1020))
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> game.move(0, 1)
                    Key.DirectionDown -> game.move(0, -1)
                    Key.DirectionLeft -> game.move(-1, 0)
                    Key.DirectionRight -> game.move(1, 0)
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> when (game.phase) {
                        Phase.DEAD -> if (game.deathTime > 1.3f) {
                            newRecord = false
                            game = DebugRoadGame()
                        }
                        Phase.READY -> game.move(0, 1)
                        else -> Unit
                    }
                    else -> return@onKeyEvent false
                }
                true
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            @Suppress("UNUSED_EXPRESSION")
            frame // redessine à chaque image
            renderer.draw(this, game, best, newRecord)
        }
    }
}
