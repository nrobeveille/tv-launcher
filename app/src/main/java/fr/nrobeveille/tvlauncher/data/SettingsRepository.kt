package fr.nrobeveille.tvlauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "launcher_settings")

/** Persistance des réglages du launcher (DataStore). */
class SettingsRepository(context: Context) {

    private val store = context.applicationContext.dataStore

    private object Keys {
        val FAVORITES = stringPreferencesKey("favorites")
        val INITIALIZED = booleanPreferencesKey("initialized")
        val BACKGROUND = stringPreferencesKey("background")
        val TILE_SIZE = stringPreferencesKey("tile_size")
        val LABEL_MODE = stringPreferencesKey("label_mode")
        val USE_24H = booleanPreferencesKey("use_24h")
    }

    val settings: Flow<LauncherSettings> = store.data.map { p ->
        val defaults = LauncherSettings()
        LauncherSettings(
            favorites = p[Keys.FAVORITES]?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList(),
            initialized = p[Keys.INITIALIZED] ?: false,
            background = enumOrDefault(p[Keys.BACKGROUND], defaults.background),
            tileSize = enumOrDefault(p[Keys.TILE_SIZE], defaults.tileSize),
            labelMode = enumOrDefault(p[Keys.LABEL_MODE], defaults.labelMode),
            use24h = p[Keys.USE_24H] ?: defaults.use24h,
        )
    }

    suspend fun setFavorites(packages: List<String>) {
        store.edit {
            it[Keys.FAVORITES] = packages.distinct().joinToString(SEPARATOR)
            it[Keys.INITIALIZED] = true
        }
    }

    suspend fun setBackground(value: BackgroundStyle) = store.edit { it[Keys.BACKGROUND] = value.name }
    suspend fun setTileSize(value: TileSize) = store.edit { it[Keys.TILE_SIZE] = value.name }
    suspend fun setLabelMode(value: LabelMode) = store.edit { it[Keys.LABEL_MODE] = value.name }
    suspend fun setUse24h(value: Boolean) = store.edit { it[Keys.USE_24H] = value }

    /** Remet tout à zéro : l'accueil sera prérempli au prochain chargement. */
    suspend fun reset() = store.edit { it.clear() }

    private inline fun <reified T : Enum<T>> enumOrDefault(name: String?, default: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

    private companion object {
        const val SEPARATOR = "\n"
    }
}
