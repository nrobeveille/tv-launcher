package fr.nrobeveille.tvlauncher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import fr.nrobeveille.tvlauncher.data.Weather
import fr.nrobeveille.tvlauncher.data.WeatherKind
import kotlin.math.sin
import kotlin.random.Random

/** Les animations repartent de zéro toutes les heures (évite la perte de précision des Float). */
private const val TIME_WRAP_SECONDS = 3_600.0

private val RainColor = Color(0xFF9CC3FF)
private val SunGlow = Color(0x40FFC94D)
private val MoonGlow = Color(0x226F8CFF)

/**
 * Fond animé qui suit la météo : ciel de jour ou de nuit, étoiles, nuages,
 * pluie, neige, brouillard, éclairs. Sans données météo : dégradé nuit fixe.
 */
@Composable
fun WeatherBackground(weather: Weather?, modifier: Modifier = Modifier) {
    val time = remember { mutableFloatStateOf(0f) }
    val particles = remember { Particles(Random(2026)) }
    val kind = weather?.kind
    val isDay = weather?.isDay ?: false

    if (kind != null) {
        LaunchedEffect(Unit) {
            val start = withFrameNanos { it }
            while (true) {
                withFrameNanos { now ->
                    time.floatValue = (((now - start) / 1_000_000_000.0) % TIME_WRAP_SECONDS).toFloat()
                }
            }
        }
    }

    // Couche séparée : seul le fond est redessiné à chaque image, pas l'interface.
    Canvas(modifier.fillMaxSize().graphicsLayer()) {
        val t = time.floatValue
        drawRect(Brush.verticalGradient(skyColors(kind, isDay)))
        when (kind) {
            null -> Unit
            WeatherKind.CLEAR -> if (isDay) sunGlow(t, 1f) else stars(particles, t, 1f)
            WeatherKind.PARTLY -> {
                if (isDay) sunGlow(t, 0.6f) else stars(particles, t, 0.5f)
                clouds(particles, t, count = 3, alpha = 0.05f)
            }
            WeatherKind.CLOUDY -> clouds(particles, t, count = 6, alpha = 0.07f)
            WeatherKind.FOG -> fog(t)
            WeatherKind.RAIN -> {
                clouds(particles, t, count = 4, alpha = 0.04f)
                rain(particles, t, count = 90)
            }
            WeatherKind.SNOW -> {
                clouds(particles, t, count = 4, alpha = 0.04f)
                snow(particles, t)
            }
            WeatherKind.STORM -> {
                clouds(particles, t, count = 5, alpha = 0.05f)
                rain(particles, t, count = 110)
                lightning(t)
            }
        }
    }
}

/** Couleurs du ciel (haut → bas), assez sombres pour garder le texte lisible. */
private fun skyColors(kind: WeatherKind?, isDay: Boolean): List<Color> = when (kind) {
    null -> listOf(Color(0xFF0B1020), Color(0xFF1B2347))
    WeatherKind.CLEAR -> if (isDay) listOf(Color(0xFF0B2747), Color(0xFF1A5182))
        else listOf(Color(0xFF050914), Color(0xFF141B38))
    WeatherKind.PARTLY -> if (isDay) listOf(Color(0xFF0F2742), Color(0xFF29496A))
        else listOf(Color(0xFF070B17), Color(0xFF1A2138))
    WeatherKind.CLOUDY -> if (isDay) listOf(Color(0xFF161C26), Color(0xFF2C3646))
        else listOf(Color(0xFF0E121A), Color(0xFF1E2532))
    WeatherKind.FOG -> listOf(Color(0xFF1C2229), Color(0xFF38414C))
    WeatherKind.RAIN -> listOf(Color(0xFF0D131D), Color(0xFF1C2735))
    WeatherKind.SNOW -> listOf(Color(0xFF1A2232), Color(0xFF36425A))
    WeatherKind.STORM -> listOf(Color(0xFF07090F), Color(0xFF1A1D2A))
}

/** Positions et vitesses tirées une fois pour toutes. */
private class Particles(random: Random) {
    val rainX = FloatArray(110) { random.nextFloat() * 1.3f }
    val rainSpeed = FloatArray(110) { 0.75f + random.nextFloat() * 0.5f }
    val rainPhase = FloatArray(110) { random.nextFloat() }
    val rainLength = FloatArray(110) { 0.6f + random.nextFloat() * 0.6f }

    val snowX = FloatArray(70) { random.nextFloat() }
    val snowSpeed = FloatArray(70) { 0.04f + random.nextFloat() * 0.06f }
    val snowPhase = FloatArray(70) { random.nextFloat() }
    val snowSize = FloatArray(70) { 0.6f + random.nextFloat() * 0.8f }

    val starX = FloatArray(80) { random.nextFloat() }
    val starY = FloatArray(80) { random.nextFloat() * 0.75f }
    val starSize = FloatArray(80) { 0.5f + random.nextFloat() }
    val starSpeed = FloatArray(80) { 0.6f + random.nextFloat() * 1.6f }
    val starPhase = FloatArray(80) { random.nextFloat() * 6.28f }

    val cloudX = FloatArray(6) { random.nextFloat() * 1.6f }
    val cloudY = FloatArray(6) { 0.08f + random.nextFloat() * 0.5f }
    val cloudRadius = FloatArray(6) { 0.12f + random.nextFloat() * 0.14f }
    val cloudSpeed = FloatArray(6) { 0.004f + random.nextFloat() * 0.006f }
}

private fun DrawScope.sunGlow(t: Float, strength: Float) {
    val center = Offset(size.width * 0.86f, size.height * 0.1f)
    val radius = size.width * 0.5f * (1f + 0.03f * sin(t * 0.5f))
    drawCircle(
        brush = Brush.radialGradient(
            listOf(SunGlow.copy(alpha = SunGlow.alpha * strength), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

private fun DrawScope.stars(p: Particles, t: Float, density: Float) {
    val moonCenter = Offset(size.width * 0.86f, size.height * 0.12f)
    val moonRadius = size.width * 0.35f
    drawCircle(
        brush = Brush.radialGradient(listOf(MoonGlow, Color.Transparent), center = moonCenter, radius = moonRadius),
        radius = moonRadius,
        center = moonCenter,
    )
    val count = (p.starX.size * density).toInt()
    val base = 1.3.dp.toPx()
    for (i in 0 until count) {
        val twinkle = 0.5f + 0.5f * sin(t * p.starSpeed[i] + p.starPhase[i])
        drawCircle(
            color = Color.White.copy(alpha = 0.2f + 0.55f * twinkle),
            radius = base * p.starSize[i],
            center = Offset(p.starX[i] * size.width, p.starY[i] * size.height),
        )
    }
}

private fun DrawScope.clouds(p: Particles, t: Float, count: Int, alpha: Float) {
    for (i in 0 until count.coerceAtMost(p.cloudX.size)) {
        val x = ((p.cloudX[i] + t * p.cloudSpeed[i]) % 1.6f - 0.3f) * size.width
        val center = Offset(x, p.cloudY[i] * size.height)
        val radius = p.cloudRadius[i] * size.width
        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color.White.copy(alpha = alpha), Color.Transparent),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )
    }
}

private fun DrawScope.rain(p: Particles, t: Float, count: Int) {
    val baseLength = 26.dp.toPx()
    val stroke = 1.5.dp.toPx()
    val color = RainColor.copy(alpha = 0.22f)
    for (i in 0 until count.coerceAtMost(p.rainX.size)) {
        val progress = ((p.rainPhase[i] + t * p.rainSpeed[i]) % 1f) * 1.15f - 0.075f
        val y = progress * size.height
        val x = (p.rainX[i] - 0.1f - progress * 0.12f) * size.width
        val length = baseLength * p.rainLength[i]
        drawLine(color, Offset(x, y), Offset(x - length * 0.25f, y + length), stroke, StrokeCap.Round)
    }
}

private fun DrawScope.snow(p: Particles, t: Float) {
    val sway = 18.dp.toPx()
    val base = 2.4.dp.toPx()
    for (i in p.snowX.indices) {
        val progress = ((p.snowPhase[i] + t * p.snowSpeed[i]) % 1f) * 1.1f - 0.05f
        val x = p.snowX[i] * size.width + sin(t * 0.9f + p.snowPhase[i] * 12f) * sway
        drawCircle(Color.White.copy(alpha = 0.55f), base * p.snowSize[i], Offset(x, progress * size.height))
    }
}

private fun DrawScope.fog(t: Float) {
    val bandHeight = size.height * 0.16f
    for (i in 0 until 4) {
        val y = size.height * (0.28f + i * 0.18f)
        val shift = sin(t * 0.07f + i * 1.7f) * 0.15f * size.width
        drawRect(
            brush = Brush.horizontalGradient(
                listOf(Color.Transparent, Color.White.copy(alpha = 0.07f), Color.Transparent),
                startX = shift - size.width * 0.15f,
                endX = shift + size.width * 1.15f,
            ),
            topLeft = Offset(0f, y - bandHeight / 2f),
            size = Size(size.width, bandHeight),
        )
    }
}

/** Double éclair toutes les ~10 s. */
private fun DrawScope.lightning(t: Float) {
    val cycle = t % 9.7f
    val alpha = when {
        cycle < 0.08f -> 0.3f * (1f - cycle / 0.08f)
        cycle in 0.18f..0.26f -> 0.2f * (1f - (cycle - 0.18f) / 0.08f)
        else -> 0f
    }
    if (alpha > 0f) drawRect(Color.White.copy(alpha = alpha))
}
