package fr.nrobeveille.tvlauncher.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/** Largeur jouable, en cases. */
const val COLS = 15

/** Les véhicules circulent sur une boucle qui déborde de [MARGIN] cases de chaque côté. */
private const val MARGIN = 6f
private const val LOOP = COLS + 2 * MARGIN

/** Durée d'un saut, en secondes. */
const val HOP_DURATION = 0.13f

enum class LaneType { SAFE, ROAD, WATER, RAIL }
enum class Phase { READY, PLAYING, DYING, DEAD }
enum class DeathCause { CAR, WATER, TRAIN, TIMEOUT, OVERFLOW }
enum class MoverKind { CAR, TRUCK, FAST, FLOPPY, RAM, TRAIN }

/** Obstacles des voies « open space » (circuit imprimé). */
object Obstacle {
    const val NONE = 0
    const val CHIP = 1
    const val CAPACITOR = 2
    const val RACK = 3
    const val FAN = 4
}

class Mover(var x: Float, val length: Float, val kind: MoverKind, val label: String, val color: Long)

class Lane(val row: Int, val type: LaneType, val dir: Int, val speed: Float, val seed: Int) {
    val movers = ArrayList<Mover>()
    val obstacles = IntArray(COLS)
    var coffee = -1

    // Voie de mise en prod : 0 = attente, 1 = alerte, 2 = passage du train
    var trainState = 0
    var trainTimer = 0f
    var train: Mover? = null

    fun update(dt: Float, random: Random) {
        when (type) {
            LaneType.ROAD, LaneType.WATER -> for (m in movers) {
                m.x += dir * speed * dt
                if (dir > 0 && m.x > COLS + MARGIN) m.x -= LOOP
                if (dir < 0 && m.x + m.length < -MARGIN) m.x += LOOP
            }
            LaneType.RAIL -> when (trainState) {
                0 -> {
                    trainTimer -= dt
                    if (trainTimer <= 0f) {
                        trainState = 1
                        trainTimer = 1.3f
                    }
                }
                1 -> {
                    trainTimer -= dt
                    if (trainTimer <= 0f) {
                        trainState = 2
                        val length = 13f
                        val start = if (dir > 0) -MARGIN - length else COLS + MARGIN
                        train = Mover(start, length, MoverKind.TRAIN, "MEP VENDREDI 17H", 0xFFD84343)
                    }
                }
                else -> {
                    val t = train
                    if (t == null) {
                        trainState = 0
                    } else {
                        t.x += dir * 32f * dt
                        val gone = if (dir > 0) t.x > COLS + MARGIN else t.x + t.length < -MARGIN
                        if (gone) {
                            train = null
                            trainState = 0
                            trainTimer = 2.5f + random.nextFloat() * 4.5f
                        }
                    }
                }
            }
            LaneType.SAFE -> Unit
        }
    }
}

class FloatingText(val text: String, val x: Float, val row: Float, var age: Float = 0f)

/**
 * Debug Road : le canard de debug doit traverser l'open space, le bus de données,
 * la rivière de café et le pipeline de mise en prod. Score = lignes de code (rangées).
 */
class DebugRoadGame(seed: Long = System.nanoTime()) {

    private val random = Random(seed)
    val lanes = HashMap<Int, Lane>()
    private var generatedUpTo = -1
    private var groupType = LaneType.SAFE
    private var groupRemaining = 0
    private var lastWaterDir = 1

    var phase = Phase.READY
        private set
    var time = 0f
        private set

    // Canard
    var px = (COLS / 2).toFloat()
        private set
    var row = 0
        private set
    private var fromX = px
    private var toX = px
    private var fromRow = 0
    private var toRow = 0
    var hopT = 1f
        private set
    var facing = 0 // 0 haut, 1 droite, 2 bas, 3 gauche
        private set
    private var ride: Mover? = null
    private var rideLastX = 0f
    private var pending: Pair<Int, Int>? = null
    var bumpT = 1f
        private set

    var cam = -2.2f
        private set
    var score = 0
        private set
    var coffees = 0
        private set

    var deathCause = DeathCause.CAR
        private set
    var deathMessage = ""
        private set
    var deathTime = 0f
        private set
    var deathX = 0f
        private set
    var deathRow = 0f
        private set
    /** Passe à true une fois au moment de la mort (pour enregistrer le record). */
    var justDied = false

    // Messages
    var promotion: String? = null
        private set
    var promotionAge = 0f
        private set
    var toast: String? = null
        private set
    var toastAge = 0f
        private set
    private var nextToastRow = 20
    private var toastPool = TOASTS.shuffled(random).toMutableList()
    val floating = ArrayList<FloatingText>()

    init {
        ensureLanes(30)
    }

    // --- Position affichée -----------------------------------------------------------

    val visualX: Float get() = fromX + (toX - fromX) * easeHop(hopT)
    val visualRow: Float get() = fromRow + (toRow - fromRow) * easeHop(hopT)
    /** Hauteur du saut, en fraction de case. */
    val hopLift: Float get() = if (hopT < 1f) sin(PI.toFloat() * hopT) * 0.45f else 0f

    val rank: String get() = rankFor(score)

    fun lane(r: Int): Lane? = lanes[r]

    // --- Commandes ---------------------------------------------------------------------

    fun move(dx: Int, dy: Int) {
        if (phase == Phase.READY) phase = Phase.PLAYING
        if (phase != Phase.PLAYING) return
        if (hopT < 1f) {
            pending = dx to dy
            return
        }
        facing = when {
            dy > 0 -> 0
            dx > 0 -> 1
            dy < 0 -> 2
            else -> 3
        }
        val targetRow = row + dy
        if (targetRow < 0) {
            bumpT = 0f
            return
        }
        val target = lanes[targetRow] ?: return
        val riding = ride
        val targetX = if (dy == 0 && riding != null) px + dx else (px.roundToInt() + dx).toFloat()
        val col = targetX.roundToInt()
        if (targetX < -0.45f || targetX > COLS - 0.55f || col !in 0 until COLS) {
            bumpT = 0f
            return
        }
        if (target.type == LaneType.SAFE && target.obstacles[col] != Obstacle.NONE) {
            bumpT = 0f
            return
        }
        // Saut latéral sur une disquette : on reste porté par elle pendant le saut.
        if (dy != 0) ride = null
        fromX = px
        toX = targetX
        fromRow = row
        toRow = targetRow
        hopT = 0f
    }

    // --- Boucle ------------------------------------------------------------------------

    fun update(dt: Float) {
        time += dt
        ensureLanes(maxOf(row, score) + 24)
        for (lane in lanes.values) lane.update(dt, random)
        if (bumpT < 1f) bumpT = min(1f, bumpT + dt * 5f)
        floating.forEach { it.age += dt }
        floating.removeAll { it.age > 0.9f }
        promotion?.let { promotionAge += dt; if (promotionAge > 2.4f) promotion = null }
        toast?.let { toastAge += dt; if (toastAge > 3.2f) toast = null }

        when (phase) {
            Phase.READY -> Unit
            Phase.PLAYING -> playing(dt)
            Phase.DYING -> {
                deathTime += dt
                if (deathCause == DeathCause.TIMEOUT) deathRow += dt * (if (deathTime > 0.55f) 9f else 0f)
                if (deathTime > 1.0f) phase = Phase.DEAD
            }
            Phase.DEAD -> deathTime += dt
        }
        // On oublie les voies loin derrière la caméra.
        val minRow = (cam - 4f).toInt()
        lanes.keys.removeAll { it < minRow }
    }

    private fun playing(dt: Float) {
        // Porté par une disquette / barrette de RAM
        ride?.let { m ->
            var d = m.x - rideLastX
            if (abs(d) > LOOP / 2) d = 0f
            rideLastX = m.x
            px += d
            fromX += d
            toX += d
            if (hopT >= 1f && (px < -0.45f || px > COLS - 0.55f)) {
                die(DeathCause.OVERFLOW)
                return
            }
        }

        if (hopT < 1f) {
            hopT = min(1f, hopT + dt / HOP_DURATION)
            if (hopT >= 1f) {
                land()
                if (phase != Phase.PLAYING) return
            }
        }

        // Collisions
        val occupied = if (hopT < 0.5f) fromRow else toRow
        val lane = lanes[occupied]
        val cx = visualX + 0.5f
        if (lane != null) {
            if (lane.type == LaneType.ROAD && lane.movers.any { cx > it.x + 0.14f && cx < it.x + it.length - 0.14f }) {
                die(DeathCause.CAR)
                return
            }
            val train = lane.train
            if (lane.type == LaneType.RAIL && train != null && cx > train.x + 0.1f && cx < train.x + train.length - 0.1f) {
                die(DeathCause.TRAIN)
                return
            }
        }

        // Caméra : suit le canard et avance toute seule (de plus en plus vite)
        val target = visualRow - 3f
        if (target > cam) cam += (target - cam) * min(1f, dt * 5f)
        cam += (0.22f + min(score, 250) * 0.0017f) * dt
        if (visualRow < cam - 0.6f) die(DeathCause.TIMEOUT)
    }

    private fun land() {
        px = toX
        row = toRow
        fromX = toX
        fromRow = toRow
        val lane = lanes[row] ?: return
        when (lane.type) {
            LaneType.WATER -> {
                val cx = px + 0.5f
                val m = lane.movers.firstOrNull { cx > it.x + 0.05f && cx < it.x + it.length - 0.05f }
                if (m == null) {
                    die(DeathCause.WATER)
                    return
                }
                ride = m
                rideLastX = m.x
            }
            LaneType.SAFE -> {
                ride = null
                if (lane.coffee == px.roundToInt()) {
                    lane.coffee = -1
                    coffees++
                    floating += FloatingText("+1 café", px, row.toFloat())
                }
            }
            else -> ride = null
        }
        if (row > score) {
            val before = rankFor(score)
            score = row
            val after = rankFor(score)
            if (after != before) {
                promotion = "Promotion : $after !"
                promotionAge = 0f
            } else if (score >= nextToastRow) {
                if (toastPool.isEmpty()) toastPool = TOASTS.shuffled(random).toMutableList()
                toast = toastPool.removeAt(0)
                toastAge = 0f
                nextToastRow = score + 16 + random.nextInt(14)
            }
        }
        pending?.let { (dx, dy) ->
            pending = null
            move(dx, dy)
        }
    }

    private fun die(cause: DeathCause) {
        phase = Phase.DYING
        deathCause = cause
        deathTime = 0f
        deathX = visualX
        deathRow = visualRow
        hopT = 1f
        pending = null
        val pool = when (cause) {
            DeathCause.CAR -> DEATH_CAR
            DeathCause.WATER -> DEATH_WATER
            DeathCause.TRAIN -> DEATH_TRAIN
            DeathCause.TIMEOUT -> DEATH_TIMEOUT
            DeathCause.OVERFLOW -> DEATH_OVERFLOW
        }
        deathMessage = pool[random.nextInt(pool.size)]
        justDied = true
    }

    // --- Génération des voies ----------------------------------------------------------

    private fun ensureLanes(upTo: Int) {
        while (generatedUpTo < upTo) {
            generatedUpTo++
            lanes[generatedUpTo] = makeLane(generatedUpTo)
        }
    }

    private fun difficulty(r: Int) = 1f + min(r, 300) / 300f

    private fun makeLane(r: Int): Lane {
        if (r < 5) return safeLane(r, start = true)
        if (groupRemaining == 0) {
            if (groupType != LaneType.SAFE) {
                groupType = LaneType.SAFE
                groupRemaining = if (difficulty(r) > 1.5f) 1 else 1 + random.nextInt(2)
            } else {
                val roll = random.nextFloat()
                groupType = when {
                    roll < 0.45f -> LaneType.ROAD
                    roll < 0.75f -> LaneType.WATER
                    else -> LaneType.RAIL
                }
                val extra = (difficulty(r) * 1.5f).toInt()
                groupRemaining = when (groupType) {
                    LaneType.ROAD -> 1 + random.nextInt(2 + extra)
                    LaneType.WATER -> 1 + random.nextInt(1 + extra)
                    else -> 1 + random.nextInt(2)
                }
            }
        }
        groupRemaining--
        return when (groupType) {
            LaneType.SAFE -> safeLane(r, start = false)
            LaneType.ROAD -> roadLane(r)
            LaneType.WATER -> waterLane(r)
            LaneType.RAIL -> railLane(r)
        }
    }

    private fun safeLane(r: Int, start: Boolean): Lane {
        val lane = Lane(r, LaneType.SAFE, 1, 0f, random.nextInt())
        val kinds = intArrayOf(Obstacle.CHIP, Obstacle.CAPACITOR, Obstacle.RACK, Obstacle.FAN)
        for (c in 0 until COLS) {
            val edge = c < 2 || c >= COLS - 2
            val chance = if (start) (if (edge && r > 0) 0.5f else 0f) else 0.16f
            if (random.nextFloat() < chance) lane.obstacles[c] = kinds[random.nextInt(kinds.size)]
        }
        if (!start || r > 2) {
            val free = (0 until COLS).filter { lane.obstacles[it] == Obstacle.NONE }
            if (free.isNotEmpty() && random.nextFloat() < 0.22f) lane.coffee = free[random.nextInt(free.size)]
        }
        return lane
    }

    private fun roadLane(r: Int): Lane {
        val d = difficulty(r)
        val roll = random.nextFloat()
        val kind = when {
            roll < 0.5f -> MoverKind.CAR
            roll < 0.8f -> MoverKind.TRUCK
            else -> MoverKind.FAST
        }
        val speed = when (kind) {
            MoverKind.CAR -> 1.8f + random.nextFloat() * 1.3f
            MoverKind.TRUCK -> 1.2f + random.nextFloat() * 0.9f
            else -> 3.8f + random.nextFloat() * 1.5f
        } * d
        val lane = Lane(r, LaneType.ROAD, if (random.nextBoolean()) 1 else -1, speed, random.nextInt())
        val length = when (kind) {
            MoverKind.CAR -> 1.35f
            MoverKind.TRUCK -> 2.7f
            else -> 1.1f
        }
        var x = random.nextFloat() * 3f
        while (x + length + 1.5f < LOOP) {
            val (label, color) = vehicleStyle(kind)
            lane.movers += Mover(x - MARGIN, length, kind, label, color)
            x += length + (2.4f + random.nextFloat() * 4f) / (0.75f + d * 0.25f)
        }
        return lane
    }

    private fun waterLane(r: Int): Lane {
        lastWaterDir = -lastWaterDir
        val speed = (0.9f + random.nextFloat() * 0.9f) * kotlin.math.sqrt(difficulty(r))
        val lane = Lane(r, LaneType.WATER, lastWaterDir, speed, random.nextInt())
        var x = random.nextFloat() * 2f
        while (true) {
            val ram = random.nextFloat() < 0.45f
            val length = if (ram) 3f + random.nextInt(2) else 2f
            if (x + length + 1f > LOOP) break
            lane.movers += if (ram) {
                Mover(x - MARGIN, length, MoverKind.RAM, "RAM ${listOf(8, 16, 32, 64)[random.nextInt(4)]} Go", 0xFF2E7D4F)
            } else {
                Mover(x - MARGIN, length, MoverKind.FLOPPY, "SAVE", 0xFF26355A)
            }
            x += length + 1.2f + random.nextFloat() * (1.2f + difficulty(r) * 0.6f)
        }
        return lane
    }

    private fun railLane(r: Int): Lane {
        val lane = Lane(r, LaneType.RAIL, if (random.nextBoolean()) 1 else -1, 0f, random.nextInt())
        lane.trainTimer = 1f + random.nextFloat() * 5f
        return lane
    }

    private fun vehicleStyle(kind: MoverKind): Pair<String, Long> = when (kind) {
        MoverKind.CAR -> CAR_LABELS[random.nextInt(CAR_LABELS.size)] to CAR_COLORS[random.nextInt(CAR_COLORS.size)]
        MoverKind.TRUCK -> TRUCK_LABELS[random.nextInt(TRUCK_LABELS.size)] to 0xFF3B6FD8
        else -> "UDP" to 0xFFFF8A3D
    }

    companion object {
        val RANKS = listOf(
            0 to "Stagiaire",
            15 to "Alternant",
            30 to "Dev junior",
            50 to "Dev confirmé",
            75 to "Dev senior",
            105 to "Lead dev",
            140 to "Architecte",
            180 to "CTO",
            230 to "Légende 10x",
        )

        fun rankFor(score: Int): String = RANKS.last { score >= it.first }.second

        private val CAR_LABELS = listOf("HTTP", "404", "SYN", "ACK", "DNS", "PING", "200 OK", "SSH", "JSON", "CSS")
        private val CAR_COLORS = listOf(0xFFE84A5F, 0xFF2BB673, 0xFF9B5DE5, 0xFFF4B400, 0xFF00A8E8, 0xFFFF6F91)
        private val TRUCK_LABELS = listOf("TCP", "LOGS", "BACKUP", "node_modules", "CONTENEUR")

        private val TOASTS = listOf(
            "Réunion dans 5 minutes",
            "Nouveau ticket assigné",
            "La machine à café est vide",
            "Build vert",
            "Quelqu'un a touché à la prod",
            "It works on my machine",
            "Ton mot de passe expire demain",
            "Stand-up dans 2 minutes",
            "Conflit de merge détecté",
            "Le Wi-Fi est tombé",
            "Il reste 1 % de batterie",
            "Mise à jour du système : 34 %",
        )

        private val DEATH_CAR = listOf(
            "Segmentation fault (core dumped)",
            "Écrasé par un git push --force",
            "Paquet perdu, retransmission impossible",
            "NullPointerException: canard.vivant",
            "Tu as été garbage-collecté",
            "Collision de hash détectée",
        )
        private val DEATH_WATER = listOf(
            "Tombé dans le café… le clavier aussi",
            "Erreur 404 : canard not found",
            "Fuite mémoire : canard introuvable",
            "rm -rf canard/",
            "Le canard a coulé en prod",
        )
        private val DEATH_TRAIN = listOf(
            "Mise en prod du vendredi 17h. Bon week-end.",
            "Le pipeline CI/CD t'est passé dessus",
            "Déployé en prod sans tests",
            "Hotfix appliqué directement sur le canard",
        )
        private val DEATH_TIMEOUT = listOf(
            "408 Request Timeout",
            "Trop lent pour le sprint, ticket repoussé",
            "Ta session a expiré",
            "Le chef de projet t'a récupéré pour une réunion",
        )
        private val DEATH_OVERFLOW = listOf(
            "Buffer overflow : sorti du tableau",
            "IndexOutOfBoundsException",
            "Tu as quitté le cadre (et le projet)",
        )

        fun easeHop(t: Float): Float = 1f - (1f - t) * (1f - t)
    }
}
