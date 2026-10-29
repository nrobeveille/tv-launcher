package fr.nrobeveille.tvlauncher.lights

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.nrobeveille.tvlauncher.ui.components.awaitLayout
import fr.nrobeveille.tvlauncher.ui.theme.Accent
import fr.nrobeveille.tvlauncher.ui.theme.LauncherTheme
import fr.nrobeveille.tvlauncher.ui.theme.SubtleText

/** Pilotage des ampoules WiZ depuis la TV. */
class LightsActivity : ComponentActivity() {

    private val vm: LightsViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LauncherTheme {
                LightsScreen(vm)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        vm.refreshAll()
    }
}

private val OffColor = Color(0xFF3A3F4B)
private val ChipShape = RoundedCornerShape(22.dp)

@Composable
private fun LightsScreen(vm: LightsViewModel) {
    val bulbs by vm.bulbs.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val partySync by vm.partySync.collectAsStateWithLifecycle()

    // null = « Toutes les ampoules »
    var selected by remember { mutableStateOf<String?>(null) }
    val selectedBulb = bulbs.firstOrNull { it.mac == selected }
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        awaitLayout()
        runCatching { firstFocus.requestFocus() }
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(36.dp),
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(Color(0xFF0B1020), Color(0xFF1B2347))))
            .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        // Colonne de gauche : les ampoules
        Column(Modifier.width(320.dp).fillMaxHeight()) {
            Text("Lumières", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Text(status ?: " ", fontSize = 14.sp, color = SubtleText, maxLines = 2)
            LazyColumn(
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f).fillMaxWidth(),
            ) {
                item(key = "all") {
                    val anyOn = bulbs.any { it.pilot?.on == true }
                    TargetRow(
                        title = "Toutes les ampoules",
                        subtitle = when {
                            bulbs.isEmpty() -> "Aucune ampoule pour l'instant"
                            anyOn -> "OK pour tout éteindre"
                            else -> "OK pour tout allumer"
                        },
                        dotColor = if (anyOn) Accent else OffColor,
                        isSelected = selected == null,
                        onFocused = { selected = null },
                        onClick = { vm.power(null, !anyOn) },
                        modifier = Modifier.focusRequester(firstFocus),
                    )
                }
                items(bulbs, key = { it.mac }) { bulb ->
                    TargetRow(
                        title = bulb.name,
                        subtitle = describe(bulb),
                        dotColor = bulbColor(bulb),
                        isSelected = selected == bulb.mac,
                        onFocused = { selected = bulb.mac },
                        onClick = { vm.power(bulb.mac, bulb.pilot?.on != true) },
                    )
                }
            }
            ActionChip(
                text = if (searching) "Recherche en cours…" else "Rechercher les ampoules",
                onClick = { vm.discover() },
            )
            Spacer(Modifier.height(8.dp))
            ActionChip(
                text = "Suivre le Mode soirée : ${if (partySync) "oui" else "non"}",
                highlighted = partySync,
                onClick = { vm.setPartySync(!partySync) },
            )
        }

        // Panneau de droite : les réglages de la sélection
        ControlPanel(vm, selectedBulb, Modifier.weight(1f).fillMaxHeight())
    }
}

@Composable
private fun ControlPanel(vm: LightsViewModel, bulb: Bulb?, modifier: Modifier = Modifier) {
    val mac = bulb?.mac
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(18.dp),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 6.dp),
        modifier = modifier,
    ) {
        item(key = "title") {
            Column {
                Text(
                    text = bulb?.name ?: "Toutes les ampoules",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when {
                        bulb == null -> "Les réglages s'appliquent à toutes les ampoules"
                        !bulb.reachable -> "${bulb.ip} · injoignable"
                        else -> bulb.ip
                    },
                    fontSize = 13.sp,
                    color = SubtleText,
                )
            }
        }
        item(key = "power") {
            ControlSection("Allumage") {
                ActionChip("Allumer", onClick = { vm.power(mac, true) })
                ActionChip("Éteindre", onClick = { vm.power(mac, false) })
                if (bulb != null) ActionChip("Faire clignoter", onClick = { vm.identify(bulb.mac) })
            }
        }
        item(key = "dimming") {
            ControlSection("Luminosité") {
                listOf(10, 25, 50, 75, 100).forEach { percent ->
                    ActionChip(
                        "$percent %",
                        highlighted = bulb?.pilot?.dimming == percent,
                        onClick = { vm.brightness(mac, percent) },
                    )
                }
            }
        }
        item(key = "whites") {
            ControlSection("Blancs") {
                ActionChip("Chaud", highlighted = bulb?.pilot?.temp == 2700, onClick = { vm.temperature(mac, 2700) })
                ActionChip("Neutre", highlighted = bulb?.pilot?.temp == 4000, onClick = { vm.temperature(mac, 4000) })
                ActionChip("Froid", highlighted = bulb?.pilot?.temp == 6500, onClick = { vm.temperature(mac, 6500) })
            }
        }
        item(key = "colors") {
            ControlSection("Couleurs") {
                WIZ_COLORS.forEach { c ->
                    ColorChip(Color(c.r, c.g, c.b), onClick = { vm.color(mac, c.r, c.g, c.b) })
                }
            }
        }
        item(key = "scenes") {
            ControlSection("Ambiances") {
                WIZ_SCENES.forEach { (id, name) ->
                    ActionChip(name, highlighted = bulb?.pilot?.sceneId == id, onClick = { vm.scene(mac, id) })
                }
            }
        }
        if (bulb != null) {
            item(key = "names") {
                ControlSection("Nom") {
                    WIZ_NAMES.forEach { name ->
                        ActionChip(name, highlighted = bulb.name == name, onClick = { vm.rename(bulb.mac, name) })
                    }
                    ActionChip("Oublier cette ampoule", onClick = { vm.forget(bulb.mac) })
                }
            }
        }
    }
}

@Composable
private fun ControlSection(title: String, content: @Composable RowScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = SubtleText)
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 4.dp, horizontal = 4.dp),
            content = content,
        )
    }
}

@Composable
private fun TargetRow(
    title: String,
    subtitle: String,
    dotColor: Color,
    isSelected: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .onFocusChanged { if (it.isFocused) onFocused() },
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = if (isSelected) 0.12f else 0.05f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF0B1020),
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (isSelected) Border(BorderStroke(2.dp, Accent), shape = shape) else Border.None,
        ),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
        ) {
            Box(
                Modifier
                    .size(16.dp)
                    .background(dotColor, CircleShape)
            )
            Column {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    subtitle,
                    fontSize = 13.sp,
                    color = LocalContentColor.current.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ActionChip(text: String, onClick: () -> Unit, highlighted: Boolean = false) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = ChipShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (highlighted) Accent.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF0B1020),
        ),
    ) {
        Text(text, fontSize = 15.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
    }
}

@Composable
private fun ColorChip(color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.2f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = color,
            focusedContainerColor = color,
            pressedContainerColor = color,
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, Color.White), shape = CircleShape),
        ),
    ) {}
}

private fun describe(bulb: Bulb): String {
    val pilot = bulb.pilot
    return when {
        !bulb.reachable -> "Injoignable"
        pilot == null -> "…"
        !pilot.on -> "Éteinte"
        else -> "Allumée" + (pilot.dimming?.let { " · $it %" } ?: "")
    }
}

private fun bulbColor(bulb: Bulb): Color {
    val pilot = bulb.pilot ?: return Color.Gray.copy(alpha = 0.4f)
    if (!bulb.reachable) return Color(0xFF7A2E2E)
    if (!pilot.on) return OffColor
    pilot.rgb?.let { (r, g, b) -> return Color(r, g, b) }
    pilot.temp?.let { t ->
        return when {
            t < 3_500 -> Color(0xFFFFC27A)
            t < 5_000 -> Color(0xFFFFF1D6)
            else -> Color(0xFFDDE8FF)
        }
    }
    return Accent
}
