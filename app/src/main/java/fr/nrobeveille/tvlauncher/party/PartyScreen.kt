package fr.nrobeveille.tvlauncher.party

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import fr.nrobeveille.tvlauncher.ui.theme.Accent
import fr.nrobeveille.tvlauncher.ui.theme.SubtleText
import kotlinx.coroutines.delay
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Effets lumineux, pensés pour les bords de l'écran (repris par le boîtier LED). */
enum class PartyEffect(val label: String) {
    RAINBOW("Arc-en-ciel"),
    FLASH("Flash couleur"),
    PULSE("Pulsation"),
}

private val WarningColor = Color(0xFFFFC94D)

/** État visuel, mis à jour à chaque image. */
@Stable
private class PartyVisual {
    var hue by mutableFloatStateOf(0f)
    var flash by mutableFloatStateOf(0f)
    var energy by mutableFloatStateOf(0f)
    var beatHue by mutableFloatStateOf(200f)

    fun update(dt: Float, level: Float) {
        energy += (level - energy) * min(1f, dt * 10f)
        flash *= exp(-dt * 4.5f)
        hue = wrapHue(hue + dt * (20f + 70f * energy))
    }

    fun onBeat() {
        flash = 1f
        hue = wrapHue(hue + 30f)
        beatHue = wrapHue(beatHue + 137.5f) // angle d'or : couleurs bien différentes à chaque temps
    }
}

@Composable
fun PartyScreen(
    engine: PartyAudioEngine,
    source: PartySource,
    effect: PartyEffect,
    message: String?,
    micTest: MicTestUi?,
    micTestLevel: Float,
    wizCount: Int,
    onBeat: (Int) -> Unit,
    onChangeSource: (Int) -> Unit,
    onChangeEffect: (Int) -> Unit,
    onStartMicTest: () -> Unit,
    onCloseMicTest: () -> Unit,
) {
    val visual = remember { PartyVisual() }
    val focus = remember { FocusRequester() }
    var hudToken by remember { mutableIntStateOf(0) }
    var hudVisible by remember { mutableStateOf(true) }
    var noSound by remember { mutableStateOf(false) }
    var activeSource by remember { mutableStateOf(engine.activeSource) }
    var shownBpm by remember { mutableIntStateOf(engine.bpm.roundToInt()) }

    // La boucle tourne en continu : elle doit toujours lire l'effet et le callback à jour.
    val currentEffect by rememberUpdatedState(effect)
    val currentOnBeat by rememberUpdatedState(onBeat)

    // Boucle d'animation : lit le moteur audio et fait évoluer les couleurs.
    LaunchedEffect(Unit) {
        var last = withFrameNanos { it }
        var lastBeats = engine.beatCount
        var silentFor = 0f
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - last) / 1_000_000_000f).coerceIn(0f, 0.1f)
            last = now

            engine.tick(SystemClock.uptimeMillis())
            val beats = engine.beatCount
            if (beats != lastBeats) {
                lastBeats = beats
                visual.onBeat()
                // Couleur du temps fort, envoyée aux ampoules WiZ si la synchro est active.
                val hue = if (currentEffect == PartyEffect.FLASH) visual.beatHue else visual.hue
                currentOnBeat(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
            }
            visual.update(dt, engine.level)

            val current = engine.activeSource
            if (current != activeSource) activeSource = current
            silentFor = if (current != PartySource.TEMPO && engine.rawLevel < 0.003f) silentFor + dt else 0f
            val silent = silentFor > 6f
            if (silent != noSound) noSound = silent
            val bpm = engine.bpm.roundToInt()
            if (bpm != shownBpm) shownBpm = bpm
        }
    }

    // Les infos s'affichent 4,5 s après chaque touche (ou un message), puis disparaissent.
    LaunchedEffect(hudToken, message, noSound) {
        hudVisible = true
        delay(4_500)
        hudVisible = false
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val hudAlpha by animateFloatAsState(if (hudVisible && micTest == null) 1f else 0f, tween(400), label = "hud")

    // Retour ferme le test du micro au lieu de quitter le Mode soirée.
    BackHandler(enabled = micTest != null) { onCloseMicTest() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                if (micTest != null) return@onKeyEvent true // pendant le test, on ignore les touches
                when (event.key) {
                    Key.DirectionLeft -> onChangeSource(-1)
                    Key.DirectionRight -> onChangeSource(1)
                    Key.DirectionUp -> onChangeEffect(-1)
                    Key.DirectionDown -> onChangeEffect(1)
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        when {
                            source == PartySource.MIC -> onStartMicTest()
                            engine.activeSource == PartySource.TEMPO -> engine.tap(SystemClock.uptimeMillis())
                        }
                    }
                    else -> return@onKeyEvent false
                }
                hudToken++
                true
            }
            .focusRequester(focus)
            .focusable(),
    ) {
        Canvas(Modifier.fillMaxSize()) { drawEffect(effect, visual) }

        // Infos au centre : les LEDs ne lisent que les bords de l'image.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { alpha = hudAlpha }
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
                .padding(horizontal = 32.dp, vertical = 20.dp),
        ) {
            Text("Mode soirée", fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                Text("◀ ▶  Source : ${source.label}", fontSize = 17.sp)
                Text("▲ ▼  Effet : ${effect.label}", fontSize = 17.sp)
            }
            if (source == PartySource.MIC) {
                Text("OK : tester le micro (environ 1 min, avec de la musique)", fontSize = 15.sp, color = SubtleText)
            } else if (activeSource == PartySource.TEMPO) {
                Text(
                    "OK au rythme de la musique pour caler le tempo · $shownBpm BPM",
                    fontSize = 15.sp,
                    color = SubtleText,
                )
            }
            Box(
                Modifier
                    .width(280.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .drawBehind {
                        drawRect(Accent, size = Size(size.width * visual.energy.coerceIn(0f, 1f), size.height))
                    },
            )
            if (message != null) {
                Text(message, fontSize = 15.sp, color = WarningColor)
            } else if (noSound) {
                Text("Aucun son capté : ◀ ▶ pour essayer une autre source", fontSize = 15.sp, color = WarningColor)
            }
            if (wizCount > 0) {
                Text("Ampoules WiZ synchronisées : $wizCount", fontSize = 14.sp, color = SubtleText)
            }
            Text("Retour pour quitter", fontSize = 13.sp, color = SubtleText)
        }

        if (micTest != null) MicTestPanel(micTest, micTestLevel, Modifier.align(Alignment.Center))
    }
}

/** Résultats du test du micro, affichés au fur et à mesure. */
@Composable
private fun MicTestPanel(test: MicTestUi, level: Float, modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .widthIn(max = 820.dp)
            .background(Color.Black.copy(alpha = 0.88f), RoundedCornerShape(18.dp))
            .padding(horizontal = 32.dp, vertical = 24.dp),
    ) {
        Text("Test du micro", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Mets de la musique et pose la télécommande près de l'enceinte.",
            fontSize = 15.sp,
            color = SubtleText,
        )
        Text(test.status, fontSize = 16.sp, color = if (test.running) Color.White else WarningColor)
        if (test.running) {
            Box(
                Modifier
                    .width(320.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White.copy(alpha = 0.15f))
                    .drawBehind {
                        // Le niveau d'un micro est faible : on l'amplifie pour l'affichage.
                        drawRect(Accent, size = Size(size.width * (level * 8f).coerceIn(0f, 1f), size.height))
                    },
            )
        }
        test.lines.takeLast(MAX_LINES).forEach { line ->
            Text(line, fontSize = 13.sp, fontFamily = FontFamily.Monospace, maxLines = 2)
        }
    }
}

private const val MAX_LINES = 16

private fun DrawScope.drawEffect(effect: PartyEffect, v: PartyVisual) {
    when (effect) {
        PartyEffect.RAINBOW -> {
            // Arc-en-ciel qui tourne autour de l'écran, plus lumineux sur chaque temps.
            val value = (0.18f + 0.47f * v.energy + 0.35f * v.flash).coerceIn(0f, 1f)
            val colors = List(7) { i -> Color.hsv(wrapHue(v.hue + i * 60f), 1f, value) }
            drawRect(Brush.sweepGradient(colors, center))
            drawRect(
                Brush.radialGradient(
                    listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent),
                    center = center,
                    radius = size.minDimension * 0.6f,
                ),
            )
        }
        PartyEffect.FLASH -> {
            // Tout l'écran change de couleur à chaque temps, puis s'éteint doucement.
            val value = (0.05f + 0.95f * v.flash).coerceIn(0f, 1f)
            drawRect(Color.hsv(wrapHue(v.beatHue), 1f, value))
        }
        PartyEffect.PULSE -> {
            // Deux couleurs opposées (gauche / droite) qui respirent avec les basses.
            val value = (0.12f + 0.88f * v.energy.coerceIn(0f, 1f).pow(0.7f)).coerceIn(0f, 1f)
            drawRect(
                Brush.horizontalGradient(
                    listOf(
                        Color.hsv(wrapHue(v.hue), 1f, value),
                        Color.hsv(wrapHue(v.hue + 150f), 1f, value),
                    ),
                ),
            )
        }
    }
}

private fun wrapHue(hue: Float): Float = ((hue % 360f) + 360f) % 360f
