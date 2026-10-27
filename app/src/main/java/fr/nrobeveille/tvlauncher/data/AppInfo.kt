package fr.nrobeveille.tvlauncher.data

import android.content.ComponentName
import androidx.compose.runtime.Immutable

/** Une appli lançable installée sur l'appareil. */
@Immutable
data class AppInfo(
    val packageName: String,
    val label: String,
    /** Activité à utiliser pour charger la bannière / l'icône. */
    val component: ComponentName,
    /** true si l'appli a une version TV (catégorie LEANBACK_LAUNCHER). */
    val isTvApp: Boolean,
)
