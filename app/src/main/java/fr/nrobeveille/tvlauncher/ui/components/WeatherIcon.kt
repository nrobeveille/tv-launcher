package fr.nrobeveille.tvlauncher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import fr.nrobeveille.tvlauncher.data.WeatherKind
import kotlin.math.cos
import kotlin.math.sin

private val SunColor = Color(0xFFFFC94D)
private val MoonColor = Color(0xFFE6E9F2)
private val CloudColor = Color(0xFFE8ECF4)
private val DarkCloudColor = Color(0xFFAEB6C6)
private val RainColor = Color(0xFF7FB2FF)
private val FogColor = Color(0xFFC9CFDB)

/** Petite icône météo dessinée à la main (aucune image à embarquer). */
@Composable
fun WeatherIcon(
    kind: WeatherKind,
    isDay: Boolean,
    description: String,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.semantics { contentDescription = description }) {
        val s = size.minDimension
        when (kind) {
            WeatherKind.CLEAR -> {
                if (isDay) sun(Offset(s * 0.5f, s * 0.5f), s * 0.2f)
                else moon(Offset(s * 0.5f, s * 0.5f), s * 0.3f)
            }
            WeatherKind.PARTLY -> {
                if (isDay) sun(Offset(s * 0.36f, s * 0.36f), s * 0.15f)
                else moon(Offset(s * 0.38f, s * 0.34f), s * 0.22f)
                cloud(Offset(s * 0.18f, s * 0.4f), s * 0.78f, CloudColor)
            }
            WeatherKind.CLOUDY -> cloud(Offset(s * 0.06f, s * 0.2f), s * 0.88f, CloudColor)
            WeatherKind.FOG -> {
                val stroke = s * 0.08f
                drawLine(FogColor, Offset(s * 0.12f, s * 0.34f), Offset(s * 0.88f, s * 0.34f), stroke, StrokeCap.Round)
                drawLine(FogColor, Offset(s * 0.22f, s * 0.52f), Offset(s * 0.78f, s * 0.52f), stroke, StrokeCap.Round)
                drawLine(FogColor, Offset(s * 0.12f, s * 0.70f), Offset(s * 0.70f, s * 0.70f), stroke, StrokeCap.Round)
            }
            WeatherKind.RAIN -> {
                cloud(Offset(s * 0.08f, s * 0.06f), s * 0.84f, CloudColor)
                val stroke = s * 0.07f
                for (x in listOf(0.32f, 0.52f, 0.72f)) {
                    drawLine(
                        RainColor,
                        Offset(s * x, s * 0.68f),
                        Offset(s * (x - 0.07f), s * 0.88f),
                        stroke,
                        StrokeCap.Round,
                    )
                }
            }
            WeatherKind.SNOW -> {
                cloud(Offset(s * 0.08f, s * 0.06f), s * 0.84f, CloudColor)
                val r = s * 0.05f
                drawCircle(CloudColor, r, Offset(s * 0.30f, s * 0.74f))
                drawCircle(CloudColor, r, Offset(s * 0.50f, s * 0.86f))
                drawCircle(CloudColor, r, Offset(s * 0.70f, s * 0.74f))
            }
            WeatherKind.STORM -> {
                cloud(Offset(s * 0.08f, s * 0.04f), s * 0.84f, DarkCloudColor)
                val bolt = Path().apply {
                    moveTo(s * 0.56f, s * 0.52f)
                    lineTo(s * 0.38f, s * 0.76f)
                    lineTo(s * 0.50f, s * 0.76f)
                    lineTo(s * 0.42f, s * 0.97f)
                    lineTo(s * 0.66f, s * 0.68f)
                    lineTo(s * 0.54f, s * 0.68f)
                    lineTo(s * 0.62f, s * 0.52f)
                    close()
                }
                drawPath(bolt, SunColor)
            }
        }
    }
}

private fun DrawScope.sun(center: Offset, radius: Float) {
    drawCircle(SunColor, radius, center)
    val stroke = radius * 0.28f
    for (i in 0 until 8) {
        val angle = Math.toRadians(i * 45.0)
        val dx = cos(angle).toFloat()
        val dy = sin(angle).toFloat()
        drawLine(
            SunColor,
            Offset(center.x + dx * radius * 1.45f, center.y + dy * radius * 1.45f),
            Offset(center.x + dx * radius * 1.95f, center.y + dy * radius * 1.95f),
            stroke,
            StrokeCap.Round,
        )
    }
}

private fun DrawScope.moon(center: Offset, radius: Float) {
    val full = Path().apply { addOval(Rect(center, radius)) }
    val cut = Path().apply {
        addOval(Rect(Offset(center.x + radius * 0.55f, center.y - radius * 0.45f), radius * 0.85f))
    }
    val crescent = Path().apply { op(full, cut, PathOperation.Difference) }
    drawPath(crescent, MoonColor)
}

/** Nuage d'une largeur [width] (hauteur ≈ 0,62 × largeur) dont le coin haut-gauche est [topLeft]. */
private fun DrawScope.cloud(topLeft: Offset, width: Float, color: Color) {
    val height = width * 0.62f
    val baseTop = topLeft.y + height * 0.45f
    drawRoundRect(
        color = color,
        topLeft = Offset(topLeft.x, baseTop),
        size = Size(width, height * 0.55f),
        cornerRadius = CornerRadius(height * 0.275f),
    )
    drawCircle(color, width * 0.2f, Offset(topLeft.x + width * 0.3f, baseTop + height * 0.05f))
    drawCircle(color, width * 0.27f, Offset(topLeft.x + width * 0.58f, topLeft.y + height * 0.42f))
}
