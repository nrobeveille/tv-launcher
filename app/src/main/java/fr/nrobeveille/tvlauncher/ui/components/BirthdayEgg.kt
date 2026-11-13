package fr.nrobeveille.tvlauncher.ui.components

import android.media.MediaPlayer
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.Text
import fr.nrobeveille.tvlauncher.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

// --- Déroulé (secondes), calé sur res/raw/birthday_song.ogg -----------------------------

private const val PHOTO_IN = 0.25f
private const val TITLE_IN = 0.7f
private const val CAKE_IN = 1.3f
private const val HAT_IN = 1.9f
private const val WISH_IN = 2.8f
private const val SONG_END = 7.96f
private const val BLOW = 8.31f
private const val FINAL_BURST = 8.6f
private const val EXIT = 9.5f
private const val END = 10.4f

/** Proportions de res/drawable-nodpi/birthday_photo.webp (665 × 1400). */
private const val PHOTO_RATIO = 665f / 1400f

/** Chapeau de fête, en proportions de la photo (réglé sur la position de la tête). */
private const val HAT_BASE_X = 0.495f   // × largeur de la photo
private const val HAT_BASE_Y = 0.083f   // × hauteur de la photo
private const val HAT_WIDTH = 0.19f     // × largeur de la photo
private const val HAT_HEIGHT = 0.25f    // × largeur de la photo
private const val HAT_ANGLE = 10f

private val Festive = listOf(
    Color(0xFFFF5D8F), Color(0xFFFFC94D), Color(0xFF7FB2FF), Color(0xFF6BE3A4),
    Color(0xFFB388FF), Color(0xFFFF8A4D), Color(0xFFFFFFFF),
)
private val Gold = Color(0xFFFFC94D)

/**
 * Surprise d'anniversaire : la photo surgit en tournoyant sur fond de rayons dorés, les
 * lettres de « Joyeux anniversaire » tombent une à une, un gâteau monte du bas de l'écran,
 * un chapeau de fête atterrit sur la tête, et des confettis pleuvent pendant la mélodie.
 * À la fin, les bougies s'éteignent d'un souffle, dernière salve de confettis, et tout
 * s'envole. Retour ferme plus tôt.
 */
@Composable
fun BirthdayOverlay(onDone: () -> Unit) {
    val context = LocalContext.current
    val currentOnDone by rememberUpdatedState(onDone)

    DisposableEffect(Unit) {
        val player = runCatching { MediaPlayer.create(context, R.raw.birthday_song) }.getOrNull()
        player?.start()
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
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect { dialogWindow?.setDimAmount(0f) }

        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val w = constraints.maxWidth.toFloat()
            val h = constraints.maxHeight.toFloat()

            var time by remember { mutableFloatStateOf(0f) }
            val confetti = remember { ConfettiSystem(420) }

            LaunchedEffect(Unit) {
                val start = withFrameNanos { it }
                var last = start
                var rainCarry = 0f
                var burstStart = false
                var burstFinal = false
                while (true) {
                    val t = withFrameNanos { now ->
                        val dt = ((now - last) / 1e9f).coerceIn(0f, 0.05f)
                        last = now
                        val t = (now - start) / 1e9f
                        if (!burstStart) {
                            burstStart = true
                            confetti.cannon(w, h, fromLeft = true, count = 90)
                            confetti.cannon(w, h, fromLeft = false, count = 90)
                        }
                        if (t in 1.2f..SONG_END) {
                            rainCarry += dt * 22f
                            while (rainCarry >= 1f) {
                                rainCarry -= 1f
                                confetti.rain(w, h)
                            }
                        }
                        if (!burstFinal && t >= FINAL_BURST) {
                            burstFinal = true
                            confetti.explode(w * 0.5f, h * 0.18f, h, count = 160)
                        }
                        confetti.step(dt, t, w, h)
                        time = t
                        t
                    }
                    if (t >= END) {
                        currentOnDone()
                        break
                    }
                }
            }

            val photoH = h * 0.74f
            val photoW = photoH * PHOTO_RATIO
            val border = with(density) { 8.dp.toPx() }
            val cardW = photoW + 2 * border
            val cardH = photoH + 2 * border
            val center = Offset(w * 0.5f, h * 0.53f)

            // Fond assombri + rayons dorés qui tournent derrière la photo
            Canvas(Modifier.fillMaxSize()) {
                val t = time
                val fade = envelope(t, 0f, 0.4f, EXIT + 0.3f, 0.5f)
                drawRect(Color.Black.copy(alpha = 0.6f * fade))
                val rays = envelope(t, PHOTO_IN, 0.6f, EXIT, 0.4f)
                if (rays > 0f) {
                    sunburst(center, hypot(w, h), t * 12f, rays)
                    drawCircle(
                        Brush.radialGradient(listOf(Gold.copy(alpha = 0.35f * rays), Color.Transparent), center, photoH * 0.8f),
                        radius = photoH * 0.8f,
                        center = center,
                    )
                }
            }

            // Titre qui tombe lettre par lettre
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(0.4f)
                    .offset(y = with(density) { (-h * 0.07f).toDp() }),
            ) {
                val big = with(density) { (w * 0.064f).toSp() }
                val medium = with(density) { (w * 0.040f).toSp() }
                LetterRow("JOYEUX", firstIndex = 0, fontSize = big, height = h, time = { time })
                LetterRow("ANNIVERSAIRE", firstIndex = 6, fontSize = medium, height = h, time = { time })
                Text(
                    text = "Fais un vœu !",
                    fontSize = with(density) { (w * 0.024f).toSp() },
                    fontStyle = FontStyle.Italic,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier
                        .padding(top = 18.dp)
                        .graphicsLayer {
                            val t = time
                            alpha = envelope(t, WISH_IN, 0.5f, BLOW - 0.2f, 0.3f)
                            val s = 1f + 0.05f * sin(t * 4f)
                            scaleX = s
                            scaleY = s
                        },
                )
            }

            // Gâteau qui monte du bas de l'écran
            val cakeW = w * 0.22f
            val cakeH = cakeW * 1.05f
            Canvas(
                Modifier
                    .offset(
                        x = with(density) { (w * 0.8f - cakeW / 2).toDp() },
                        y = with(density) { (h * 0.9f - cakeH).toDp() },
                    )
                    .size(with(density) { cakeW.toDp() }, with(density) { cakeH.toDp() })
                    .graphicsLayer {
                        val t = time
                        val inP = clamp01((t - CAKE_IN) / 0.7f)
                        val outP = clamp01((t - EXIT - 0.1f) / 0.6f)
                        translationY = (1f - easeOutBack(inP)) * h * 0.7f + easeInCubic(outP) * h * 0.9f
                        alpha = if (inP > 0f) 1f else 0f
                    },
            ) { cake(time) }

            // Photo + chapeau de fête
            Box(
                Modifier
                    .offset(
                        x = with(density) { (center.x - cardW / 2).toDp() },
                        y = with(density) { (center.y - cardH / 2).toDp() },
                    )
                    .size(with(density) { cardW.toDp() }, with(density) { cardH.toDp() })
                    .graphicsLayer {
                        val t = time
                        val p = clamp01((t - PHOTO_IN) / 0.75f)
                        val q = clamp01((t - EXIT) / 0.7f)
                        val s = 0.15f + 0.85f * easeOutBack(p)
                        scaleX = s
                        scaleY = s
                        alpha = clamp01(p * 3f)
                        rotationZ = -25f + 22f * easeOutCubic(p) + 1.5f * sin(t * 2.2f) * p + 30f * q
                        translationY = 6f * sin(t * 1.8f) * p - easeInCubic(q) * h * 1.2f
                    },
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .shadow(28.dp, RoundedCornerShape(18.dp))
                        .background(Color.White, RoundedCornerShape(18.dp))
                        .padding(8.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.birthday_photo),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(11.dp)),
                    )
                }
                Canvas(Modifier.fillMaxSize()) {
                    val t = time
                    val d = clamp01((t - HAT_IN) / 0.6f)
                    if (d <= 0f) return@Canvas
                    val base = Offset(border + HAT_BASE_X * photoW, border + HAT_BASE_Y * photoH)
                    val drop = (1f - easeOutBounce(d)) * h * 0.6f
                    partyHat(
                        base = base.copy(y = base.y - drop),
                        width = HAT_WIDTH * photoW,
                        height = HAT_HEIGHT * photoW,
                        angle = HAT_ANGLE + (1f - d) * -30f,
                    )
                }
            }

            // Confettis par-dessus tout
            Canvas(Modifier.fillMaxSize()) {
                val t = time
                confetti.draw(this, alpha = 1f - clamp01((t - EXIT - 0.4f) / 0.5f))
            }
        }
    }
}

// --- Titre ---------------------------------------------------------------------------

@Composable
private fun LetterRow(text: String, firstIndex: Int, fontSize: TextUnit, height: Float, time: () -> Float) {
    Row {
        text.forEachIndexed { i, char ->
            val index = firstIndex + i
            Text(
                text = char.toString(),
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                color = Festive[index % (Festive.size - 1)],
                style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(5f, 6f), 10f)),
                modifier = Modifier.graphicsLayer {
                    val t = time()
                    val d = clamp01((t - TITLE_IN - index * 0.06f) / 0.55f)
                    val e = clamp01((t - EXIT - index * 0.025f) / 0.7f)
                    val wave = clamp01((t - 2.4f) / 0.6f)
                    alpha = if (d > 0f) 1f else 0f
                    translationY = -(1f - easeOutBounce(d)) * height * 0.6f +
                        sin(t * 5f - index * 0.5f) * 7f * wave +
                        e * e * height * 1.3f
                    rotationZ = (1f - d) * (if (index % 2 == 0) -25f else 25f) +
                        e * (if (index % 2 == 0) 50f else -50f)
                },
            )
        }
    }
}

// --- Confettis -------------------------------------------------------------------------

private class ConfettiSystem(private val capacity: Int) {
    private val x = FloatArray(capacity)
    private val y = FloatArray(capacity)
    private val vx = FloatArray(capacity)
    private val vy = FloatArray(capacity)
    private val rot = FloatArray(capacity)
    private val vrot = FloatArray(capacity)
    private val flip = FloatArray(capacity)
    private val vflip = FloatArray(capacity)
    private val sizes = FloatArray(capacity)
    private val sway = FloatArray(capacity)
    private val phase = FloatArray(capacity)
    private val color = IntArray(capacity)
    private val round = BooleanArray(capacity)
    private val alive = BooleanArray(capacity)
    private var next = 0
    private val random = Random(2026)

    private fun spawn(px: Float, py: Float, pvx: Float, pvy: Float, h: Float) {
        val i = next
        next = (next + 1) % capacity
        x[i] = px
        y[i] = py
        vx[i] = pvx
        vy[i] = pvy
        rot[i] = random.nextFloat() * 360f
        vrot[i] = (random.nextFloat() - 0.5f) * 720f
        flip[i] = random.nextFloat() * 6.28f
        vflip[i] = 4f + random.nextFloat() * 8f
        sizes[i] = h * (0.012f + random.nextFloat() * 0.012f)
        sway[i] = h * (0.03f + random.nextFloat() * 0.06f)
        phase[i] = random.nextFloat() * 6.28f
        color[i] = random.nextInt(Festive.size)
        round[i] = random.nextFloat() < 0.18f
        alive[i] = true
    }

    /** Canon à confettis depuis un coin bas de l'écran. */
    fun cannon(w: Float, h: Float, fromLeft: Boolean, count: Int) {
        repeat(count) {
            val angle = Math.toRadians((-82.0 + random.nextDouble() * 40.0))
            val speed = h * (1.5f + random.nextFloat() * 1.1f)
            val dir = if (fromLeft) 1f else -1f
            spawn(
                px = if (fromLeft) w * 0.02f else w * 0.98f,
                py = h * 1.02f,
                pvx = dir * cos(angle).toFloat() * speed,
                pvy = sin(angle).toFloat() * speed,
                h = h,
            )
        }
    }

    fun rain(w: Float, h: Float) = spawn(
        px = random.nextFloat() * w,
        py = -h * 0.04f,
        pvx = (random.nextFloat() - 0.5f) * h * 0.1f,
        pvy = h * (0.1f + random.nextFloat() * 0.15f),
        h = h,
    )

    /** Explosion dans toutes les directions. */
    fun explode(cx: Float, cy: Float, h: Float, count: Int) {
        repeat(count) {
            val angle = random.nextDouble() * 2 * PI
            val speed = h * (0.5f + random.nextFloat() * 1.2f)
            spawn(cx, cy, cos(angle).toFloat() * speed, sin(angle).toFloat() * speed - h * 0.3f, h)
        }
    }

    fun step(dt: Float, t: Float, w: Float, h: Float) {
        val gravity = h * 0.9f
        val drag = exp(-2.6f * dt)
        for (i in 0 until capacity) {
            if (!alive[i]) continue
            vy[i] = (vy[i] + gravity * dt) * drag
            vx[i] *= drag
            x[i] += (vx[i] + sin(phase[i] + t * 3f) * sway[i]) * dt
            y[i] += vy[i] * dt
            rot[i] += vrot[i] * dt
            flip[i] += vflip[i] * dt
            if (y[i] > h * 1.1f || x[i] < -w * 0.1f || x[i] > w * 1.1f) alive[i] = false
        }
    }

    fun draw(scope: DrawScope, alpha: Float) = with(scope) {
        if (alpha <= 0f) return@with
        for (i in 0 until capacity) {
            if (!alive[i]) continue
            val c = Festive[color[i]].copy(alpha = 0.95f * alpha)
            val p = Offset(x[i], y[i])
            val s = sizes[i]
            if (round[i]) {
                drawCircle(c, s * 0.35f, p)
            } else {
                withTransform({
                    rotate(rot[i], p)
                    scale(max(0.15f, kotlin.math.abs(cos(flip[i]))), 1f, p)
                }) {
                    drawRect(c, Offset(p.x - s / 2, p.y - s * 0.22f), Size(s, s * 0.44f))
                }
            }
        }
    }
}

// --- Dessins -------------------------------------------------------------------------

private fun DrawScope.sunburst(center: Offset, radius: Float, rotationDeg: Float, strength: Float) {
    val color = Gold.copy(alpha = 0.10f * strength)
    rotate(rotationDeg, center) {
        for (k in 0 until 18) {
            val a0 = Math.toRadians(k * 20.0).toFloat()
            val a1 = Math.toRadians(k * 20.0 + 9.0).toFloat()
            val path = Path().apply {
                moveTo(center.x, center.y)
                lineTo(center.x + cos(a0) * radius, center.y + sin(a0) * radius)
                lineTo(center.x + cos(a1) * radius, center.y + sin(a1) * radius)
                close()
            }
            drawPath(path, color)
        }
    }
}

/** Chapeau pointu à rayures, pompon doré et bordure duveteuse. */
private fun DrawScope.partyHat(base: Offset, width: Float, height: Float, angle: Float) {
    rotate(angle, base) {
        val cone = Path().apply {
            moveTo(base.x - width / 2, base.y)
            lineTo(base.x + width / 2, base.y)
            lineTo(base.x, base.y - height)
            close()
        }
        drawPath(
            cone,
            Brush.horizontalGradient(
                listOf(Color(0xFFE0457B), Color(0xFFFF6FA3), Color(0xFFD13C70)),
                startX = base.x - width / 2,
                endX = base.x + width / 2,
            ),
        )
        clipPath(cone) {
            for (k in 0 until 4) {
                val y0 = base.y - height * (0.15f + k * 0.22f)
                val stripe = Path().apply {
                    moveTo(base.x - width, y0)
                    lineTo(base.x + width, y0 - height * 0.12f)
                    lineTo(base.x + width, y0 - height * 0.2f)
                    lineTo(base.x - width, y0 - height * 0.08f)
                    close()
                }
                drawPath(stripe, Gold.copy(alpha = 0.9f))
            }
            for (k in 0 until 6) {
                val dx = (k % 3 - 1) * width * 0.22f
                val dy = height * (0.2f + (k / 3) * 0.32f) + (k % 2) * height * 0.08f
                drawCircle(Color.White.copy(alpha = 0.85f), width * 0.035f, Offset(base.x + dx, base.y - dy))
            }
        }
        // Bordure duveteuse
        val puffs = 7
        for (k in 0 until puffs) {
            val px = base.x - width / 2 + width * k / (puffs - 1)
            drawCircle(Color.White, width * 0.085f, Offset(px, base.y))
        }
        // Pompon
        val top = Offset(base.x, base.y - height)
        drawCircle(Gold, width * 0.13f, top)
        for (k in 0 until 6) {
            val a = k * PI.toFloat() / 3f
            drawCircle(Color(0xFFFFE08A), width * 0.06f, top + Offset(cos(a) * width * 0.09f, sin(a) * width * 0.09f))
        }
    }
}

/** Gâteau à deux étages, glaçage qui coule, vermicelles, trois bougies (soufflées à la fin). */
private fun DrawScope.cake(t: Float) {
    val w = size.width
    val h = size.height
    val cream = Color(0xFFFFF4E6)

    // Lueur des bougies
    val lit = 1f - clamp01((t - BLOW) / 0.25f)
    if (lit > 0f) {
        drawCircle(
            Brush.radialGradient(listOf(Color(0x66FFB74D), Color.Transparent), Offset(w * 0.5f, h * 0.15f), w * 0.55f),
            radius = w * 0.55f,
            center = Offset(w * 0.5f, h * 0.15f),
            alpha = lit,
        )
    }

    // Assiette
    drawOval(Color(0x33000000), Offset(w * 0.0f, h * 0.89f), Size(w, h * 0.1f))
    drawOval(Color(0xFFE9EEF5), Offset(w * 0.02f, h * 0.875f), Size(w * 0.96f, h * 0.09f))

    // Étage du bas
    drawRoundRect(
        Brush.horizontalGradient(listOf(Color(0xFFE76D96), Color(0xFFFF9BBE), Color(0xFFE06690))),
        Offset(w * 0.1f, h * 0.58f), Size(w * 0.8f, h * 0.34f), CornerRadius(w * 0.04f),
    )
    for (k in 0 until 9) {
        drawCircle(cream, w * 0.018f, Offset(w * (0.15f + k * 0.0875f), h * 0.84f))
    }
    frosting(left = w * 0.08f, right = w * 0.92f, top = h * 0.55f, band = h * 0.07f, seed = 1, color = cream)

    // Étage du haut
    drawRoundRect(
        Brush.horizontalGradient(listOf(Color(0xFF7A4A30), Color(0xFF9C6644), Color(0xFF74452C))),
        Offset(w * 0.24f, h * 0.34f), Size(w * 0.52f, h * 0.25f), CornerRadius(w * 0.03f),
    )
    frosting(left = w * 0.22f, right = w * 0.78f, top = h * 0.31f, band = h * 0.065f, seed = 7, color = cream)

    // Vermicelles
    val rng = Random(42)
    repeat(26) {
        val onTop = it < 10
        val px = if (onTop) w * (0.27f + rng.nextFloat() * 0.46f) else w * (0.14f + rng.nextFloat() * 0.72f)
        val py = if (onTop) h * (0.43f + rng.nextFloat() * 0.12f) else h * (0.65f + rng.nextFloat() * 0.15f)
        val a = rng.nextFloat() * 180f
        rotate(a, Offset(px, py)) {
            drawLine(Festive[rng.nextInt(Festive.size - 1)], Offset(px - w * 0.012f, py), Offset(px + w * 0.012f, py), w * 0.011f, StrokeCap.Round)
        }
    }

    // Bougies
    val candleColors = listOf(Color(0xFF7FB2FF), Color(0xFFFFC94D), Color(0xFF6BE3A4))
    for (i in 0 until 3) {
        val cx = w * (0.38f + i * 0.12f)
        val cw = w * 0.045f
        val top = h * 0.16f
        val bottom = h * 0.33f
        drawRoundRect(Color.White, Offset(cx - cw / 2, top), Size(cw, bottom - top), CornerRadius(cw * 0.25f))
        clipPath(Path().apply { addRect(androidx.compose.ui.geometry.Rect(cx - cw / 2, top, cx + cw / 2, bottom)) }) {
            for (k in 0 until 5) {
                val y0 = top + k * (bottom - top) / 4f
                drawLine(candleColors[i], Offset(cx - cw, y0 + cw * 0.6f), Offset(cx + cw, y0 - cw * 0.6f), cw * 0.35f)
            }
        }
        drawLine(Color(0xFF3A2A20), Offset(cx, top), Offset(cx, top - h * 0.02f), w * 0.006f, StrokeCap.Round)

        val blow = clamp01((t - BLOW) / 0.18f)
        val flameScale = (1f - blow) * (1f + 0.1f * sin(t * 19f + i * 2.1f) + 0.06f * sin(t * 31f + i))
        if (flameScale > 0.02f) {
            val fx = cx + sin(t * 7f + i) * w * 0.004f + blow * w * 0.03f
            val fy = top - h * 0.02f
            flame(Offset(fx, fy), w * 0.034f * flameScale, h * 0.08f * flameScale)
        }
        // Fumée après le souffle
        for (k in 0 until 3) {
            val age = t - BLOW - 0.1f - k * 0.12f
            if (age <= 0f || age > 1.4f) continue
            val sx = cx + sin(age * 4f + k + i) * w * 0.02f
            val sy = top - h * 0.03f - age * h * 0.25f
            drawCircle(Color(0xFFB0B6C2).copy(alpha = 0.4f * (1f - age / 1.4f)), w * (0.012f + age * 0.03f), Offset(sx, sy))
        }
    }
}

private fun DrawScope.frosting(left: Float, right: Float, top: Float, band: Float, seed: Int, color: Color) {
    drawRoundRect(color, Offset(left, top), Size(right - left, band), CornerRadius(band * 0.5f))
    val rng = Random(seed)
    val drips = 8
    val dw = (right - left) / drips * 0.7f
    for (k in 0 until drips) {
        val cx = left + (right - left) * (k + 0.5f) / drips
        val len = band * (0.6f + rng.nextFloat() * 1.1f)
        drawRoundRect(color, Offset(cx - dw / 2, top + band * 0.4f), Size(dw, len), CornerRadius(dw / 2))
    }
}

private fun DrawScope.flame(base: Offset, width: Float, height: Float) {
    drawCircle(
        Brush.radialGradient(listOf(Color(0x88FFD54F), Color.Transparent), base.copy(y = base.y - height * 0.5f), height * 1.2f),
        radius = height * 1.2f,
        center = base.copy(y = base.y - height * 0.5f),
    )
    fun drop(scale: Float, color: Color) {
        val wd = width * scale
        val ht = height * scale
        val path = Path().apply {
            moveTo(base.x, base.y - ht)
            cubicTo(base.x + wd * 0.9f, base.y - ht * 0.45f, base.x + wd * 0.6f, base.y, base.x, base.y)
            cubicTo(base.x - wd * 0.6f, base.y, base.x - wd * 0.9f, base.y - ht * 0.45f, base.x, base.y - ht)
            close()
        }
        drawPath(path, color)
    }
    drop(1f, Color(0xFFFFA726))
    drop(0.65f, Color(0xFFFFEE58))
    drop(0.3f, Color(0xFFFFFFFF))
}

// --- Courbes d'animation --------------------------------------------------------------

private fun clamp01(v: Float) = v.coerceIn(0f, 1f)

private fun easeOutCubic(x: Float): Float {
    val p = 1f - x
    return 1f - p * p * p
}

private fun easeInCubic(x: Float) = x * x * x

private fun easeOutBack(x: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val p = x - 1f
    return 1f + c3 * p * p * p + c1 * p * p
}

private fun easeOutBounce(x: Float): Float {
    val n1 = 7.5625f
    val d1 = 2.75f
    return when {
        x < 1f / d1 -> n1 * x * x
        x < 2f / d1 -> { val p = x - 1.5f / d1; n1 * p * p + 0.75f }
        x < 2.5f / d1 -> { val p = x - 2.25f / d1; n1 * p * p + 0.9375f }
        else -> { val p = x - 2.625f / d1; n1 * p * p + 0.984375f }
    }
}

/** 0 → 1 entre [inStart] et [inStart]+[inDur], puis 1 → 0 à partir de [outStart]. */
private fun envelope(t: Float, inStart: Float, inDur: Float, outStart: Float, outDur: Float): Float =
    clamp01((t - inStart) / inDur) * (1f - clamp01((t - outStart) / outDur))
