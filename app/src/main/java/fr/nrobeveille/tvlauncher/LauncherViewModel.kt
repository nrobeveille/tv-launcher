package fr.nrobeveille.tvlauncher

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.nrobeveille.tvlauncher.data.AppInfo
import fr.nrobeveille.tvlauncher.data.AppRepository
import fr.nrobeveille.tvlauncher.data.LauncherSettings
import fr.nrobeveille.tvlauncher.data.SettingsRepository
import fr.nrobeveille.tvlauncher.data.Weather
import fr.nrobeveille.tvlauncher.data.WeatherRepository
import fr.nrobeveille.tvlauncher.update.UpdateEvents
import fr.nrobeveille.tvlauncher.update.UpdateException
import fr.nrobeveille.tvlauncher.update.UpdateInfo
import fr.nrobeveille.tvlauncher.update.Updater
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** État de la mise à jour, affiché dans les paramètres. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data object Installing : UpdateState
    data class Error(val message: String, val info: UpdateInfo? = null) : UpdateState
}

/** Écrans du launcher (navigation simple par pile, sans bibliothèque). */
sealed interface Screen {
    data object Home : Screen
    data object AllApps : Screen
    data object Settings : Screen
    data object HomeApps : Screen
    data object Reorder : Screen
}

class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    val appRepository = AppRepository(application)
    private val settingsRepository = SettingsRepository(application)

    /** Toutes les applis lançables (null tant que pas chargées). */
    private val _apps = MutableStateFlow<List<AppInfo>?>(null)
    val apps: StateFlow<List<AppInfo>?> = _apps.asStateFlow()

    /**
     * Ordre des applis de l'accueil. Gardé ici en mémoire (source de vérité immédiate)
     * puis enregistré en arrière-plan, pour que les déplacements rapides ne se perdent pas.
     */
    private val _favorites = MutableStateFlow<List<String>?>(null)
    val favorites: StateFlow<List<String>?> = _favorites.asStateFlow()

    /** Réglages d'apparence. */
    val settings: StateFlow<LauncherSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Applis de l'accueil prêtes à afficher (null tant que le chargement n'est pas fini). */
    val favoriteApps: StateFlow<List<AppInfo>?> = combine(_apps, _favorites) { apps, favs ->
        if (apps == null || favs == null) {
            null
        } else {
            val byPackage = apps.associateBy { it.packageName }
            // Une appli désinstallée disparaît de l'accueil (mais reste mémorisée
            // si elle est réinstallée, par exemple pendant une mise à jour).
            favs.mapNotNull { byPackage[it] }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // --- Navigation ---------------------------------------------------------

    private val _backStack = MutableStateFlow<List<Screen>>(listOf(Screen.Home))
    val backStack: StateFlow<List<Screen>> = _backStack.asStateFlow()

    /** Dernier élément sélectionné sur chaque écran, pour y revenir au retour. */
    var homeFocusKey: String? = null
    var settingsFocusKey: String? = null

    /** Incrémenté quand on appuie sur Home : l'accueil remet le focus au début. */
    private val _homeFocusSignal = MutableStateFlow(0)
    val homeFocusSignal: StateFlow<Int> = _homeFocusSignal.asStateFlow()

    fun navigate(screen: Screen) {
        _backStack.value = _backStack.value + screen
    }

    /** Retour arrière. Sur l'accueil, ne fait rien (un launcher ne se ferme pas). */
    fun back() {
        val stack = _backStack.value
        if (stack.size > 1) _backStack.value = stack.dropLast(1)
    }

    fun goHome() {
        _backStack.value = listOf(Screen.Home)
        homeFocusKey = null
        _homeFocusSignal.value++
    }

    // --- Chargement ---------------------------------------------------------

    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            val stored = settingsRepository.settings.first()
            val apps = appRepository.loadApps()
            _apps.value = apps
            if (stored.initialized) {
                _favorites.value = stored.favorites
            } else {
                val defaults = defaultFavorites(apps)
                _favorites.value = defaults
                settingsRepository.setFavorites(defaults)
            }
        }
    }

    fun refreshApps() {
        if (_apps.value == null) return // premier chargement en cours
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch { _apps.value = appRepository.loadApps() }
    }

    fun onPackageChanged(packageName: String?) {
        appRepository.invalidate(packageName)
        refreshApps()
    }

    // --- Applis de l'accueil ------------------------------------------------

    private var persistJob: Job? = null

    private fun updateFavorites(newList: List<String>) {
        val list = newList.distinct()
        _favorites.value = list
        persistJob?.cancel()
        persistJob = viewModelScope.launch { settingsRepository.setFavorites(list) }
    }

    fun isFavorite(packageName: String): Boolean = _favorites.value?.contains(packageName) == true

    fun addFavorite(packageName: String) {
        val current = _favorites.value ?: return
        if (packageName !in current) updateFavorites(current + packageName)
    }

    fun removeFavorite(packageName: String) {
        val current = _favorites.value ?: return
        updateFavorites(current - packageName)
    }

    fun toggleFavorite(packageName: String) {
        if (isFavorite(packageName)) removeFavorite(packageName) else addFavorite(packageName)
    }

    /** Déplace une appli de l'accueil d'une position (delta = -1 ou +1). */
    fun moveFavorite(packageName: String, delta: Int) {
        val stored = _favorites.value ?: return
        val visible = favoriteApps.value?.map { it.packageName }?.toMutableList() ?: return
        val from = visible.indexOf(packageName)
        val to = from + delta
        if (from < 0 || to !in visible.indices) return
        visible.add(to, visible.removeAt(from))
        // Les applis masquées (désinstallées) restent mémorisées, à la fin.
        updateFavorites(visible + stored.filterNot { it in visible })
    }

    // --- Apparence ----------------------------------------------------------

    fun cycleBackground() = settings.value?.let { s ->
        viewModelScope.launch { settingsRepository.setBackground(s.background.next()) }
    }

    fun cycleTileSize() = settings.value?.let { s ->
        viewModelScope.launch { settingsRepository.setTileSize(s.tileSize.next()) }
    }

    fun cycleLabelMode() = settings.value?.let { s ->
        viewModelScope.launch { settingsRepository.setLabelMode(s.labelMode.next()) }
    }

    fun toggleClockFormat() = settings.value?.let { s ->
        viewModelScope.launch { settingsRepository.setUse24h(!s.use24h) }
    }

    fun resetAll() {
        viewModelScope.launch {
            settingsRepository.reset()
            val apps = _apps.value ?: appRepository.loadApps()
            val defaults = defaultFavorites(apps)
            _favorites.value = defaults
            settingsRepository.setFavorites(defaults)
        }
    }

    // --- Météo --------------------------------------------------------------

    private val weatherRepository = WeatherRepository()

    private val _weather = MutableStateFlow<Weather?>(null)
    val weather: StateFlow<Weather?> = _weather.asStateFlow()

    private var weatherJob: Job? = null
    private var lastWeatherFetch = 0L

    /** Rafraîchit la météo, au plus toutes les 30 min tant que la dernière requête a réussi. */
    fun refreshWeather() {
        if (weatherJob?.isActive == true) return
        if (System.currentTimeMillis() - lastWeatherFetch < 30 * 60_000L) return
        weatherJob = viewModelScope.launch {
            val result = runCatching { weatherRepository.fetch() }.getOrNull() ?: return@launch
            _weather.value = result
            lastWeatherFetch = System.currentTimeMillis()
        }
    }

    // --- Mise à jour --------------------------------------------------------

    val updater = Updater(application)

    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState: StateFlow<UpdateState> = _updateState.asStateFlow()

    private var updateJob: Job? = null
    private var lastUpdateCheck = 0L
    private var lastInstallInfo: UpdateInfo? = null

    init {
        // Échec d'installation remonté par InstallResultReceiver.
        viewModelScope.launch {
            UpdateEvents.installError.collect { message ->
                if (message != null) {
                    _updateState.value = UpdateState.Error(message, lastInstallInfo)
                }
            }
        }
        // Vérification discrète au démarrage (pastille sur la roue dentée).
        checkForUpdate(silent = true)
    }

    /**
     * Cherche une nouvelle version. En mode silencieux (au démarrage), une erreur
     * réseau n'est pas affichée et on ne revérifie pas plus d'une fois toutes les 6 h.
     */
    fun checkForUpdate(silent: Boolean = false) {
        if (updateJob?.isActive == true) return
        val now = System.currentTimeMillis()
        if (silent && now - lastUpdateCheck < 6 * 60 * 60 * 1000L) return
        lastUpdateCheck = now
        updateJob = viewModelScope.launch {
            if (!silent) _updateState.value = UpdateState.Checking
            _updateState.value = try {
                val info = updater.findUpdate()
                if (info != null) UpdateState.Available(info) else UpdateState.UpToDate
            } catch (e: UpdateException) {
                if (silent) UpdateState.Idle else UpdateState.Error(e.message.orEmpty())
            } catch (e: Exception) {
                if (silent) UpdateState.Idle else UpdateState.Error("Vérification impossible")
            }
        }
    }

    /** Télécharge et installe la version disponible. */
    fun installUpdate(context: Context) {
        val info = when (val state = _updateState.value) {
            is UpdateState.Available -> state.info
            is UpdateState.Error -> state.info ?: return checkForUpdate()
            else -> return
        }
        if (!updater.canInstall()) {
            val opened = updater.openInstallPermissionScreen(context)
            _updateState.value = UpdateState.Error(
                if (opened) {
                    "Autorise « Launcher TV » à installer des applis, puis relance la mise à jour"
                } else {
                    "Autorise l'installation avec adb : adb shell appops set " +
                        "${context.packageName} REQUEST_INSTALL_PACKAGES allow"
                },
                info,
            )
            return
        }
        if (updateJob?.isActive == true) return
        UpdateEvents.clear()
        lastInstallInfo = info
        updateJob = viewModelScope.launch {
            try {
                _updateState.value = UpdateState.Downloading(info, 0f)
                val apk = updater.download(info) { progress ->
                    _updateState.value = UpdateState.Downloading(info, progress)
                }
                _updateState.value = UpdateState.Installing
                updater.install(apk)
                // La suite se passe dans InstallResultReceiver : en cas de succès,
                // Android remplace l'appli et la relance.
            } catch (e: UpdateException) {
                _updateState.value = UpdateState.Error(e.message.orEmpty(), info)
            } catch (e: Exception) {
                _updateState.value = UpdateState.Error("Échec du téléchargement", info)
            }
        }
    }

    // --- Actions ------------------------------------------------------------

    fun launch(context: Context, app: AppInfo) = appRepository.launch(context, app)

    fun openAppDetails(context: Context, packageName: String) =
        appRepository.openAppDetails(context, packageName)

    private fun defaultFavorites(apps: List<AppInfo>): List<String> {
        val installed = apps.map { it.packageName }.toSet()
        val picks = PREFERRED_DEFAULTS.filter { it in installed }
        if (picks.size >= 3) return picks
        val extra = apps.filter { it.isTvApp && it.packageName !in picks }.map { it.packageName }
        return (picks + extra).take(6)
    }

    private companion object {
        /** Applis mises sur l'accueil au premier lancement, si elles sont installées. */
        val PREFERRED_DEFAULTS = listOf(
            "com.netflix.ninja",                  // Netflix
            "com.google.android.youtube.tv",      // YouTube
            "com.amazon.amazonvideo.livingroom",  // Prime Video
            "com.disney.disneyplus",              // Disney+
            "com.canal.android.canal",            // CANAL+
            "com.google.android.youtube.tvmusic", // YouTube Music
            "com.android.vending",                // Play Store
        )
    }
}

private inline fun <reified T : Enum<T>> T.next(): T {
    val values = enumValues<T>()
    return values[(ordinal + 1) % values.size]
}
