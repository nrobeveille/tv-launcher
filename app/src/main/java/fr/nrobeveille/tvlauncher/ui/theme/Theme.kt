package fr.nrobeveille.tvlauncher.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import fr.nrobeveille.tvlauncher.data.BackgroundStyle

val Accent = Color(0xFF7FB2FF)
val TileColor = Color(0xFF232A40)
val DialogColor = Color(0xFF161B2E)
val SubtleText = Color(0xB3FFFFFF)

@Composable
fun LauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            onPrimary = Color(0xFF0B1020),
            surface = DialogColor,
            onSurface = Color.White,
            background = Color(0xFF0B1020),
            onBackground = Color.White,
        )
    ) {
        CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
    }
}

/** Dégradé de fond correspondant au réglage choisi. */
fun BackgroundStyle.brush(): Brush {
    fun diagonal(colors: List<Color>) = Brush.linearGradient(
        colors = colors,
        start = Offset.Zero,
        end = Offset.Infinite,
    )
    return when (this) {
        BackgroundStyle.METEO, BackgroundStyle.NUIT -> diagonal(listOf(Color(0xFF0B1020), Color(0xFF1B2347)))
        BackgroundStyle.OCEAN -> diagonal(listOf(Color(0xFF02131F), Color(0xFF0A3D52)))
        BackgroundStyle.AURORE -> diagonal(listOf(Color(0xFF160C2B), Color(0xFF3A1D52), Color(0xFF0E3A45)))
        BackgroundStyle.FORET -> diagonal(listOf(Color(0xFF07140F), Color(0xFF173A2C)))
        BackgroundStyle.GRAPHITE -> diagonal(listOf(Color(0xFF111111), Color(0xFF2A2A2A)))
        BackgroundStyle.NOIR -> Brush.linearGradient(listOf(Color.Black, Color.Black))
    }
}
