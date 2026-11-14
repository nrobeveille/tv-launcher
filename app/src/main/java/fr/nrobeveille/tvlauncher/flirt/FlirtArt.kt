package fr.nrobeveille.tvlauncher.flirt

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val Ink = Color(0xFF2A1B22)
private val Blush = Color(0xFFFF7A9C)

/** Fond de soirée : dégradé, guirlande lumineuse, boule à facettes et paillettes. */
fun DrawScope.partyBackground(t: Float) {
    val w = size.width
    val h = size.height
    drawRect(Brush.verticalGradient(listOf(Color(0xFF1B0B2E), Color(0xFF3E1450), Color(0xFF240C35))))
    // Faisceaux de lumière
    val ball = Offset(w * 0.5f, h * 0.1f)
    for (k in 0 until 5) {
        val a = (sin(t * 0.5f + k * 1.3f) * 0.6f + PI.toFloat() / 2f)
        val end = ball + Offset(cos(a) * h * 1.3f, sin(a) * h * 1.3f)
        val colors = listOf(Color(0xFFFF5D8F), Color(0xFF7FB2FF), Color(0xFFB388FF), Color(0xFFFFC94D), Color(0xFF6BE3A4))
        drawLine(colors[k].copy(alpha = 0.07f), ball, end, w * 0.06f, StrokeCap.Round)
    }
    // Guirlande
    val bulbs = 22
    val path = Path()
    for (i in 0..bulbs) {
        val x = w * i / bulbs
        val y = h * 0.035f + sin(i / bulbs.toFloat() * PI.toFloat() * 3f) * h * 0.02f + h * 0.02f
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, Color(0xFF0F0716), style = Stroke(3f))
    val bulbColors = listOf(Color(0xFFFF5D8F), Color(0xFFFFC94D), Color(0xFF6BE3A4), Color(0xFF7FB2FF), Color(0xFFB388FF))
    for (i in 0..bulbs) {
        val x = w * i / bulbs
        val y = h * 0.035f + sin(i / bulbs.toFloat() * PI.toFloat() * 3f) * h * 0.02f + h * 0.02f
        val on = 0.55f + 0.45f * sin(t * 3f + i * 0.9f)
        val c = bulbColors[i % bulbColors.size]
        drawCircle(c.copy(alpha = 0.25f * on), h * 0.022f, Offset(x, y + h * 0.012f))
        drawCircle(c.copy(alpha = 0.6f + 0.4f * on), h * 0.009f, Offset(x, y + h * 0.012f))
    }
    // Boule à facettes
    val r = h * 0.045f
    drawLine(Color(0xFF8C8C9A), Offset(ball.x, 0f), Offset(ball.x, ball.y - r), 2f)
    drawCircle(Color(0xFF9AA3B2), r, ball)
    for (row in -3..3) {
        for (col in -3..3) {
            val fx = ball.x + col * r * 0.28f
            val fy = ball.y + row * r * 0.28f
            if ((fx - ball.x) * (fx - ball.x) + (fy - ball.y) * (fy - ball.y) > r * r * 0.8f) continue
            val shine = 0.5f + 0.5f * sin(t * 4f + col * 1.1f + row * 0.7f)
            drawRect(Color.White.copy(alpha = 0.15f + 0.6f * shine), Offset(fx - r * 0.11f, fy - r * 0.11f), Size(r * 0.22f, r * 0.22f))
        }
    }
    // Paillettes
    for (k in 0 until 40) {
        val x = ((k * 97.3f) % 100f) / 100f * w
        val y = ((k * 53.7f) % 100f) / 100f * h
        val tw = 0.5f + 0.5f * sin(t * (1.5f + k % 5) + k)
        drawCircle(Color.White.copy(alpha = 0.25f * tw), 2.5f + tw * 2f, Offset(x, y))
    }
}

/** Cœur centré sur [c], de largeur [s]. */
fun DrawScope.heart(c: Offset, s: Float, color: Color) {
    val p = Path().apply {
        moveTo(c.x, c.y + s * 0.35f)
        cubicTo(c.x - s * 0.9f, c.y - s * 0.15f, c.x - s * 0.45f, c.y - s * 0.75f, c.x, c.y - s * 0.3f)
        cubicTo(c.x + s * 0.45f, c.y - s * 0.75f, c.x + s * 0.9f, c.y - s * 0.15f, c.x, c.y + s * 0.35f)
        close()
    }
    drawPath(p, color)
}

/** Petite flamme qui vacille. */
fun DrawScope.flame(base: Offset, width: Float, height: Float, t: Float) {
    val f = 1f + 0.12f * sin(t * 17f) + 0.06f * sin(t * 29f)
    fun drop(scale: Float, color: Color) {
        val wd = width * scale
        val ht = height * scale * f
        val path = Path().apply {
            moveTo(base.x, base.y - ht)
            cubicTo(base.x + wd * 0.9f, base.y - ht * 0.45f, base.x + wd * 0.6f, base.y, base.x, base.y)
            cubicTo(base.x - wd * 0.6f, base.y, base.x - wd * 0.9f, base.y - ht * 0.45f, base.x, base.y - ht)
            close()
        }
        drawPath(path, color)
    }
    drop(1f, Color(0xFFFF7043))
    drop(0.65f, Color(0xFFFFB74D))
    drop(0.32f, Color(0xFFFFF59D))
}

/**
 * Personnage dessiné (buste), entièrement inventé. [t] sert au clignement des yeux et à la
 * respiration.
 */
fun DrawScope.avatar(look: Look, mood: Mood, t: Float) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val bob = sin(t * 1.6f) * h * 0.006f
    val head = Offset(cx, h * 0.36f + bob)
    val rx = w * 0.2f
    val ry = h * 0.2f
    val darkHair = look.hair
    val lightHair = look.hair.copy(red = minOf(1f, look.hair.red + 0.12f), green = minOf(1f, look.hair.green + 0.08f), blue = minOf(1f, look.hair.blue + 0.06f))

    // Cheveux (arrière)
    when (look.hairStyle) {
        0 -> drawRoundRect(darkHair, Offset(cx - rx * 1.18f, head.y - ry * 0.9f), Size(rx * 2.36f, ry * 1.85f), CornerRadius(rx * 0.6f))
        1 -> {
            drawOval(darkHair, Offset(cx - rx * 1.45f, head.y - ry * 1.05f), Size(rx * 2.9f, ry * 3.0f))
            for (k in 0 until 8) {
                val a = k / 7f
                drawCircle(darkHair, rx * 0.32f, Offset(cx - rx * 1.35f + a * rx * 2.7f, head.y + ry * 1.7f + sin(k * 1.7f) * ry * 0.1f))
            }
        }
        else -> drawCircle(darkHair, rx * 0.5f, Offset(cx + rx * 0.15f, head.y - ry * 1.05f))
    }

    // Buste
    val shoulderTop = h * 0.66f + bob
    drawRect(look.skin, Offset(cx - rx * 0.32f, head.y + ry * 0.7f), Size(rx * 0.64f, shoulderTop - head.y - ry * 0.6f))
    drawRoundRect(look.top, Offset(cx - w * 0.36f, shoulderTop), Size(w * 0.72f, h - shoulderTop + 40f), CornerRadius(w * 0.16f))
    // Encolure
    drawOval(look.skin, Offset(cx - rx * 0.42f, shoulderTop - h * 0.02f), Size(rx * 0.84f, h * 0.06f))

    when (look.accessory) {
        0 -> {
            // Sweat à capuche avec « </> »
            drawArc(Color(0xFF2E4766), 200f, 140f, false, Offset(cx - w * 0.3f, shoulderTop - h * 0.04f), Size(w * 0.6f, h * 0.16f), style = Stroke(w * 0.025f))
            val y = shoulderTop + h * 0.16f
            val s = w * 0.05f
            drawLine(Color.White, Offset(cx - s * 1.6f, y - s * 0.5f), Offset(cx - s * 2.3f, y), 6f, StrokeCap.Round)
            drawLine(Color.White, Offset(cx - s * 2.3f, y), Offset(cx - s * 1.6f, y + s * 0.5f), 6f, StrokeCap.Round)
            drawLine(Color.White, Offset(cx + s * 0.4f, y - s * 0.6f), Offset(cx - s * 0.4f, y + s * 0.6f), 6f, StrokeCap.Round)
            drawLine(Color.White, Offset(cx + s * 1.6f, y - s * 0.5f), Offset(cx + s * 2.3f, y), 6f, StrokeCap.Round)
            drawLine(Color.White, Offset(cx + s * 2.3f, y), Offset(cx + s * 1.6f, y + s * 0.5f), 6f, StrokeCap.Round)
        }
        1 -> {
            // Casque de DJ autour du cou
            drawArc(Color(0xFF15151C), 20f, 140f, false, Offset(cx - rx * 1.05f, shoulderTop - h * 0.13f), Size(rx * 2.1f, h * 0.16f), style = Stroke(w * 0.025f))
            drawRoundRect(Color(0xFF15151C), Offset(cx - rx * 1.15f, shoulderTop - h * 0.06f), Size(rx * 0.42f, h * 0.08f), CornerRadius(12f))
            drawRoundRect(Color(0xFF15151C), Offset(cx + rx * 0.73f, shoulderTop - h * 0.06f), Size(rx * 0.42f, h * 0.08f), CornerRadius(12f))
            drawCircle(Color(0xFF00E5FF), rx * 0.06f, Offset(cx - rx * 0.94f, shoulderTop - h * 0.02f))
            drawCircle(Color(0xFF00E5FF), rx * 0.06f, Offset(cx + rx * 0.94f, shoulderTop - h * 0.02f))
        }
        else -> {
            // Tablier de pâtissière
            drawRoundRect(Color(0xFFE57373), Offset(cx - w * 0.2f, shoulderTop + h * 0.05f), Size(w * 0.4f, h * 0.4f), CornerRadius(18f))
            drawLine(Color(0xFFE57373), Offset(cx - w * 0.17f, shoulderTop + h * 0.06f), Offset(cx - rx * 0.4f, shoulderTop), 8f)
            drawLine(Color(0xFFE57373), Offset(cx + w * 0.17f, shoulderTop + h * 0.06f), Offset(cx + rx * 0.4f, shoulderTop), 8f)
            heart(Offset(cx, shoulderTop + h * 0.17f), w * 0.06f, Color.White.copy(alpha = 0.85f))
        }
    }

    // Oreilles + tête
    drawOval(look.skin, Offset(cx - rx * 1.08f, head.y - ry * 0.15f), Size(rx * 0.3f, ry * 0.42f))
    drawOval(look.skin, Offset(cx + rx * 0.78f, head.y - ry * 0.15f), Size(rx * 0.3f, ry * 0.42f))
    drawOval(look.skin, Offset(cx - rx, head.y - ry), Size(rx * 2f, ry * 2f))

    // Cheveux (avant)
    val fringe = Path().apply {
        moveTo(cx - rx * 1.05f, head.y - ry * 0.05f)
        cubicTo(cx - rx * 1.1f, head.y - ry * 1.35f, cx + rx * 1.1f, head.y - ry * 1.35f, cx + rx * 1.05f, head.y - ry * 0.05f)
        when (look.hairStyle) {
            0 -> {
                lineTo(cx + rx * 0.9f, head.y - ry * 0.35f)
                cubicTo(cx + rx * 0.3f, head.y - ry * 0.45f, cx - rx * 0.4f, head.y - ry * 0.4f, cx - rx * 0.9f, head.y - ry * 0.3f)
            }
            1 -> {
                cubicTo(cx + rx * 0.7f, head.y - ry * 0.7f, cx - rx * 0.1f, head.y - ry * 0.75f, cx - rx * 0.2f, head.y - ry * 0.5f)
                cubicTo(cx - rx * 0.5f, head.y - ry * 0.75f, cx - rx * 0.9f, head.y - ry * 0.5f, cx - rx * 1.05f, head.y - ry * 0.05f)
            }
            else -> {
                cubicTo(cx + rx * 0.6f, head.y - ry * 0.8f, cx - rx * 0.6f, head.y - ry * 0.8f, cx - rx * 1.05f, head.y - ry * 0.05f)
            }
        }
        close()
    }
    drawPath(fringe, darkHair)
    drawArc(lightHair, 200f, 50f, false, Offset(cx - rx * 0.8f, head.y - ry * 1.05f), Size(rx * 1.6f, ry * 1.2f), style = Stroke(rx * 0.06f, cap = StrokeCap.Round))
    if (look.accessory == 2) {
        // Barrette cerise
        drawCircle(Color(0xFFD32F2F), rx * 0.1f, Offset(cx + rx * 0.62f, head.y - ry * 0.72f))
        drawCircle(Color(0xFFD32F2F), rx * 0.1f, Offset(cx + rx * 0.8f, head.y - ry * 0.66f))
        drawLine(Color(0xFF388E3C), Offset(cx + rx * 0.62f, head.y - ry * 0.82f), Offset(cx + rx * 0.75f, head.y - ry * 1.0f), 4f)
        // Trace de farine sur la joue
        drawOval(Color.White.copy(alpha = 0.6f), Offset(cx - rx * 0.75f, head.y + ry * 0.2f), Size(rx * 0.28f, ry * 0.12f))
    }

    // Visage
    val eyeY = head.y + ry * 0.05f
    val ex = rx * 0.4f
    val blink = (t % 3.7f) < 0.12f
    val closedHappy = mood == Mood.BLUSH || mood == Mood.LAUGH
    for (s in listOf(-1f, 1f)) {
        val e = Offset(cx + s * ex, eyeY)
        when {
            closedHappy -> drawArc(Ink, 200f, 140f, false, Offset(e.x - rx * 0.16f, e.y - ry * 0.08f), Size(rx * 0.32f, ry * 0.22f), style = Stroke(5f, cap = StrokeCap.Round))
            blink -> drawLine(Ink, Offset(e.x - rx * 0.13f, e.y), Offset(e.x + rx * 0.13f, e.y), 5f, StrokeCap.Round)
            else -> {
                drawOval(Ink, Offset(e.x - rx * 0.1f, e.y - ry * 0.13f), Size(rx * 0.2f, ry * 0.26f))
                drawCircle(Color.White, rx * 0.035f, Offset(e.x + rx * 0.03f, e.y - ry * 0.06f))
                if (mood == Mood.ANNOYED) {
                    drawRect(look.skin, Offset(e.x - rx * 0.12f, e.y - ry * 0.16f), Size(rx * 0.24f, ry * 0.12f))
                    drawLine(Ink, Offset(e.x - rx * 0.12f, e.y - ry * 0.04f), Offset(e.x + rx * 0.12f, e.y - ry * 0.04f), 4f)
                }
            }
        }
        // Sourcils
        val by = eyeY - ry * 0.3f
        if (mood == Mood.ANNOYED) {
            drawLine(darkHair, Offset(cx + s * ex * 1.45f, by - ry * 0.05f), Offset(cx + s * ex * 0.6f, by + ry * 0.04f), 6f, StrokeCap.Round)
        } else {
            drawArc(darkHair, 200f, 140f, false, Offset(cx + s * ex - rx * 0.17f, by - ry * 0.04f), Size(rx * 0.34f, ry * 0.16f), style = Stroke(6f, cap = StrokeCap.Round))
        }
    }
    // Nez
    drawArc(Ink.copy(alpha = 0.5f), 20f, 140f, false, Offset(cx - rx * 0.08f, head.y + ry * 0.22f), Size(rx * 0.16f, ry * 0.12f), style = Stroke(4f))
    // Bouche
    val my = head.y + ry * 0.5f
    when (mood) {
        Mood.NEUTRAL -> drawArc(Ink, 20f, 140f, false, Offset(cx - rx * 0.22f, my - ry * 0.08f), Size(rx * 0.44f, ry * 0.14f), style = Stroke(5f, cap = StrokeCap.Round))
        Mood.SMILE, Mood.BLUSH -> drawArc(Ink, 10f, 160f, false, Offset(cx - rx * 0.32f, my - ry * 0.16f), Size(rx * 0.64f, ry * 0.26f), style = Stroke(6f, cap = StrokeCap.Round))
        Mood.LAUGH -> {
            drawArc(Color(0xFF7A1F2B), 0f, 180f, true, Offset(cx - rx * 0.3f, my - ry * 0.14f), Size(rx * 0.6f, ry * 0.34f))
            drawRect(Color.White, Offset(cx - rx * 0.22f, my + ry * 0.03f), Size(rx * 0.44f, ry * 0.05f))
        }
        Mood.ANNOYED -> drawLine(Ink, Offset(cx - rx * 0.18f, my + ry * 0.04f), Offset(cx + rx * 0.2f, my - ry * 0.02f), 6f, StrokeCap.Round)
    }
    // Joues
    val blushAlpha = when (mood) {
        Mood.BLUSH -> 0.6f
        Mood.SMILE, Mood.LAUGH -> 0.3f
        else -> 0.12f
    }
    drawOval(Blush.copy(alpha = blushAlpha), Offset(cx - rx * 0.78f, head.y + ry * 0.22f), Size(rx * 0.36f, ry * 0.18f))
    drawOval(Blush.copy(alpha = blushAlpha), Offset(cx + rx * 0.42f, head.y + ry * 0.22f), Size(rx * 0.36f, ry * 0.18f))

    // Lunettes (Léa)
    if (look.accessory == 0) {
        for (s in listOf(-1f, 1f)) {
            drawCircle(Color(0xFF1B1B24), rx * 0.24f, Offset(cx + s * ex, eyeY), style = Stroke(5f))
        }
        drawLine(Color(0xFF1B1B24), Offset(cx - ex + rx * 0.24f, eyeY), Offset(cx + ex - rx * 0.24f, eyeY), 5f)
    }
    // Petits cœurs quand elle rougit
    if (mood == Mood.BLUSH) {
        for (k in 0 until 3) {
            val a = (t * 0.7f + k / 3f) % 1f
            heart(
                Offset(cx + rx * (1.1f + 0.15f * k) , head.y - ry * (0.6f + a * 0.9f)),
                rx * (0.18f + 0.05f * k),
                Color(0xFFFF5D8F).copy(alpha = 1f - a),
            )
        }
    }
}

/** Petit chapeau de fête posé sur le portrait du héros. */
fun DrawScope.miniPartyHat(base: Offset, width: Float, height: Float) {
    rotate(14f, base) {
        val cone = Path().apply {
            moveTo(base.x - width / 2, base.y)
            lineTo(base.x + width / 2, base.y)
            lineTo(base.x, base.y - height)
            close()
        }
        drawPath(cone, Color(0xFFFF5D8F))
        drawPath(
            Path().apply {
                moveTo(base.x - width * 0.32f, base.y - height * 0.35f)
                lineTo(base.x + width * 0.32f, base.y - height * 0.45f)
                lineTo(base.x + width * 0.25f, base.y - height * 0.56f)
                lineTo(base.x - width * 0.22f, base.y - height * 0.5f)
                close()
            },
            Color(0xFFFFC94D),
        )
        drawCircle(Color(0xFFFFC94D), width * 0.13f, Offset(base.x, base.y - height))
        for (k in 0 until 6) {
            drawCircle(Color.White, width * 0.08f, Offset(base.x - width / 2 + width * k / 5f, base.y))
        }
    }
}
