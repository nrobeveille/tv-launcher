package fr.nrobeveille.tvlauncher.data

import androidx.compose.runtime.Immutable

enum class BackgroundStyle(val label: String) {
    METEO("Suit la météo"),
    NUIT("Nuit"),
    OCEAN("Océan"),
    AURORE("Aurore"),
    FORET("Forêt"),
    GRAPHITE("Graphite"),
    NOIR("Noir"),
}

enum class TileSize(val label: String, val widthDp: Int) {
    PETITE("Petite", 168),
    MOYENNE("Moyenne", 208),
    GRANDE("Grande", 256),
}

enum class LabelMode(val label: String) {
    TOUJOURS("Toujours"),
    FOCUS("Appli sélectionnée seulement"),
    JAMAIS("Jamais"),
}

@Immutable
data class LauncherSettings(
    /** Paquets affichés sur l'accueil, dans l'ordre. */
    val favorites: List<String> = emptyList(),
    /** false tant que l'accueil n'a jamais été prérempli. */
    val initialized: Boolean = false,
    val background: BackgroundStyle = BackgroundStyle.METEO,
    val tileSize: TileSize = TileSize.MOYENNE,
    val labelMode: LabelMode = LabelMode.TOUJOURS,
    val use24h: Boolean = true,
)
