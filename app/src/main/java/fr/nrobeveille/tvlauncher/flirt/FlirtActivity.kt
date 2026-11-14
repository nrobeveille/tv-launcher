package fr.nrobeveille.tvlauncher.flirt

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.nrobeveille.tvlauncher.R
import fr.nrobeveille.tvlauncher.ui.theme.LauncherTheme
import kotlin.math.sin
import kotlin.random.Random

/** Love.exe : jeu de drague parodique, tout public, pour l'anniversaire du birthday boy. */
class FlirtActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherTheme {
                FlirtScreen(onQuit = { finish() })
            }
        }
    }
}

private sealed interface Step {
    data object Intro : Step
    data class Meet(val i: Int) : Step
    data class Ask(val i: Int, val r: Int) : Step
    data class React(val i: Int, val r: Int, val c: Int) : Step
    data class Result(val i: Int) : Step
    data object Final : Step
}

private val Pink = Color(0xFFFF5D8F)
private val Gold = Color(0xFFFFC94D)
private val Soft = Color(0xCCFFFFFF)

@Composable
private fun FlirtScreen(onQuit: () -> Unit) {
    var step by remember { mutableStateOf<Step>(Step.Intro) }
    val heats = remember { mutableStateListOf(FlirtStory.START_HEAT, FlirtStory.START_HEAT, FlirtStory.START_HEAT) }
    var time by remember { mutableFloatStateOf(0f) }
    // Effet en cours : +1 cœurs, -1 nuage de pluie, avec son heure de départ
    var effect by remember { mutableStateOf(0) }
    var effectStart by remember { mutableFloatStateOf(-10f) }

    LaunchedEffect(Unit) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1e9f }
    }

    fun play(kind: Int) {
        effect = kind
        effectStart = time
    }

    val encounterIndex = when (val s = step) {
        is Step.Meet -> s.i
        is Step.Ask -> s.i
        is Step.React -> s.i
        is Step.Result -> s.i
        else -> -1
    }
    val encounter = FlirtStory.encounters.getOrNull(encounterIndex)
    val mood = when (val s = step) {
        is Step.React -> FlirtStory.encounters[s.i].rounds[s.r].choices[s.c].mood
        is Step.Result -> FlirtStory.outcome(heats[s.i], FlirtStory.encounters[s.i].name).mood
        else -> Mood.SMILE
    }

    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) { partyBackground(time) }

        // Le héros (portrait + chapeau) et le thermomètre
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 56.dp, top = 64.dp),
        ) {
            HeroPortrait()
            Spacer(Modifier.height(10.dp))
            NameTag("Le birthday boy", Gold)
            if (encounter != null) {
                Spacer(Modifier.height(22.dp))
                Thermometer(heats[encounterIndex], time)
            }
        }

        // Le personnage rencontré
        if (encounter != null) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 48.dp, top = 24.dp),
            ) {
                Canvas(Modifier.size(width = 360.dp, height = 420.dp)) { avatar(encounter.look, mood, time) }
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 6.dp),
                ) {
                    NameTag(encounter.name, Pink)
                    Text(encounter.role, fontSize = 13.sp, color = Soft, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }

        // Dialogues / cartes
        when (val s = step) {
            Step.Intro -> CenterCard {
                Text("Love.exe", fontSize = 54.sp, fontWeight = FontWeight.Black, color = Pink)
                Text("Mode drague : activé", fontSize = 18.sp, color = Gold, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(14.dp))
                Text(
                    "C'est la soirée d'anniversaire du birthday boy. Trois rencontres, une seule mission : " +
                        "faire monter la température… sans se prendre de râteau.",
                    fontSize = 18.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(22.dp))
                Buttons(step, listOf("Lancer Love.exe" to { step = Step.Meet(0) }))
            }
            is Step.Meet -> {
                val e = FlirtStory.encounters[s.i]
                BottomPanel {
                    Text("Rencontre ${s.i + 1}/3", fontSize = 14.sp, color = Gold, fontWeight = FontWeight.SemiBold)
                    Text("${e.name} s'approche du buffet…", fontSize = 22.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(14.dp))
                    Buttons(step, listOf("Aborder ${e.name}" to { step = Step.Ask(s.i, 0) }))
                }
            }
            is Step.Ask -> {
                val e = FlirtStory.encounters[s.i]
                val round = e.rounds[s.r]
                BottomPanel {
                    Speaker(e.name, round.question)
                    Spacer(Modifier.height(12.dp))
                    Buttons(
                        step,
                        round.choices.mapIndexed { c, choice ->
                            choice.text to {
                                heats[s.i] = (heats[s.i] + choice.heat).coerceIn(0, 100)
                                play(if (choice.heat >= 15) 1 else if (choice.heat < 0) -1 else 0)
                                step = Step.React(s.i, s.r, c)
                            }
                        },
                    )
                }
            }
            is Step.React -> {
                val e = FlirtStory.encounters[s.i]
                val choice = e.rounds[s.r].choices[s.c]
                BottomPanel {
                    Text("Toi : « ${choice.text} »", fontSize = 16.sp, color = Soft)
                    Spacer(Modifier.height(8.dp))
                    Speaker(e.name, choice.reaction)
                    Spacer(Modifier.height(12.dp))
                    val last = s.r == e.rounds.lastIndex
                    Buttons(step, listOf((if (last) "Verdict" else "Continuer") to {
                        if (last) {
                            if (heats[s.i] >= 85) play(1)
                            step = Step.Result(s.i)
                        } else {
                            step = Step.Ask(s.i, s.r + 1)
                        }
                    }))
                }
            }
            is Step.Result -> {
                val e = FlirtStory.encounters[s.i]
                val o = FlirtStory.outcome(heats[s.i], e.name)
                BottomPanel {
                    Text(o.title, fontSize = 30.sp, fontWeight = FontWeight.Black, color = if (o.good) Gold else Color(0xFF8FA3C0))
                    Text(o.text, fontSize = 19.sp, color = Color.White)
                    Spacer(Modifier.height(12.dp))
                    val last = s.i == FlirtStory.encounters.lastIndex
                    Buttons(step, listOf((if (last) "Bilan de la soirée" else "Rencontre suivante") to {
                        step = if (last) Step.Final else Step.Meet(s.i + 1)
                        if (last && heats.sum() >= 170) play(1)
                    }))
                }
            }
            Step.Final -> CenterCard {
                Text("Bilan de la soirée", fontSize = 18.sp, color = Gold, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                FlirtStory.encounters.forEachIndexed { i, e ->
                    val o = FlirtStory.outcome(heats[i], e.name)
                    Text("${e.name} · ${heats[i]}° · ${o.title}", fontSize = 17.sp, color = if (o.good) Color.White else Soft)
                }
                Spacer(Modifier.height(14.dp))
                Text(FlirtStory.finalTitle(heats.sum()), fontSize = 40.sp, fontWeight = FontWeight.Black, color = Pink)
                Text("Joyeux anniversaire au birthday boy !", fontSize = 20.sp, color = Gold, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(20.dp))
                Buttons(step, listOf(
                    "Rejouer" to {
                        for (i in heats.indices) heats[i] = FlirtStory.START_HEAT
                        step = Step.Meet(0)
                    },
                    "Quitter" to onQuit,
                ), horizontal = true)
            }
        }

        // Effets : pluie de cœurs ou petit nuage au-dessus du héros
        EffectsLayer(effect, time - effectStart)
    }
}

// --- Éléments -------------------------------------------------------------------------

@Composable
private fun HeroPortrait() {
    Box(Modifier.size(190.dp)) {
        Image(
            painter = painterResource(R.drawable.flirt_hero),
            contentDescription = "Le birthday boy",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .border(5.dp, Gold, CircleShape),
        )
        Canvas(Modifier.fillMaxSize()) {
            miniPartyHat(Offset(size.width * 0.58f, size.height * 0.1f), size.width * 0.34f, size.width * 0.42f)
        }
    }
}

@Composable
private fun NameTag(name: String, color: Color) {
    Text(
        name,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF1B0B2E),
        modifier = Modifier
            .background(color, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

@Composable
private fun Thermometer(heat: Int, time: Float) {
    val shown by animateFloatAsState(heat.toFloat(), tween(700), label = "heat")
    val label = when {
        heat >= 85 -> "Brûlant !"
        heat >= 55 -> "Ça chauffe !"
        heat >= 25 -> "Tiède"
        else -> "Glacial"
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Température · ${shown.toInt()}°", fontSize = 15.sp, color = Soft, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Canvas(Modifier.size(width = 240.dp, height = 46.dp)) {
            val barH = size.height * 0.42f
            val y = size.height * 0.62f - barH / 2
            val r = CornerRadius(barH / 2)
            drawRoundRect(Color(0x33FFFFFF), Offset(0f, y), Size(size.width * 0.86f, barH), r)
            val fill = size.width * 0.86f * (shown / 100f)
            if (fill > 0f) {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Color(0xFF4FC3F7), Color(0xFFFFB74D), Color(0xFFFF3D3D)), 0f, size.width * 0.86f),
                    Offset(0f, y), Size(fill, barH), r,
                )
            }
            if (shown >= 55f) flame(Offset(fill.coerceAtLeast(barH), y + barH * 0.4f), barH * 0.6f, barH * 1.6f, time)
        }
        Text(label, fontSize = 16.sp, color = if (heat >= 55) Color(0xFFFF8A65) else Color(0xFF8FC9FF), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Speaker(name: String, line: String) {
    Text(name, fontSize = 15.sp, color = Pink, fontWeight = FontWeight.Bold)
    Text(line, fontSize = 22.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun BottomPanel(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
        Column(
            modifier = Modifier
                .padding(horizontal = 40.dp, vertical = 26.dp)
                .fillMaxWidth()
                .background(Color(0xEB140A1F), RoundedCornerShape(22.dp))
                .border(2.dp, Pink.copy(alpha = 0.45f), RoundedCornerShape(22.dp))
                .padding(horizontal = 28.dp, vertical = 18.dp),
            content = content,
        )
    }
}

@Composable
private fun CenterCard(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(max = 620.dp)
                .background(Color(0xEB140A1F), RoundedCornerShape(26.dp))
                .border(2.dp, Pink.copy(alpha = 0.45f), RoundedCornerShape(26.dp))
                .padding(horizontal = 40.dp, vertical = 30.dp),
            content = content,
        )
    }
}

/** Boutons de choix ; le premier prend le focus à chaque nouvelle étape. */
@Composable
private fun Buttons(step: Step, actions: List<Pair<String, () -> Unit>>, horizontal: Boolean = false) {
    val first = remember(step) { FocusRequester() }
    LaunchedEffect(step) {
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }
    @Composable
    fun button(i: Int, label: String, onClick: () -> Unit, modifier: Modifier) {
        val shape = RoundedCornerShape(14.dp)
        Surface(
            onClick = onClick,
            modifier = if (i == 0) modifier.focusRequester(first) else modifier,
            shape = ClickableSurfaceDefaults.shape(shape = shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.White.copy(alpha = 0.07f),
                contentColor = Color.White,
                focusedContainerColor = Pink,
                focusedContentColor = Color(0xFF1B0B2E),
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)), shape = shape),
            ),
        ) {
            Text(label, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
        }
    }
    if (horizontal) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            actions.forEachIndexed { i, (label, onClick) -> button(i, label, onClick, Modifier) }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.forEachIndexed { i, (label, onClick) -> button(i, label, onClick, Modifier.fillMaxWidth()) }
        }
    }
}

/** Cœurs qui volent du personnage vers le héros, ou nuage de pluie sur le héros. */
@Composable
private fun EffectsLayer(kind: Int, age: Float) {
    if (kind == 0 || age > 1.8f) return
    val density = LocalDensity.current
    val seeds = remember { List(16) { Random(it).nextFloat() } }
    Canvas(Modifier.fillMaxSize()) {
        val hero = with(density) { Offset(151.dp.toPx(), 159.dp.toPx()) }
        if (kind > 0) {
            val from = Offset(size.width - with(density) { 228.dp.toPx() }, with(density) { 175.dp.toPx() })
            for (k in 0 until 16) {
                val delay = seeds[k] * 0.4f
                val p = ((age - delay) / 1.1f).coerceIn(0f, 1f)
                if (p <= 0f || p >= 1f) continue
                val spread = (seeds[(k + 5) % 16] - 0.5f) * size.height * 0.35f
                val x = from.x + (hero.x - from.x) * p
                val y = from.y + (hero.y - from.y) * p - sin(p * Math.PI.toFloat()) * (size.height * 0.18f) + spread * sin(p * Math.PI.toFloat())
                heart(Offset(x, y), size.height * (0.03f + seeds[k] * 0.025f), Pink.copy(alpha = 1f - p * 0.6f))
            }
        } else {
            val alpha = (1f - ((age - 1.3f) / 0.5f).coerceIn(0f, 1f))
            val c = hero + Offset(0f, -with(density) { 125.dp.toPx() })
            val r = size.height * 0.035f
            val grey = Color(0xFF7D8796).copy(alpha = alpha)
            drawCircle(grey, r, c + Offset(-r, 0f))
            drawCircle(grey, r * 1.3f, c + Offset(r * 0.2f, -r * 0.5f))
            drawCircle(grey, r, c + Offset(r * 1.4f, 0f))
            drawRoundRect(grey, c + Offset(-r * 1.8f, 0f), Size(r * 4f, r), CornerRadius(r / 2))
            for (k in 0 until 7) {
                val phase = (age * 1.8f + seeds[k]) % 1f
                val dx = (k - 3) * r * 0.55f
                val y0 = c.y + r + phase * r * 3f
                drawLine(Color(0xFF7FB2FF).copy(alpha = alpha * (1f - phase)), Offset(c.x + dx, y0), Offset(c.x + dx - r * 0.1f, y0 + r * 0.4f), 4f, StrokeCap.Round)
            }
        }
    }
}
