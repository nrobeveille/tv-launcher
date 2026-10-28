package fr.nrobeveille.tvlauncher.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import fr.nrobeveille.tvlauncher.LauncherViewModel
import fr.nrobeveille.tvlauncher.UpdateState
import fr.nrobeveille.tvlauncher.data.Weather
import fr.nrobeveille.tvlauncher.data.WeatherRepository
import fr.nrobeveille.tvlauncher.ui.components.WeatherIcon
import androidx.compose.foundation.layout.width
import fr.nrobeveille.tvlauncher.ui.theme.Accent
import fr.nrobeveille.tvlauncher.data.AppInfo
import fr.nrobeveille.tvlauncher.data.LauncherSettings
import fr.nrobeveille.tvlauncher.ui.components.ActionMenu
import fr.nrobeveille.tvlauncher.ui.components.AllAppsTile
import fr.nrobeveille.tvlauncher.ui.components.AppTile
import fr.nrobeveille.tvlauncher.ui.components.FocusRegistry
import fr.nrobeveille.tvlauncher.ui.components.MenuAction
import fr.nrobeveille.tvlauncher.ui.components.awaitLayout
import fr.nrobeveille.tvlauncher.ui.components.isSelectKey
import fr.nrobeveille.tvlauncher.ui.theme.SubtleText
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private const val KEY_SETTINGS = "__settings__"
private const val KEY_ALL_APPS = "__all_apps__"

@Composable
fun HomeScreen(
    vm: LauncherViewModel,
    settings: LauncherSettings,
    onOpenAllApps: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val favorites by vm.favoriteApps.collectAsStateWithLifecycle()
    val focusSignal by vm.homeFocusSignal.collectAsStateWithLifecycle()
    val updateState by vm.updateState.collectAsStateWithLifecycle()
    val weather by vm.weather.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val registry = remember { FocusRegistry() }
    val listState = rememberLazyListState()
    var menuFor by remember { mutableStateOf<AppInfo?>(null) }
    var movingPackage by remember { mutableStateOf<String?>(null) }
    var pendingFocus by remember { mutableStateOf<String?>(null) }

    val apps = favorites ?: return
    val tileWidth = settings.tileSize.widthDp.dp
    val rowKeys = apps.map { it.packageName } + KEY_ALL_APPS

    // Retour pendant un déplacement : on valide la position.
    BackHandler(enabled = movingPackage != null) { movingPackage = null }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 56.dp, vertical = 36.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Clock(use24h = settings.use24h, weather = weather)
            Box {
                SettingsButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .focusRequester(registry.requester(KEY_SETTINGS))
                        .onFocusChanged { if (it.isFocused) vm.homeFocusKey = KEY_SETTINGS },
                )
                // Pastille : une mise à jour est disponible.
                if (updateState is UpdateState.Available) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .size(14.dp)
                            .background(Accent, CircleShape)
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))

        Text(
            text = if (movingPackage != null) {
                "◀ ▶ pour déplacer · OK pour valider"
            } else {
                "Mes applis"
            },
            fontSize = 20.sp,
            fontWeight = FontWeight.Medium,
            color = SubtleText,
            modifier = Modifier.padding(start = 4.dp),
        )

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .onPreviewKeyEvent { event ->
                    val moving = movingPackage ?: return@onPreviewKeyEvent false
                    when {
                        event.key == Key.DirectionLeft -> {
                            if (event.type == KeyEventType.KeyDown) vm.moveFavorite(moving, -1)
                            true
                        }
                        event.key == Key.DirectionRight -> {
                            if (event.type == KeyEventType.KeyDown) vm.moveFavorite(moving, +1)
                            true
                        }
                        event.key == Key.DirectionUp || event.key == Key.DirectionDown -> true
                        event.isSelectKey() -> {
                            if (event.type == KeyEventType.KeyUp) movingPackage = null
                            true
                        }
                        else -> false
                    }
                },
        ) {
            items(apps, key = { it.packageName }) { app ->
                AppTile(
                    app = app,
                    width = tileWidth,
                    labelMode = settings.labelMode,
                    moving = movingPackage == app.packageName,
                    onClick = { vm.launch(context, app) },
                    onLongClick = { menuFor = app },
                    modifier = Modifier
                        .focusRequester(registry.requester(app.packageName))
                        .onFocusChanged { if (it.isFocused) vm.homeFocusKey = app.packageName },
                )
            }
            item(key = KEY_ALL_APPS) {
                AllAppsTile(
                    width = tileWidth,
                    labelMode = settings.labelMode,
                    onClick = onOpenAllApps,
                    modifier = Modifier
                        .focusRequester(registry.requester(KEY_ALL_APPS))
                        .onFocusChanged { if (it.isFocused) vm.homeFocusKey = KEY_ALL_APPS },
                )
            }
        }

        Spacer(Modifier.weight(1.3f))
    }

    menuFor?.let { app ->
        ActionMenu(
            title = app.label,
            actions = listOf(
                MenuAction("Déplacer") { movingPackage = app.packageName },
                MenuAction("Retirer de l'accueil") {
                    // Le focus passe à la tuile suivante.
                    val index = rowKeys.indexOf(app.packageName)
                    pendingFocus = rowKeys.getOrNull(index + 1) ?: KEY_ALL_APPS
                    vm.removeFavorite(app.packageName)
                },
                MenuAction("Infos de l'appli") { vm.openAppDetails(context, app.packageName) },
            ),
            onDismiss = { menuFor = null },
        )
    }

    // Focus à l'ouverture, au retour d'un autre écran, ou après un appui sur Home.
    LaunchedEffect(focusSignal) {
        val wanted = vm.homeFocusKey
        val target = when {
            wanted == KEY_SETTINGS -> KEY_SETTINGS
            wanted != null && wanted in rowKeys -> wanted
            else -> rowKeys.first()
        }
        val index = rowKeys.indexOf(target)
        if (index >= 0) {
            if (wanted == null) {
                listState.scrollToItem(0)
            } else if (listState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                listState.scrollToItem(index)
            }
        }
        awaitLayout()
        if (!registry.request(target)) registry.request(rowKeys.first())
    }

    // Météo : nouvelle tentative toutes les 5 min (le ViewModel limite à 30 min après un succès).
    LaunchedEffect(Unit) {
        while (true) {
            vm.refreshWeather()
            delay(5 * 60_000L)
        }
    }

    // Après le retrait d'une appli.
    LaunchedEffect(apps, pendingFocus) {
        val key = pendingFocus ?: return@LaunchedEffect
        awaitLayout()
        registry.request(key)
        pendingFocus = null
    }

    // Pendant un déplacement : garder la tuile visible et sélectionnée.
    LaunchedEffect(apps, movingPackage) {
        val moving = movingPackage ?: return@LaunchedEffect
        val index = rowKeys.indexOf(moving)
        if (index < 0) {
            movingPackage = null
            return@LaunchedEffect
        }
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == index }
        val fullyVisible = item != null &&
            item.offset >= 0 && item.offset + item.size <= layout.viewportEndOffset
        if (!fullyVisible) listState.animateScrollToItem((index - 1).coerceAtLeast(0))
        awaitLayout()
        registry.request(moving)
    }
}

@Composable
private fun Clock(use24h: Boolean, weather: Weather?) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
        }
    }
    val locale = Locale.getDefault()
    val time = now.format(DateTimeFormatter.ofPattern(if (use24h) "HH:mm" else "h:mm a", locale))
    val date = now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", locale))
        .replaceFirstChar { it.titlecase(locale) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = time, fontSize = 56.sp, fontWeight = FontWeight.Light)
            if (weather != null) {
                Spacer(Modifier.width(32.dp))
                WeatherIcon(
                    kind = weather.kind,
                    isDay = weather.isDay,
                    description = "${weather.description} à ${WeatherRepository.CITY}",
                    modifier = Modifier.size(46.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(text = "${weather.temperature}°", fontSize = 36.sp, fontWeight = FontWeight.Light)
            }
        }
        Text(
            text = if (weather != null) "$date · ${WeatherRepository.CITY}" else date,
            fontSize = 18.sp,
            color = SubtleText,
        )
    }
}

@Composable
private fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(52.dp),
        shape = ClickableSurfaceDefaults.shape(shape = CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.15f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.08f),
            contentColor = Color.White,
            focusedContainerColor = Color.White,
            focusedContentColor = Color(0xFF0B1020),
        ),
    ) {
        Image(
            imageVector = Icons.Filled.Settings,
            contentDescription = "Paramètres",
            colorFilter = ColorFilter.tint(LocalContentColor.current),
            modifier = Modifier
                .size(26.dp)
                .align(Alignment.Center),
        )
    }
}
