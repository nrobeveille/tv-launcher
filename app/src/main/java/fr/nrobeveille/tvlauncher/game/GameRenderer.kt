package fr.nrobeveille.tvlauncher.game

import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

private val Pcb1 = Color(0xFF1F6B47)
private val Pcb2 = Color(0xFF1B6040)
private val Trace = Color(0x88D4AF52)
private val Asphalt = Color(0xFF2A2E3B)
private val Coffee = Color(0xFF5A3923)
private val Gravel = Color(0xFF46433D)
private val DuckTop = Color(0xFFFFD43B)
private val DuckSide = Color(0xFFEDB100)
private val Beak = Color(0xFFFF8A2A)
private val Accent = Color(0xFF7FB2FF)
private val Gold = Color(0xFFFFC94D)

/** Dessine Debug Road en fausse 3D (vue de face plongeante) avec Canvas. */
class GameRenderer {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var w = 0f
    private var h = 0f
    private var ts = 0f // largeur d'une case
    private var td = 0f // profondeur d'une rangée à l'écran
    private var ox = 0f
    private var cam = 0f
    private var time = 0f

    /** Bord avant (bas) de la rangée [r] à l'écran. */
    private fun front(r: Float) = h - (r - cam) * td
    private fun colX(c: Float) = ox + c * ts

    fun draw(scope: DrawScope, g: DebugRoadGame, best: Int, newRecord: Boolean) = with(scope) {
        w = size.width
        h = size.height
        ts = w / 17f
        td = ts * 0.8f
        ox = (w - COLS * ts) / 2f
        cam = g.cam
        time = g.time

        val first = floor(cam).toInt() - 1
        val last = first + ceil(h / td).toInt() + 3

        // 1. Sols
        for (r in first..last) ground(g.lane(r), r)
        // 2. Objets, du fond vers l'avant, avec le canard à sa place
        val duckRow = floor(displayRow(g) + 0.5f).toInt()
        for (r in last downTo first) {
            g.lane(r)?.let { objects(it, r) }
            if (r == duckRow) drawDuckState(g)
        }
        // 3. Bords hors jeu assombris
        drawRect(Color.Black.copy(alpha = 0.38f), Offset.Zero, Size(ox, h))
        drawRect(Color.Black.copy(alpha = 0.38f), Offset(ox + COLS * ts, 0f), Size(ox, h))
        // 4. Textes flottants
        for (f in g.floating) {
            val y = front(f.row) - td * (0.9f + f.age * 0.8f)
            text(f.text, colX(f.x + 0.5f), y, h * 0.032f, Gold.copy(alpha = 1f - f.age / 0.9f), Paint.Align.CENTER, bold = true)
        }
        // 5. Interface
        hud(g, best)
        when (g.phase) {
            Phase.READY -> titleScreen(best)
            Phase.DEAD -> deathScreen(g, best, newRecord)
            else -> Unit
        }
    }

    private fun displayRow(g: DebugRoadGame) = if (g.phase == Phase.PLAYING || g.phase == Phase.READY) g.visualRow else g.deathRow

    // --- Sols -------------------------------------------------------------------------

    private fun DrawScope.ground(lane: Lane?, r: Int) {
        val y0 = front(r.toFloat()) - td
        if (lane == null) {
            drawRect(Color(0xFF123D2A), Offset(0f, y0), Size(w, td + 1f))
            return
        }
        when (lane.type) {
            LaneType.SAFE -> {
                drawRect(if (r % 2 == 0) Pcb1 else Pcb2, Offset(0f, y0), Size(w, td + 1f))
                // Pistes de cuivre et pastilles
                val s = lane.seed
                for (k in 0 until 3) {
                    val yy = y0 + td * (0.22f + 0.28f * k)
                    val a = colX(((s ushr (k * 4)) and 15) - 1f)
                    val b = a + ts * (2 + ((s ushr (k * 3 + 1)) and 7))
                    drawLine(Trace, Offset(a, yy), Offset(b, yy), 2.5f)
                    drawLine(Trace, Offset(b, yy), Offset(b + td * 0.2f, yy + td * 0.2f), 2.5f)
                    drawCircle(Trace, 4.5f, Offset(a, yy))
                }
            }
            LaneType.ROAD -> {
                drawRect(Asphalt, Offset(0f, y0), Size(w, td + 1f))
                // Pointillés entre deux voies du bus de données
                if (r > 0) {
                    val dash = ts * 0.45f
                    var x = 0f
                    while (x < w) {
                        drawLine(Color.White.copy(alpha = 0.22f), Offset(x, y0 + td), Offset(x + dash, y0 + td), 3f)
                        x += dash * 2
                    }
                }
                // Bits qui défilent au sol
                val bits = if (lane.seed % 2 == 0) "0101" else "1100"
                val shift = (time * lane.speed * lane.dir * ts * 0.3f) % (ts * 4)
                var x = -ts * 4 + shift
                while (x < w + ts * 4) {
                    text(bits, x, y0 + td * 0.62f, td * 0.22f, Color.White.copy(alpha = 0.05f), Paint.Align.LEFT, mono = true)
                    x += ts * 4
                }
            }
            LaneType.WATER -> {
                drawRect(
                    Brush.verticalGradient(listOf(Color(0xFF4A2D1A), Coffee), startY = y0, endY = y0 + td),
                    Offset(0f, y0), Size(w, td + 1f),
                )
                for (k in 0 until 3) {
                    val yy = y0 + td * (0.25f + k * 0.25f)
                    val shift = ((time * 0.6f * lane.dir + k * 0.37f) * ts) % (ts * 1.5f)
                    var x = -ts * 1.5f + shift
                    while (x < w) {
                        drawLine(Color(0x33E8C9A0), Offset(x, yy), Offset(x + ts * 0.5f, yy), 3f, StrokeCap.Round)
                        x += ts * 1.5f
                    }
                }
            }
            LaneType.RAIL -> {
                drawRect(Gravel, Offset(0f, y0), Size(w, td + 1f))
                var x = 0f
                while (x < w) {
                    drawRect(Color(0xFF5E4630), Offset(x, y0 + td * 0.18f), Size(ts * 0.16f, td * 0.64f))
                    x += ts * 0.5f
                }
                drawLine(Color(0xFFB8C0CC), Offset(0f, y0 + td * 0.3f), Offset(w, y0 + td * 0.3f), 4f)
                drawLine(Color(0xFFB8C0CC), Offset(0f, y0 + td * 0.7f), Offset(w, y0 + td * 0.7f), 4f)
            }
        }
    }

    // --- Objets -----------------------------------------------------------------------

    private fun DrawScope.objects(lane: Lane, r: Int) {
        val rf = r.toFloat()
        when (lane.type) {
            LaneType.SAFE -> {
                for (c in 0 until COLS) obstacle(lane.obstacles[c], c, rf)
                if (lane.coffee >= 0) coffeeCup(lane.coffee, rf)
            }
            LaneType.ROAD -> for (m in lane.movers) vehicle(m, rf, lane.dir)
            LaneType.WATER -> for (m in lane.movers) floater(m, rf)
            LaneType.RAIL -> {
                signal(rf, lane.trainState)
                lane.train?.let { train(it, rf, lane.dir) }
            }
        }
    }

    /** Pavé : face avant + dessus. [frontY] = bord avant de l'emprise au sol. */
    private fun DrawScope.block(left: Float, width: Float, frontY: Float, depth: Float, height: Float, top: Color, side: Color, radius: Float = 0f) {
        drawRoundRect(side, Offset(left, frontY - height - radius), Size(width, height + radius), CornerRadius(radius))
        drawRoundRect(top, Offset(left, frontY - height - depth), Size(width, depth), CornerRadius(radius))
    }

    private fun DrawScope.obstacle(kind: Int, c: Int, rf: Float) {
        if (kind == Obstacle.NONE) return
        val cx = colX(c + 0.5f)
        val fy = front(rf) - td * 0.12f
        when (kind) {
            Obstacle.CHIP -> {
                val bw = ts * 0.72f
                for (k in 0 until 6) {
                    val px = cx - bw / 2 + bw * (k + 0.5f) / 6
                    drawLine(Color(0xFFC9CED6), Offset(px, fy - td * 0.06f), Offset(px, fy + td * 0.02f), 3f)
                }
                block(cx - bw / 2, bw, fy - td * 0.04f, td * 0.66f, td * 0.15f, Color(0xFF2A2F3A), Color(0xFF1A1E26), 4f)
                text("CPU", cx, fy - td * 0.45f, td * 0.2f, Color(0xFF9AA3B2), Paint.Align.CENTER, mono = true, bold = true)
                drawCircle(Color(0xFF9AA3B2), td * 0.03f, Offset(cx - bw * 0.36f, fy - td * 0.68f))
            }
            Obstacle.CAPACITOR -> {
                val bw = ts * 0.44f
                val bh = td * 0.62f
                drawRect(
                    Brush.horizontalGradient(listOf(Color(0xFF1F4FA0), Color(0xFF4A86E8), Color(0xFF1C478F)), cx - bw / 2, cx + bw / 2),
                    Offset(cx - bw / 2, fy - td * 0.2f - bh), Size(bw, bh),
                )
                drawRect(Color(0xFFDCE6F5), Offset(cx + bw * 0.18f, fy - td * 0.2f - bh), Size(bw * 0.14f, bh))
                drawOval(Color(0xFF1C478F), Offset(cx - bw / 2, fy - td * 0.32f), Size(bw, td * 0.24f))
                drawOval(Color(0xFFB9C4D4), Offset(cx - bw / 2, fy - td * 0.2f - bh - td * 0.12f), Size(bw, td * 0.24f))
                drawLine(Color(0xFF7D8796), Offset(cx - bw * 0.2f, fy - td * 0.2f - bh), Offset(cx + bw * 0.2f, fy - td * 0.2f - bh), 3f)
            }
            Obstacle.RACK -> {
                val bw = ts * 0.8f
                val left = cx - bw / 2
                val bh = td * 0.95f
                block(left, bw, fy, td * 0.62f, bh, Color(0xFF3A3F4B), Color(0xFF262A33), 3f)
                for (k in 0 until 5) {
                    val yy = fy - bh + bh * (k + 0.5f) / 5
                    drawRect(Color(0xFF15181E), Offset(left + bw * 0.08f, yy - 4f), Size(bw * 0.6f, 6f))
                    val on = sin(time * (3f + k + c) + k * 1.7f) > -0.2f
                    drawCircle(if (on) Color(0xFF39FF88) else Color(0xFF1B3B28), 4.5f, Offset(left + bw * 0.8f, yy - 1f))
                    if (k % 2 == 0) drawCircle(if (!on) Color(0xFF3FA9FF) else Color(0xFF173049), 4.5f, Offset(left + bw * 0.9f, yy - 1f))
                }
            }
            Obstacle.FAN -> {
                val bw = ts * 0.8f
                val depth = td * 0.7f
                block(cx - bw / 2, bw, fy, depth, td * 0.18f, Color(0xFF2C313B), Color(0xFF1C2028), 6f)
                val center = Offset(cx, fy - td * 0.18f - depth / 2)
                drawOval(Color(0xFF14171D), Offset(center.x - bw * 0.4f, center.y - depth * 0.42f), Size(bw * 0.8f, depth * 0.84f))
                rotate((time * 720f) % 360f, center) {
                    for (k in 0 until 4) {
                        rotate(k * 90f, center) {
                            drawOval(Color(0xFF8C96A8), Offset(center.x - bw * 0.05f, center.y - depth * 0.38f), Size(bw * 0.12f, depth * 0.34f))
                        }
                    }
                }
                drawCircle(Color(0xFFB8C0CC), td * 0.05f, center)
            }
        }
    }

    private fun DrawScope.coffeeCup(c: Int, rf: Float) {
        val cx = colX(c + 0.5f)
        val fy = front(rf) - td * 0.3f + sin(time * 3f + c) * td * 0.04f
        val cw = ts * 0.3f
        val ch = td * 0.3f
        drawOval(Color.Black.copy(alpha = 0.25f), Offset(cx - cw * 0.6f, front(rf) - td * 0.33f), Size(cw * 1.2f, td * 0.16f))
        drawArc(Color(0xFFF2F2F2), -90f, 180f, false, Offset(cx + cw * 0.3f, fy - ch * 0.85f), Size(cw * 0.45f, ch * 0.55f), style = Stroke(5f))
        drawRect(Color(0xFFF2F2F2), Offset(cx - cw / 2, fy - ch), Size(cw, ch))
        drawRect(Color(0xFFD7DBE2), Offset(cx + cw * 0.25f, fy - ch), Size(cw * 0.25f, ch))
        drawOval(Color(0xFF6B4226), Offset(cx - cw / 2, fy - ch - td * 0.06f), Size(cw, td * 0.12f))
        for (k in 0 until 2) {
            val sx = cx - cw * 0.15f + k * cw * 0.3f
            val phase = (time * 1.2f + k * 0.5f) % 1f
            val sy = fy - ch - td * (0.12f + phase * 0.3f)
            drawLine(Color.White.copy(alpha = 0.5f * (1f - phase)), Offset(sx + sin(time * 5f + k) * 4f, sy), Offset(sx - sin(time * 5f + k) * 4f, sy - td * 0.1f), 3f, StrokeCap.Round)
        }
    }

    private fun DrawScope.vehicle(m: Mover, rf: Float, dir: Int) {
        val left = colX(m.x) + ts * 0.04f
        val width = m.length * ts - ts * 0.08f
        val fy = front(rf) - td * 0.14f
        val base = Color(m.color)
        val height = when (m.kind) {
            MoverKind.TRUCK -> td * 0.5f
            MoverKind.FAST -> td * 0.26f
            else -> td * 0.33f
        }
        val depth = td * 0.68f
        // Ombre
        drawRoundRect(Color.Black.copy(alpha = 0.25f), Offset(left + 6f, fy - depth + 6f), Size(width, depth), CornerRadius(8f))
        if (m.kind == MoverKind.FAST) {
            // Traînée de vitesse (UDP n'attend personne)
            val tail = if (dir > 0) left - ts * 0.8f else left + width
            for (k in 0 until 3) {
                val yy = fy - depth * (0.25f + k * 0.25f)
                drawLine(Color.White.copy(alpha = 0.25f), Offset(tail, yy), Offset(tail + ts * 0.8f, yy), 3f, StrokeCap.Round)
            }
        }
        if (m.kind == MoverKind.TRUCK) {
            val cabW = ts * 0.75f
            val cabLeft = if (dir > 0) left + width - cabW else left
            val boxLeft = if (dir > 0) left else left + cabW + ts * 0.05f
            val boxW = width - cabW - ts * 0.05f
            block(cabLeft, cabW, fy, depth, height * 0.75f, Color(0xFFE8ECF4), Color(0xFFB9C1CF), 6f)
            val glassX = if (dir > 0) cabLeft + cabW * 0.55f else cabLeft + cabW * 0.05f
            drawRect(Color(0xFF1A2233), Offset(glassX, fy - height * 0.75f - depth * 0.85f), Size(cabW * 0.4f, depth * 0.7f))
            block(boxLeft, boxW, fy, depth, height, lighten(base), base, 6f)
            fitText(m.label, boxLeft + boxW / 2, fy - height - depth * 0.38f, boxW * 0.9f, td * 0.28f, Color.White)
            wheels(boxLeft, boxW, fy)
            wheels(cabLeft, cabW, fy)
        } else {
            block(left, width, fy, depth, height, lighten(base), base, 8f)
            val glassW = width * 0.32f
            val glassX = if (dir > 0) left + width * 0.6f else left + width * 0.08f
            drawRoundRect(Color(0xCC1A2233), Offset(glassX, fy - height - depth * 0.85f), Size(glassW, depth * 0.7f), CornerRadius(6f))
            val labelX = if (dir > 0) left + width * 0.3f else left + width * 0.7f
            fitText(m.label, labelX, fy - height - depth * 0.38f, width * 0.5f, td * 0.22f, Color.White)
            // Phares
            val lightX = if (dir > 0) left + width - 8f else left + 2f
            drawRect(Color(0xFFFFF3B0), Offset(lightX, fy - height * 0.75f), Size(6f, height * 0.3f))
            wheels(left, width, fy)
        }
    }

    private fun DrawScope.wheels(left: Float, width: Float, fy: Float) {
        drawCircle(Color(0xFF111318), td * 0.09f, Offset(left + width * 0.2f, fy - td * 0.02f))
        drawCircle(Color(0xFF111318), td * 0.09f, Offset(left + width * 0.8f, fy - td * 0.02f))
    }

    private fun DrawScope.floater(m: Mover, rf: Float) {
        val fy = front(rf) - td * 0.14f
        val depth = td * 0.72f
        if (m.kind == MoverKind.FLOPPY) {
            val n = m.length.toInt()
            for (k in 0 until n) {
                val left = colX(m.x + k) + ts * 0.05f
                val bw = ts * 0.9f
                block(left, bw, fy, depth, td * 0.08f, Color(0xFF2B3C66), Color(0xFF1A2540), 6f)
                val topY = fy - td * 0.08f - depth
                drawRect(Color(0xFFC7CCD6), Offset(left + bw * 0.25f, topY + depth * 0.04f), Size(bw * 0.45f, depth * 0.34f))
                drawRect(Color(0xFF6E7684), Offset(left + bw * 0.52f, topY + depth * 0.08f), Size(bw * 0.1f, depth * 0.24f))
                drawRect(Color(0xFFF4F1E8), Offset(left + bw * 0.14f, topY + depth * 0.5f), Size(bw * 0.72f, depth * 0.42f))
                text(m.label, left + bw / 2, topY + depth * 0.8f, td * 0.16f, Color(0xFF2B3C66), Paint.Align.CENTER, mono = true, bold = true)
            }
        } else {
            val left = colX(m.x) + ts * 0.05f
            val bw = m.length * ts - ts * 0.1f
            block(left, bw, fy, depth * 0.8f, td * 0.08f, Color(0xFF2E7D4F), Color(0xFF1E5435), 4f)
            // Contacts dorés
            var x = left + 6f
            while (x < left + bw - 8f) {
                drawRect(Color(0xFFE2B33C), Offset(x, fy - td * 0.07f), Size(5f, td * 0.06f))
                x += 11f
            }
            // Puces mémoire
            val topY = fy - td * 0.08f - depth * 0.8f
            val chips = m.length.toInt() * 2
            for (k in 0 until chips) {
                val cx = left + bw * (k + 0.5f) / chips
                drawRect(Color(0xFF15181E), Offset(cx - bw / chips * 0.35f, topY + depth * 0.15f), Size(bw / chips * 0.7f, depth * 0.45f))
            }
            text(m.label, left + bw / 2, topY + depth * 0.74f, td * 0.15f, Color(0xFFE2E8D8), Paint.Align.CENTER, mono = true, bold = true)
        }
    }

    private fun DrawScope.signal(rf: Float, state: Int) {
        val x = ox - ts * 0.45f
        val fy = front(rf) - td * 0.2f
        drawRect(Color(0xFF2A2D35), Offset(x - 4f, fy - td * 1.1f), Size(8f, td * 1.1f))
        drawRoundRect(Color(0xFF15171C), Offset(x - td * 0.16f, fy - td * 1.4f), Size(td * 0.32f, td * 0.36f), CornerRadius(8f))
        val blink = state == 1 && sin(time * 18f) > 0f || state == 2
        drawCircle(if (blink) Color(0xFFFF3B3B) else Color(0xFF4A1C1C), td * 0.1f, Offset(x, fy - td * 1.22f))
        if (blink) {
            drawCircle(Color(0x55FF3B3B), td * 0.22f, Offset(x, fy - td * 1.22f))
            text("!", colX(COLS / 2f), front(rf) - td * 0.3f, td * 0.5f, Color(0x66FF3B3B), Paint.Align.CENTER, bold = true)
        }
    }

    private fun DrawScope.train(m: Mover, rf: Float, dir: Int) {
        val fy = front(rf) - td * 0.12f
        val depth = td * 0.72f
        val wagon = 3.2f
        var k = 0f
        while (k < m.length - 0.01f) {
            val len = min(wagon, m.length - k)
            val left = colX(m.x + k) + 3f
            val width = len * ts - 6f
            val isLoco = if (dir > 0) k + len >= m.length - 0.01f else k == 0f
            val base = if (isLoco) Color(0xFFB72E2E) else Color(0xFFD84343)
            block(left, width, fy, depth, td * 0.62f, lighten(base), base, 6f)
            var wx = left + ts * 0.25f
            while (wx < left + width - ts * 0.4f) {
                drawRect(Color(0xFFFFE9A8), Offset(wx, fy - td * 0.5f), Size(ts * 0.3f, td * 0.18f))
                wx += ts * 0.55f
            }
            val label = if (isLoco) m.label else "PROD"
            fitText(label, left + width / 2, fy - td * 0.62f - depth * 0.38f, width * 0.9f, td * 0.26f, Color.White)
            k += wagon
        }
    }

    // --- Canard -----------------------------------------------------------------------

    private fun DrawScope.drawDuckState(g: DebugRoadGame) {
        when (g.phase) {
            Phase.READY, Phase.PLAYING -> {
                val bump = if (g.bumpT < 1f) sin(g.bumpT * PI.toFloat()) * 0.08f else 0f
                duck(g.visualX, g.visualRow, g.hopLift + bump, g.facing)
            }
            Phase.DYING, Phase.DEAD -> deadDuck(g)
        }
    }

    private fun DrawScope.duck(x: Float, rowF: Float, lift: Float, facing: Int, alpha: Float = 1f, sink: Float = 0f) {
        val cx = colX(x + 0.5f)
        val ground = front(rowF) - td * 0.2f
        val z = lift * td - sink * td
        // Ombre
        val shadowW = ts * 0.58f * (1f - min(lift, 0.6f) * 0.5f)
        drawOval(Color.Black.copy(alpha = 0.28f * alpha), Offset(cx - shadowW / 2, ground - td * 0.44f), Size(shadowW, td * 0.3f))

        val bw = ts * 0.6f
        val bd = td * 0.55f
        val bh = td * 0.32f
        val fy = ground - z
        val left = cx - bw / 2
        // Corps
        block(left, bw, fy, bd, bh, DuckTop.copy(alpha = alpha), DuckSide.copy(alpha = alpha), 10f)
        // Aile
        drawRoundRect(Color(0xFFE09F00).copy(alpha = alpha), Offset(left + bw * 0.12f, fy - bh * 0.8f), Size(bw * 0.4f, bh * 0.5f), CornerRadius(8f))
        // Tête
        val hw = ts * 0.38f
        val hd = td * 0.3f
        val hh = td * 0.28f
        val shiftX = when (facing) { 1 -> ts * 0.1f; 3 -> -ts * 0.1f; else -> 0f }
        val headFront = when (facing) {
            0 -> fy - bh - bd * 0.5f
            2 -> fy - bh - bd * 0.02f
            else -> fy - bh - bd * 0.25f
        }
        val hl = cx - hw / 2 + shiftX
        block(hl, hw, headFront, hd, hh, DuckTop.copy(alpha = alpha), DuckSide.copy(alpha = alpha), 8f)
        val black = Color(0xFF1A1A1A).copy(alpha = alpha)
        val eyeY = headFront - hh * 0.62f
        when (facing) {
            2 -> {
                drawRoundRect(Beak.copy(alpha = alpha), Offset(cx - hw * 0.18f, headFront - hh * 0.38f), Size(hw * 0.36f, hh * 0.26f), CornerRadius(5f))
                drawCircle(black, td * 0.032f, Offset(cx - hw * 0.22f, eyeY))
                drawCircle(black, td * 0.032f, Offset(cx + hw * 0.22f, eyeY))
                // Lunettes de dev
                val gs = td * 0.075f
                drawRect(black, Offset(cx - hw * 0.22f - gs, eyeY - gs), Size(gs * 2, gs * 2), style = Stroke(3f))
                drawRect(black, Offset(cx + hw * 0.22f - gs, eyeY - gs), Size(gs * 2, gs * 2), style = Stroke(3f))
                drawLine(black, Offset(cx - hw * 0.22f + gs, eyeY), Offset(cx + hw * 0.22f - gs, eyeY), 3f)
            }
            1, 3 -> {
                val sgn = if (facing == 1) 1f else -1f
                val edge = if (facing == 1) hl + hw else hl
                drawRoundRect(Beak.copy(alpha = alpha), Offset(if (facing == 1) edge - 2f else edge - ts * 0.12f + 2f, headFront - hh * 0.45f), Size(ts * 0.12f, hh * 0.24f), CornerRadius(5f))
                val ex = cx + shiftX + sgn * hw * 0.22f
                drawCircle(black, td * 0.032f, Offset(ex, eyeY))
                val gs = td * 0.075f
                drawRect(black, Offset(ex - gs, eyeY - gs), Size(gs * 2, gs * 2), style = Stroke(3f))
            }
            else -> {
                // De dos : une petite mèche
                drawLine(DuckSide.copy(alpha = alpha), Offset(cx, headFront - hh - hd * 0.5f), Offset(cx + ts * 0.04f, headFront - hh - hd * 0.5f - td * 0.12f), 5f, StrokeCap.Round)
            }
        }
    }

    private fun DrawScope.deadDuck(g: DebugRoadGame) {
        val t = g.deathTime
        val cx = colX(g.deathX + 0.5f)
        val ground = front(g.deathRow) - td * 0.45f
        when (g.deathCause) {
            DeathCause.CAR, DeathCause.TRAIN -> {
                val sw = ts * 0.95f
                drawOval(DuckSide, Offset(cx - sw / 2, ground - td * 0.12f), Size(sw, td * 0.34f))
                drawOval(DuckTop, Offset(cx - sw * 0.42f, ground - td * 0.1f), Size(sw * 0.84f, td * 0.26f))
                val black = Color(0xFF1A1A1A)
                for (s in listOf(-1f, 1f)) {
                    val ex = cx + s * sw * 0.15f
                    val ey = ground + td * 0.03f
                    val e = td * 0.04f
                    drawLine(black, Offset(ex - e, ey - e), Offset(ex + e, ey + e), 3f)
                    drawLine(black, Offset(ex - e, ey + e), Offset(ex + e, ey - e), 3f)
                }
                drawRoundRect(Beak, Offset(cx - ts * 0.06f, ground + td * 0.06f), Size(ts * 0.12f, td * 0.06f), CornerRadius(4f))
                if (t < 0.8f) text("CRASH", cx, ground - td * (0.4f + t), td * 0.32f, Color(0xFFFF5D5D).copy(alpha = 1f - t / 0.8f), Paint.Align.CENTER, bold = true)
            }
            DeathCause.WATER -> {
                val sink = min(t / 0.5f, 1f)
                if (sink < 1f) duck(g.deathX, g.deathRow, 0f, 2, alpha = 1f - sink, sink = sink * 0.5f)
                for (k in 0 until 3) {
                    val a = t - k * 0.18f
                    if (a <= 0f || a > 1f) continue
                    val rw = ts * (0.3f + a * 1.1f)
                    drawOval(Color(0xFFE8C9A0).copy(alpha = 0.6f * (1f - a)), Offset(cx - rw / 2, ground - rw * 0.2f), Size(rw, rw * 0.4f), style = Stroke(4f))
                }
            }
            DeathCause.TIMEOUT -> {
                duck(g.deathX, g.deathRow, 0.4f, 2)
                // Grand curseur qui vient attraper le canard
                val arrive = min(t / 0.5f, 1f)
                val tipY = ground - td * 1.0f - (1f - arrive) * h
                val tip = Offset(cx + ts * 0.1f, tipY)
                val s = td * 1.3f
                val cursor = Path().apply {
                    moveTo(tip.x, tip.y)
                    lineTo(tip.x, tip.y - s)
                    lineTo(tip.x + s * 0.28f, tip.y - s * 0.72f)
                    lineTo(tip.x + s * 0.48f, tip.y - s * 1.15f)
                    lineTo(tip.x + s * 0.62f, tip.y - s * 1.08f)
                    lineTo(tip.x + s * 0.42f, tip.y - s * 0.65f)
                    lineTo(tip.x + s * 0.78f, tip.y - s * 0.62f)
                    close()
                }
                // Le curseur est dessiné pointe en haut puis retourné pour viser le canard
                rotate(180f, tip) {
                    drawPath(cursor, Color.White)
                    drawPath(cursor, Color.Black, style = Stroke(5f))
                }
            }
            DeathCause.OVERFLOW -> {
                val fall = min(t / 0.6f, 1f)
                duck(g.deathX, g.deathRow, -fall, 2, alpha = 1f - fall)
                if (t < 0.9f) text("OVERFLOW", cx, ground - td * (0.6f + t), td * 0.28f, Color(0xFFFFC94D).copy(alpha = 1f - t / 0.9f), Paint.Align.CENTER, bold = true)
            }
        }
    }

    // --- Interface --------------------------------------------------------------------

    private fun DrawScope.hud(g: DebugRoadGame, best: Int) {
        drawRect(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent), 0f, h * 0.24f), Offset.Zero, Size(w, h * 0.24f))
        val x = w * 0.035f
        text("${g.score}", x, h * 0.115f, h * 0.085f, Color.White, Paint.Align.LEFT, bold = true, shadow = true)
        text("lignes de code", x, h * 0.155f, h * 0.026f, Color(0xCCFFFFFF), Paint.Align.LEFT)
        text(g.rank, x, h * 0.195f, h * 0.03f, Accent, Paint.Align.LEFT, bold = true, shadow = true)

        val rx = w * 0.965f
        text("Record : $best", rx, h * 0.07f, h * 0.03f, Color.White, Paint.Align.RIGHT, bold = true, shadow = true)
        text("${g.coffees}", rx, h * 0.125f, h * 0.034f, Gold, Paint.Align.RIGHT, bold = true, shadow = true)
        miniCup(rx - h * 0.034f * (g.coffees.toString().length * 0.6f + 0.9f), h * 0.125f)

        g.promotion?.let { label ->
            val a = g.promotionAge
            val pop = if (a < 0.3f) 0.6f + 0.4f * easeOutBack(a / 0.3f) else 1f
            val alpha = if (a > 2f) 1f - (a - 2f) / 0.4f else 1f
            val size = h * 0.045f * pop
            paint.textSize = size
            paint.typeface = Typeface.DEFAULT_BOLD
            val tw = paint.measureText(label)
            val pw = tw + size * 1.6f
            val ph = size * 1.7f
            drawRoundRect(Gold.copy(alpha = alpha), Offset(w / 2 - pw / 2, h * 0.2f - ph * 0.72f), Size(pw, ph), CornerRadius(ph / 2))
            text(label, w / 2, h * 0.2f, size, Color(0xFF2A1E00).copy(alpha = alpha), Paint.Align.CENTER, bold = true)
        }

        g.toast?.let { message ->
            val a = g.toastAge
            val slide = when {
                a < 0.35f -> 1f - easeOutBack(a / 0.35f)
                a > 2.8f -> (a - 2.8f) / 0.4f
                else -> 0f
            }
            val cw = w * 0.27f
            val ch = h * 0.105f
            val left = w - cw - w * 0.025f + slide * (cw + w * 0.05f)
            val top = h * 0.22f
            drawRoundRect(Color(0x55000000), Offset(left + 6f, top + 8f), Size(cw, ch), CornerRadius(16f))
            drawRoundRect(Color(0xF7FFFFFF), Offset(left, top), Size(cw, ch), CornerRadius(16f))
            drawRoundRect(Accent, Offset(left + ch * 0.18f, top + ch * 0.22f), Size(ch * 0.56f, ch * 0.56f), CornerRadius(10f))
            text("!", left + ch * 0.46f, top + ch * 0.64f, ch * 0.4f, Color.White, Paint.Align.CENTER, bold = true)
            text("Notification", left + ch * 0.95f, top + ch * 0.4f, ch * 0.2f, Color(0xFF6B7280), Paint.Align.LEFT)
            fitTextLeft(message, left + ch * 0.95f, top + ch * 0.72f, cw - ch * 1.1f, ch * 0.26f, Color(0xFF111827))
        }
    }

    private fun DrawScope.miniCup(x: Float, baseline: Float) {
        val s = h * 0.03f
        drawRect(Color.White, Offset(x - s * 0.5f, baseline - s), Size(s, s))
        drawArc(Color.White, -90f, 180f, false, Offset(x + s * 0.3f, baseline - s * 0.85f), Size(s * 0.5f, s * 0.55f), style = Stroke(4f))
        drawRect(Color(0xFF6B4226), Offset(x - s * 0.42f, baseline - s * 0.92f), Size(s * 0.84f, s * 0.2f))
    }

    private fun DrawScope.titleScreen(best: Int) {
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color(0xAA000000), Color(0xAA000000), Color.Transparent), h * 0.22f, h * 0.74f), Offset(0f, h * 0.22f), Size(w, h * 0.52f))
        val wobble = sin(time * 2f) * 3f
        text("DEBUG ROAD", w / 2, h * 0.4f + wobble, h * 0.12f, Gold, Paint.Align.CENTER, bold = true, shadow = true)
        text("Aide le canard de debug à traverser la prod", w / 2, h * 0.47f, h * 0.034f, Color.White, Paint.Align.CENTER, shadow = true)
        text("▲ avancer    ◀ ▶ ▼ esquiver    Retour : quitter", w / 2, h * 0.54f, h * 0.028f, Color(0xCCFFFFFF), Paint.Align.CENTER, mono = true)
        if (best > 0) text("Record : $best lignes de code", w / 2, h * 0.6f, h * 0.028f, Accent, Paint.Align.CENTER, bold = true)
        if (sin(time * 5f) > -0.3f) text("Appuie sur ▲ pour lancer le build", w / 2, h * 0.68f, h * 0.034f, Color.White, Paint.Align.CENTER, bold = true, shadow = true)
    }

    private fun DrawScope.deathScreen(g: DebugRoadGame, best: Int, newRecord: Boolean) {
        val appear = min((g.deathTime - 1f) / 0.25f, 1f).coerceAtLeast(0f)
        drawRect(Color.Black.copy(alpha = 0.55f * appear))
        val pw = w * 0.58f
        val ph = h * 0.66f
        val left = (w - pw) / 2
        val top = (h - ph) / 2 + (1f - appear) * h * 0.08f
        drawRoundRect(Color(0xF00D1117).copy(alpha = 0.96f * appear), Offset(left, top), Size(pw, ph), CornerRadius(18f))
        // Barre de titre façon terminal
        drawRoundRect(Color(0xFF1F2430).copy(alpha = appear), Offset(left, top), Size(pw, h * 0.06f), CornerRadius(18f))
        drawRect(Color(0xFF1F2430).copy(alpha = appear), Offset(left, top + h * 0.03f), Size(pw, h * 0.03f))
        listOf(Color(0xFFFF5F56), Color(0xFFFFBD2E), Color(0xFF27C93F)).forEachIndexed { i, c ->
            drawCircle(c.copy(alpha = appear), h * 0.011f, Offset(left + h * 0.035f + i * h * 0.032f, top + h * 0.03f))
        }
        text("canard@prod: ~", left + pw / 2, top + h * 0.04f, h * 0.022f, Color(0xFF9AA3B2).copy(alpha = appear), Paint.Align.CENTER, mono = true)

        val x = left + pw * 0.06f
        var y = top + h * 0.15f
        val line = h * 0.047f
        fun row(s: String, color: Color, size: Float = h * 0.03f, bold: Boolean = false) {
            fitTextLeft(s, x, y, pw * 0.88f, size, color.copy(alpha = appear), mono = true, bold = bold)
            y += line
        }
        row("FATAL ERROR", Color(0xFFFF5F56), h * 0.05f, bold = true)
        y += line * 0.2f
        row(g.deathMessage, Color.White, h * 0.031f, bold = true)
        row("  at Canard.traverser(Route.kt:${g.score})", Color(0xFF8B949E), h * 0.024f)
        row("  at Sprint.livrer(Vendredi.kt:17)", Color(0xFF8B949E), h * 0.024f)
        row("  at Cafe.recharger(Machine.kt:${g.coffees})", Color(0xFF8B949E), h * 0.024f)
        y += line * 0.4f
        row("Lignes de code : ${g.score}", Color(0xFF3FB950), bold = true)
        row(if (newRecord) "Record : $best   NOUVEAU RECORD !" else "Record : $best", Gold, bold = newRecord)
        row("Grade : ${g.rank}", Accent)
        y += line * 0.4f
        val cursor = if (sin(time * 6f) > 0f) "_" else " "
        row("> OK : relancer le build$cursor", Color.White, bold = true)
        row("  Retour : quitter", Color(0xFF8B949E), h * 0.024f)
    }

    // --- Texte --------------------------------------------------------------------------

    private fun DrawScope.text(
        s: String,
        x: Float,
        y: Float,
        size: Float,
        color: Color,
        align: Paint.Align,
        bold: Boolean = false,
        mono: Boolean = false,
        shadow: Boolean = false,
    ) {
        paint.textSize = size
        paint.color = color.toArgb()
        paint.textAlign = align
        paint.typeface = when {
            mono && bold -> Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
            mono -> Typeface.MONOSPACE
            bold -> Typeface.DEFAULT_BOLD
            else -> Typeface.DEFAULT
        }
        if (shadow) paint.setShadowLayer(size * 0.12f, 0f, size * 0.06f, 0xAA000000.toInt()) else paint.clearShadowLayer()
        drawContext.canvas.nativeCanvas.drawText(s, x, y, paint)
        paint.clearShadowLayer()
    }

    /** Texte centré réduit pour tenir dans [maxWidth]. */
    private fun DrawScope.fitText(s: String, cx: Float, y: Float, maxWidth: Float, size: Float, color: Color) {
        paint.textSize = size
        paint.typeface = Typeface.DEFAULT_BOLD
        val tw = paint.measureText(s)
        val fitted = if (tw > maxWidth) size * maxWidth / tw else size
        text(s, cx, y, fitted, color, Paint.Align.CENTER, bold = true)
    }

    private fun DrawScope.fitTextLeft(s: String, x: Float, y: Float, maxWidth: Float, size: Float, color: Color, mono: Boolean = false, bold: Boolean = false) {
        paint.textSize = size
        paint.typeface = if (mono) Typeface.MONOSPACE else Typeface.DEFAULT
        val tw = paint.measureText(s)
        val fitted = if (tw > maxWidth) size * maxWidth / tw else size
        text(s, x, y, fitted, color, Paint.Align.LEFT, bold = bold, mono = mono)
    }

    private fun lighten(c: Color) = Color(
        red = min(1f, c.red + (1f - c.red) * 0.28f),
        green = min(1f, c.green + (1f - c.green) * 0.28f),
        blue = min(1f, c.blue + (1f - c.blue) * 0.28f),
        alpha = c.alpha,
    )

    private fun easeOutBack(x: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val p = x - 1f
        return 1f + c3 * p * p * p + c1 * p * p
    }
}
