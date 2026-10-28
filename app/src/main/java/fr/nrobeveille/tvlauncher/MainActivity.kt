package fr.nrobeveille.tvlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.nrobeveille.tvlauncher.data.BackgroundStyle
import fr.nrobeveille.tvlauncher.ui.allapps.AllAppsScreen
import fr.nrobeveille.tvlauncher.ui.components.LocalAppRepository
import fr.nrobeveille.tvlauncher.ui.home.HomeScreen
import fr.nrobeveille.tvlauncher.ui.settings.HomeAppsScreen
import fr.nrobeveille.tvlauncher.ui.settings.ReorderScreen
import fr.nrobeveille.tvlauncher.ui.settings.SettingsScreen
import fr.nrobeveille.tvlauncher.ui.theme.LauncherTheme
import fr.nrobeveille.tvlauncher.ui.theme.brush

class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()

    /** Rafraîchit la liste quand une appli est installée, mise à jour ou supprimée. */
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            vm.onPackageChanged(intent.data?.schemeSpecificPart)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        setContent {
            LauncherTheme {
                LauncherRoot(vm)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Appui sur Home alors que le launcher est déjà ouvert : retour au début de l'accueil.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) vm.goHome()
    }

    override fun onResume() {
        super.onResume()
        vm.refreshApps()
        vm.refreshWeather()
        vm.checkForUpdate(silent = true) // au plus une fois toutes les 6 h
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }
}

@Composable
private fun LauncherRoot(vm: LauncherViewModel) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val backStack by vm.backStack.collectAsStateWithLifecycle()
    val background = settings?.background ?: BackgroundStyle.NUIT
    val brush = remember(background) { background.brush() }

    CompositionLocalProvider(LocalAppRepository provides vm.appRepository) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush),
        ) {
            // Le bouton Retour ne ferme jamais le launcher.
            BackHandler { vm.back() }

            val s = settings ?: return@Box
            when (backStack.last()) {
                Screen.Home -> HomeScreen(
                    vm = vm,
                    settings = s,
                    onOpenAllApps = { vm.navigate(Screen.AllApps) },
                    onOpenSettings = {
                        vm.settingsFocusKey = null
                        vm.navigate(Screen.Settings)
                    },
                )
                Screen.AllApps -> AllAppsScreen(vm, s)
                Screen.Settings -> SettingsScreen(vm, s)
                Screen.HomeApps -> HomeAppsScreen(vm)
                Screen.Reorder -> ReorderScreen(vm)
            }
        }
    }
}
