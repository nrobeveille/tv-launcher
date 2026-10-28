package fr.nrobeveille.tvlauncher.ui.settings

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import fr.nrobeveille.tvlauncher.ui.components.EasterEggOverlay
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import fr.nrobeveille.tvlauncher.LauncherViewModel
import fr.nrobeveille.tvlauncher.Screen
import fr.nrobeveille.tvlauncher.UpdateState
import fr.nrobeveille.tvlauncher.data.LauncherSettings
import fr.nrobeveille.tvlauncher.ui.components.ActionMenu
import fr.nrobeveille.tvlauncher.ui.components.FocusRegistry
import fr.nrobeveille.tvlauncher.ui.components.MenuAction
import fr.nrobeveille.tvlauncher.ui.components.awaitLayout
import fr.nrobeveille.tvlauncher.ui.theme.SubtleText

/** Une entrée de la page Paramètres. */
private class Entry(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val value: String? = null,
    val chevron: Boolean = false,
    val highlighted: Boolean = false,
    val onClick: () -> Unit,
)

@Composable
fun SettingsScreen(vm: LauncherViewModel, settings: LauncherSettings) {
    val context = LocalContext.current
    val favorites by vm.favoriteApps.collectAsStateWithLifecycle()
    val registry = remember { FocusRegistry() }
    val listState = rememberLazyListState()
    var confirmReset by remember { mutableStateOf(false) }
    var versionTaps by remember { mutableIntStateOf(0) }
    var lastVersionTap by remember { mutableLongStateOf(0L) }
    var showEasterEgg by remember { mutableStateOf(false) }
    val version = remember { vm.appRepository.versionName() }

    val update by vm.updateState.collectAsStateWithLifecycle()

    fun system(action: String, label: String) =
        vm.appRepository.openSystemScreen(context, action, label)

    val installed = "version installée : $version"
    val updateEntry = when (val u = update) {
        UpdateState.Idle -> Entry(
            "update", "Rechercher une mise à jour", subtitle = installed.replaceFirstChar { it.uppercase() },
            onClick = { vm.checkForUpdate() },
        )
        UpdateState.Checking -> Entry(
            "update", "Recherche d'une mise à jour…", subtitle = installed.replaceFirstChar { it.uppercase() },
            onClick = {},
        )
        UpdateState.UpToDate -> Entry(
            "update", "Rechercher une mise à jour", subtitle = "Tu as la dernière version ($version)",
            onClick = { vm.checkForUpdate() },
        )
        is UpdateState.Available -> Entry(
            "update", "Installer la version ${u.info.versionName}",
            subtitle = "Nouvelle version disponible · $installed",
            highlighted = true,
            onClick = { vm.installUpdate(context) },
        )
        is UpdateState.Downloading -> Entry(
            "update", "Téléchargement… ${(u.progress * 100).toInt()} %",
            subtitle = "Version ${u.info.versionName}",
            highlighted = true,
            onClick = {},
        )
        UpdateState.Installing -> Entry(
            "update", "Installation…", subtitle = "Le launcher va redémarrer tout seul",
            highlighted = true,
            onClick = {},
        )
        is UpdateState.Error -> Entry(
            "update",
            if (u.info != null) "Réessayer l'installation" else "Rechercher une mise à jour",
            subtitle = u.message,
            onClick = { if (u.info != null) vm.installUpdate(context) else vm.checkForUpdate() },
        )
    }

    val sections: List<Pair<String, List<Entry>>> = listOf(
        "Mise à jour" to listOf(updateEntry),
        "Accueil" to listOf(
            Entry(
                key = "home_apps",
                title = "Applis de l'accueil",
                subtitle = "Choisir les applis affichées sur l'écran d'accueil",
                value = "${favorites?.size ?: 0}",
                chevron = true,
                onClick = { vm.navigate(Screen.HomeApps) },
            ),
            Entry(
                key = "reorder",
                title = "Réorganiser",
                subtitle = "Changer l'ordre des applis de l'accueil",
                chevron = true,
                onClick = { vm.navigate(Screen.Reorder) },
            ),
        ),
        "Apparence" to listOf(
            Entry("background", "Fond d'écran", value = settings.background.label, onClick = { vm.cycleBackground() }),
            Entry("tile_size", "Taille des tuiles", value = settings.tileSize.label, onClick = { vm.cycleTileSize() }),
            Entry("labels", "Nom des applis", value = settings.labelMode.label, onClick = { vm.cycleLabelMode() }),
            Entry(
                "clock",
                "Format de l'heure",
                value = if (settings.use24h) "24 h" else "12 h",
                onClick = { vm.toggleClockFormat() },
            ),
        ),
        "Système" to listOf(
            Entry(
                "tv_settings",
                "Paramètres de la TV",
                subtitle = "Réseau, son, image, comptes…",
                chevron = true,
                onClick = { system(Settings.ACTION_SETTINGS, "les paramètres") },
            ),
            Entry(
                "wifi",
                "Wi-Fi",
                chevron = true,
                onClick = { system(Settings.ACTION_WIFI_SETTINGS, "les réglages Wi-Fi") },
            ),
            Entry(
                "dev",
                "Options pour les développeurs",
                chevron = true,
                onClick = {
                    system(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, "les options développeur")
                },
            ),
        ),
        "Launcher" to listOf(
            Entry(
                "reset",
                "Réinitialiser l'accueil",
                subtitle = "Remet les applis et l'apparence par défaut",
                onClick = { confirmReset = true },
            ),
            Entry("about", "Version", value = version, onClick = {
                // 5 appuis d'affilée (moins de 1,5 s entre deux) : surprise.
                val now = SystemClock.uptimeMillis()
                versionTaps = if (now - lastVersionTap < 1_500L) versionTaps + 1 else 1
                lastVersionTap = now
                if (versionTaps >= 5) {
                    versionTaps = 0
                    showEasterEgg = true
                }
            }),
        ),
    )
    val firstKey = sections.first().second.first().key

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .padding(horizontal = 56.dp)
                .padding(top = 36.dp),
        ) {
            Text("Paramètres", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "OK pour changer une valeur · Retour pour revenir à l'accueil",
                fontSize = 15.sp,
                color = SubtleText,
            )
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp, start = 8.dp, end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                sections.forEach { (title, entries) -> section(title, entries, registry, vm) }
            }
        }
    }

    if (showEasterEgg) {
        EasterEggOverlay(onDone = { showEasterEgg = false })
    }

    if (confirmReset) {
        ActionMenu(
            title = "Réinitialiser l'accueil ?",
            subtitle = "Les applis de l'accueil et l'apparence reviendront aux réglages par défaut.",
            actions = listOf(
                MenuAction("Annuler") {},
                MenuAction("Réinitialiser") { vm.resetAll() },
            ),
            onDismiss = { confirmReset = false },
        )
    }

    // À l'ouverture, on vérifie les mises à jour si ce n'est pas déjà fait.
    LaunchedEffect(Unit) {
        if (vm.updateState.value == UpdateState.Idle) vm.checkForUpdate()
    }

    LaunchedEffect(Unit) {
        val wanted = vm.settingsFocusKey ?: firstKey
        // Index dans la liste (en-têtes de section compris) pour faire défiler jusqu'à l'entrée.
        val keysInOrder = sections.flatMap { (title, entries) ->
            listOf("header_$title") + entries.map { it.key }
        }
        val index = keysInOrder.indexOf(wanted)
        if (index > 0) listState.scrollToItem((index - 1).coerceAtLeast(0))
        awaitLayout()
        if (!registry.request(wanted)) registry.request(firstKey)
    }
}

private fun LazyListScope.section(
    title: String,
    entries: List<Entry>,
    registry: FocusRegistry,
    vm: LauncherViewModel,
) {
    item(key = "header_$title") { SectionHeader(title) }
    entries.forEach { entry ->
        item(key = entry.key) {
            SettingsRow(
                title = entry.title,
                subtitle = entry.subtitle,
                value = entry.value,
                chevron = entry.chevron,
                highlighted = entry.highlighted,
                onClick = entry.onClick,
                modifier = Modifier
                    .focusRequester(registry.requester(entry.key))
                    .onFocusChanged { if (it.isFocused) vm.settingsFocusKey = entry.key },
            )
        }
    }
}
