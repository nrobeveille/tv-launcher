package fr.nrobeveille.tvlauncher.ui.components

import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.Text
import fr.nrobeveille.tvlauncher.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

/** Moment où le fil remonte la photo (la surprise dure 4 s au total). */
private const val YANK_AT_MS = 3_450L
private const val YANK_DURATION_MS = 450

/** Proportions de l'image embarquée (604 × 1080). */
private const val PHOTO_RATIO = 604f / 1080f

private val PolaroidColor = Color(0xFFFAF7F0)
private val ThreadColor = Color(0xE6FFFFFF)
private val BoingColor = Color(0xFFFFC94D)
private val SparkColors = listOf(Color(0xFFFFC94D), Color(0xFF7FB2FF), Color.White, Color(0xFFFF7A9C))

/**
 * Surprise : un polaroïd suspendu à un fil tombe du plafond comme au bout d'un élastique,
 * percute (texte BD, étincelles, son), se balance, puis est remonté d'un coup sec.
 * Le fond reste visible autour. [onImpact] est appelé au moment de l'impact
 * (pour faire trembler l'écran derrière). Retour ferme plus tôt.
 */
@Composable
fun EasterEggOverlay(onImpact: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val currentOnDone by rememberUpdatedState(onDone)
    val currentOnImpact by rememberUpdatedState(onImpact)

    val player = remember { runCatching { MediaPlayer.create(context, R.raw.easter_egg) }.getOrNull() }
    DisposableEffect(Unit) {
        onDispose {
            player?.let {
                runCatching { it.stop() }
                it.release()
            }
        }
    }

    Dialog(
        onDismissRequest = onDone,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // Pas d'assombrissement : les paramètres restent visibles autour de la photo.
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { dialogWindow?.setDimAmount(0f) }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val screenHeightPx = with(density) { maxHeight.toPx() }
            val topPadding = 34.dp
            val photoHeight = maxHeight * 0.64f
            val photoWidth = photoHeight * PHOTO_RATIO
            val topPx = with(density) { topPadding.toPx() }
            val polaroidCenterPx = with(density) { (topPadding + photoHeight / 2 + 10.dp).toPx() }

            val drop = remember { Animatable(-screenHeightPx) }
            val swing = remember { Animatable(0f) }
            val squashX = remember { Animatable(1f) }
            val squashY = remember { Animatable(1f) }
            val sparks = remember { Animatable(0f) }
            val boingScale = remember { Animatable(0f) }
            val boingAlpha = remember { Animatable(0f) }

            LaunchedEffect(Unit) {
                val start = withFrameMillis { it }

                // 1. Chute au bout d'un élastique.
                launch { drop.animateTo(0f, spring(dampingRatio = 0.42f, stiffness = 160f)) }
                snapshotFlow { drop.value }.first { it >= 0f }

                // 2. Impact !
                player?.start()
                currentOnImpact()
                launch { sparks.animateTo(1f, tween(750, easing = LinearOutSlowInEasing)) }
                launch {
                    squashX.snapTo(1.14f)
                    squashY.snapTo(0.84f)
                    launch { squashX.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 380f)) }
                    squashY.animateTo(1f, spring(dampingRatio = 0.35f, stiffness = 380f))
                }
                // 3. Balancier qui s'amortit doucement.
                launch {
                    swing.animateTo(
                        targetValue = 0f,
                        animationSpec = spring(dampingRatio = 0.12f, stiffness = 28f),
                        initialVelocity = 95f,
                    )
                }
                launch {
                    boingAlpha.snapTo(1f)
                    boingScale.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = 500f))
                }
                launch {
                    delay(1_000)
                    boingAlpha.animateTo(0f, tween(300))
                }

                // 4. Remontée express, avec un petit élan vers le bas.
                val elapsed = withFrameMillis { it } - start
                delay((YANK_AT_MS - elapsed).coerceAtLeast(0L))
                val from = drop.value
                drop.animateTo(
                    targetValue = -screenHeightPx * 1.3f,
                    animationSpec = keyframes {
                        durationMillis = YANK_DURATION_MS
                        (from + 70f) at 130 using FastOutSlowInEasing
                    },
                )
                currentOnDone()
            }

            // Le fil, du plafond jusqu'au haut de la photo.
            Canvas(Modifier.fillMaxSize()) {
                val attachY = topPx + drop.value
                if (attachY > 0f) {
                    val x = size.width / 2f
                    drawLine(ThreadColor, Offset(x, 0f), Offset(x, attachY), strokeWidth = 2.dp.toPx())
                }
            }

            // Le polaroïd.
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topPadding)
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        translationY = drop.value
                        rotationZ = swing.value
                        scaleX = squashX.value
                        scaleY = squashY.value
                    }
                    .shadow(18.dp, RoundedCornerShape(6.dp))
                    .background(PolaroidColor, RoundedCornerShape(6.dp))
                    .padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 28.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.easter_egg),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = photoWidth, height = photoHeight)
                        .clip(RoundedCornerShape(3.dp)),
                )
            }

            // Étincelles autour de la photo à l'impact.
            Canvas(Modifier.fillMaxSize()) {
                val p = sparks.value
                if (p <= 0f || p >= 1f) return@Canvas
                val center = Offset(size.width / 2f, polaroidCenterPx)
                val startRadius = photoWidth.toPx() * 0.75f
                val travel = 200.dp.toPx()
                for (i in 0 until 14) {
                    val angle = Math.toRadians(i * (360.0 / 14) + 10.0)
                    val distance = startRadius + travel * p * (0.75f + (i % 3) * 0.15f)
                    val position = center + Offset(cos(angle).toFloat() * distance, sin(angle).toFloat() * distance)
                    sparkle(
                        center = position,
                        radius = 9.dp.toPx() * (1f - p * 0.5f),
                        color = SparkColors[i % SparkColors.size].copy(alpha = 1f - p),
                    )
                }
            }

            // Texte façon bande dessinée.
            Text(
                text = "GAY",
                fontSize = 42.sp,
                fontWeight = FontWeight.Black,
                color = BoingColor,
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.6f), Offset(4f, 4f), 8f)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(x = photoWidth / 2 + 130.dp, y = topPadding + 40.dp)
                    .graphicsLayer {
                        scaleX = boingScale.value
                        scaleY = boingScale.value
                        alpha = boingAlpha.value
                        rotationZ = -14f
                    },
            )
        }
    }
}

/** Petite étoile à 4 branches. */
private fun DrawScope.sparkle(center: Offset, radius: Float, color: Color) {
    val inner = radius * 0.32f
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        lineTo(center.x + inner, center.y - inner)
        lineTo(center.x + radius, center.y)
        lineTo(center.x + inner, center.y + inner)
        lineTo(center.x, center.y + radius)
        lineTo(center.x - inner, center.y + inner)
        lineTo(center.x - radius, center.y)
        lineTo(center.x - inner, center.y - inner)
        close()
    }
    drawPath(path, color)
}
