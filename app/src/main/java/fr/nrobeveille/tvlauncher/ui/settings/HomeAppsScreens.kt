package fr.nrobeveille.tvlauncher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import fr.nrobeveille.tvlauncher.LauncherViewModel
import fr.nrobeveille.tvlauncher.ui.components.AppIcon
import fr.nrobeveille.tvlauncher.ui.components.FocusRegistry
import fr.nrobeveille.tvlauncher.ui.components.awaitLayout
import fr.nrobeveille.tvlauncher.ui.components.isSelectKey
import fr.nrobeveille.tvlauncher.ui.theme.SubtleText

/** Choix des applis affichées sur l'accueil (cases à cocher). */
@Composable
fun HomeAppsScreen(vm: LauncherViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val registry = remember { FocusRegistry() }
    val listState = rememberLazyListState()

    val list = apps ?: return
    val selected = favorites.orEmpty().toSet()
    val shownCount = list.count { it.packageName in selected }

    SettingsPage(
        title = "Applis de l'accueil",
        subtitle = "$shownCount appli${if (shownCount > 1) "s" else ""} sur l'accueil · OK pour cocher ou décocher",
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(list, key = { it.packageName }) { app ->
                val checked = app.packageName in selected
                SettingsRow(
                    title = app.label,
                    subtitle = if (app.isTvApp) null else "Appli non optimisée pour la TV",
                    onClick = { vm.toggleFavorite(app.packageName) },
                    modifier = Modifier.focusRequester(registry.requester(app.packageName)),
                    leading = { AppIcon(app, size = 40.dp) },
                    trailing = { CheckMark(checked) },
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        val first = list.firstOrNull() ?: return@LaunchedEffect
        awaitLayout()
        registry.request(first.packageName)
    }
}

/**
 * Réorganisation de l'accueil : OK pour « prendre » une appli,
 * ▲ ▼ pour la déplacer, OK (ou Retour) pour la poser.
 */
@Composable
fun ReorderScreen(vm: LauncherViewModel) {
    val favorites by vm.favoriteApps.collectAsStateWithLifecycle()
    val registry = remember { FocusRegistry() }
    val listState = rememberLazyListState()
    var picked by remember { mutableStateOf<String?>(null) }

    val list = favorites ?: return

    BackHandler(enabled = picked != null) { picked = null }

    SettingsPage(
        title = "Réorganiser l'accueil",
        subtitle = if (picked != null) {
            "▲ ▼ pour déplacer · OK pour poser"
        } else {
            "OK pour prendre une appli, puis ▲ ▼ pour la déplacer"
        },
    ) {
        if (list.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Aucune appli sur l'accueil pour le moment.",
                    fontSize = 18.sp,
                    color = SubtleText,
                    textAlign = TextAlign.Center,
                )
            }
            return@SettingsPage
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 8.dp, bottom = 48.dp, start = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    val moving = picked ?: return@onPreviewKeyEvent false
                    when {
                        event.key == Key.DirectionUp -> {
                            if (event.type == KeyEventType.KeyDown) vm.moveFavorite(moving, -1)
                            true
                        }
                        event.key == Key.DirectionDown -> {
                            if (event.type == KeyEventType.KeyDown) vm.moveFavorite(moving, +1)
                            true
                        }
                        event.key == Key.DirectionLeft || event.key == Key.DirectionRight -> true
                        event.isSelectKey() -> {
                            if (event.type == KeyEventType.KeyUp) picked = null
                            true
                        }
                        else -> false
                    }
                },
        ) {
            itemsIndexed(list, key = { _, app -> app.packageName }) { index, app ->
                val isPicked = picked == app.packageName
                SettingsRow(
                    title = app.label,
                    value = if (isPicked) "▲ ▼" else null,
                    highlighted = isPicked,
                    onClick = { picked = app.packageName },
                    modifier = Modifier.focusRequester(registry.requester(app.packageName)),
                    leading = {
                        Text(
                            text = "${index + 1}",
                            fontSize = 16.sp,
                            textAlign = TextAlign.End,
                            modifier = Modifier.width(28.dp),
                        )
                        AppIcon(app, size = 40.dp)
                    },
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        val first = list.firstOrNull() ?: return@LaunchedEffect
        awaitLayout()
        registry.request(first.packageName)
    }

    // Garde l'appli déplacée visible et sélectionnée.
    LaunchedEffect(list, picked) {
        val moving = picked ?: return@LaunchedEffect
        val index = list.indexOfFirst { it.packageName == moving }
        if (index < 0) return@LaunchedEffect
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
        val fullyVisible = item != null &&
            item.offset >= 0 && item.offset + item.size <= layout.viewportEndOffset
        if (!fullyVisible) listState.animateScrollToItem((index - 2).coerceAtLeast(0))
        awaitLayout()
        registry.request(moving)
    }
}

/** Mise en page commune des sous-pages de réglages. */
@Composable
private fun SettingsPage(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 760.dp)
                .padding(horizontal = 56.dp)
                .padding(top = 36.dp),
        ) {
            Text(title, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, fontSize = 15.sp, color = SubtleText)
            content()
        }
    }
}
