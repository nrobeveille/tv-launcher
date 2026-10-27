package fr.nrobeveille.tvlauncher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.nrobeveille.tvlauncher.data.AppInfo
import fr.nrobeveille.tvlauncher.data.AppRepository
import fr.nrobeveille.tvlauncher.data.AppVisual
import fr.nrobeveille.tvlauncher.ui.theme.Accent

val LocalAppRepository = staticCompositionLocalOf<AppRepository> {
    error("AppRepository non fourni")
}

@Composable
fun rememberBanner(app: AppInfo): AppVisual? {
    val repo = LocalAppRepository.current
    val visual by produceState(initialValue = repo.cachedBanner(app), app.packageName) {
        value = repo.loadBanner(app)
    }
    return visual
}

/** Petite icône carrée d'appli, pour les listes. */
@Composable
fun AppIcon(app: AppInfo, size: Dp, modifier: Modifier = Modifier) {
    val repo = LocalAppRepository.current
    val visual by produceState(initialValue = repo.cachedIcon(app), app.packageName) {
        value = repo.loadIcon(app)
    }
    val v = visual
    if (v == null) {
        Box(
            modifier
                .size(size)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.1f))
        )
    } else {
        val bitmap = remember(v) { v.bitmap.asImageBitmap() }
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size),
        )
    }
}

/** Icône « grille » de la tuile Toutes les applis. */
@Composable
fun GridIcon(size: Dp, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val gap = this.size.width * 0.14f
        val cell = (this.size.width - gap * 2) / 3f
        for (row in 0 until 3) {
            for (col in 0 until 3) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(col * (cell + gap), row * (cell + gap)),
                    size = Size(cell, cell),
                    cornerRadius = CornerRadius(cell * 0.25f),
                )
            }
        }
    }
}

/** Pastille ✓ indiquant qu'une appli est sur l'accueil. */
@Composable
fun CheckBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(6.dp)
            .size(24.dp)
            .clip(CircleShape)
            .background(Accent),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            imageVector = Icons.Filled.Check,
            contentDescription = "Sur l'accueil",
            colorFilter = ColorFilter.tint(Color(0xFF0B1020)),
            modifier = Modifier.size(16.dp),
        )
    }
}
