package fr.nrobeveille.tvlauncher.ui.allapps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import fr.nrobeveille.tvlauncher.LauncherViewModel
import fr.nrobeveille.tvlauncher.data.AppInfo
import fr.nrobeveille.tvlauncher.data.LauncherSettings
import fr.nrobeveille.tvlauncher.ui.components.ActionMenu
import fr.nrobeveille.tvlauncher.ui.components.AppTile
import fr.nrobeveille.tvlauncher.ui.components.FocusRegistry
import fr.nrobeveille.tvlauncher.ui.components.MenuAction
import fr.nrobeveille.tvlauncher.ui.components.awaitLayout
import fr.nrobeveille.tvlauncher.ui.theme.SubtleText

/** Grille de toutes les applis installées. */
@Composable
fun AllAppsScreen(vm: LauncherViewModel, settings: LauncherSettings) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val registry = remember { FocusRegistry() }
    val gridState = rememberLazyGridState()
    var menuFor by remember { mutableStateOf<AppInfo?>(null) }

    val list = apps ?: return
    val favoriteSet = favorites.orEmpty().toSet()
    val tileWidth = settings.tileSize.widthDp.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 56.dp, end = 56.dp, top = 36.dp),
    ) {
        Text("Toutes les applis", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
        Text(
            text = "${list.size} applis · appui long sur OK pour ajouter ou retirer de l'accueil",
            fontSize = 15.sp,
            color = SubtleText,
        )
        Spacer(Modifier.height(8.dp))

        LazyVerticalGrid(
            columns = GridCells.FixedSize(tileWidth),
            state = gridState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(list, key = { it.packageName }) { app ->
                AppTile(
                    app = app,
                    width = tileWidth,
                    labelMode = settings.labelMode,
                    showBadge = app.packageName in favoriteSet,
                    onClick = { vm.launch(context, app) },
                    onLongClick = { menuFor = app },
                    modifier = Modifier.focusRequester(registry.requester(app.packageName)),
                )
            }
        }
    }

    menuFor?.let { app ->
        val onHome = app.packageName in favoriteSet
        ActionMenu(
            title = app.label,
            actions = listOf(
                if (onHome) {
                    MenuAction("Retirer de l'accueil") { vm.removeFavorite(app.packageName) }
                } else {
                    MenuAction("Ajouter à l'accueil") { vm.addFavorite(app.packageName) }
                },
                MenuAction("Infos de l'appli") { vm.openAppDetails(context, app.packageName) },
            ),
            onDismiss = { menuFor = null },
        )
    }

    LaunchedEffect(Unit) {
        val first = list.firstOrNull() ?: return@LaunchedEffect
        awaitLayout()
        registry.request(first.packageName)
    }
}
