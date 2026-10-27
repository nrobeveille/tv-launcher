package fr.nrobeveille.tvlauncher.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.util.LruCache
import android.widget.Toast
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale

/** Image d'une appli : sa bannière TV si elle en a une, sinon son icône. */
class AppVisual(val bitmap: Bitmap, val isBanner: Boolean)

/**
 * Liste les applis installées, charge leurs bannières/icônes (avec cache mémoire)
 * et les lance.
 */
class AppRepository(context: Context) {

    private val appContext = context.applicationContext
    private val pm: PackageManager = appContext.packageManager

    /** Cache limité à ~16 Mo de bitmaps. */
    private val cache = object : LruCache<String, AppVisual>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: AppVisual): Int = value.bitmap.allocationByteCount
    }

    suspend fun loadApps(): List<AppInfo> = withContext(Dispatchers.IO) {
        val result = LinkedHashMap<String, AppInfo>()

        fun collect(category: String, isTv: Boolean) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            @Suppress("DEPRECATION")
            val activities = pm.queryIntentActivities(intent, 0)
            for (ri in activities) {
                val info = ri.activityInfo ?: continue
                val pkg = info.packageName
                if (pkg == appContext.packageName || result.containsKey(pkg)) continue
                val label = ri.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg }
                result[pkg] = AppInfo(
                    packageName = pkg,
                    label = label,
                    component = android.content.ComponentName(pkg, info.name),
                    isTvApp = isTv,
                )
            }
        }

        // Les applis TV d'abord, puis les applis "téléphone" sans version TV.
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER, isTv = true)
        collect(Intent.CATEGORY_LAUNCHER, isTv = false)

        val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
        result.values.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    fun cachedBanner(app: AppInfo): AppVisual? = cache.get("b:" + app.packageName)
    fun cachedIcon(app: AppInfo): AppVisual? = cache.get("i:" + app.packageName)

    /** Bannière 16:9 de l'appli, ou son icône si elle n'en a pas. */
    suspend fun loadBanner(app: AppInfo): AppVisual? = withContext(Dispatchers.IO) {
        val key = "b:" + app.packageName
        cache.get(key)?.let { return@withContext it }
        val info = runCatching { pm.getActivityInfo(app.component, 0) }.getOrNull()
            ?: return@withContext null
        val banner = runCatching { info.loadBanner(pm) }.getOrNull()
        val visual = if (banner != null) {
            AppVisual(banner.toScaledBitmap(maxWidth = 480), isBanner = true)
        } else {
            val icon = runCatching { info.loadIcon(pm) }.getOrNull() ?: return@withContext null
            AppVisual(icon.toBitmap(192, 192), isBanner = false)
        }
        cache.put(key, visual)
        visual
    }

    /** Icône carrée de l'appli (pour les listes des paramètres). */
    suspend fun loadIcon(app: AppInfo): AppVisual? = withContext(Dispatchers.IO) {
        val key = "i:" + app.packageName
        cache.get(key)?.let { return@withContext it }
        val info = runCatching { pm.getActivityInfo(app.component, 0) }.getOrNull()
            ?: return@withContext null
        val icon = runCatching { info.loadIcon(pm) }.getOrNull() ?: return@withContext null
        val visual = AppVisual(icon.toBitmap(96, 96), isBanner = false)
        cache.put(key, visual)
        visual
    }

    /** Oublie les images d'une appli (mise à jour, désinstallation). */
    fun invalidate(packageName: String?) {
        if (packageName == null) {
            cache.evictAll()
        } else {
            cache.remove("b:$packageName")
            cache.remove("i:$packageName")
        }
    }

    fun launch(context: Context, app: AppInfo) {
        val intent = pm.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: pm.getLaunchIntentForPackage(app.packageName)
        if (intent == null) {
            Toast.makeText(context, "Impossible d'ouvrir ${app.label}", Toast.LENGTH_SHORT).show()
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        startSafely(context, intent, app.label)
    }

    fun openAppDetails(context: Context, packageName: String) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startSafely(context, intent, "les infos de l'appli")
    }

    fun openSystemScreen(context: Context, action: String, label: String) {
        startSafely(context, Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), label)
    }

    fun versionName(): String = runCatching {
        pm.getPackageInfo(appContext.packageName, 0).versionName
    }.getOrNull() ?: "?"

    private fun startSafely(context: Context, intent: Intent, label: String) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Impossible d'ouvrir $label", Toast.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            Toast.makeText(context, "Impossible d'ouvrir $label", Toast.LENGTH_SHORT).show()
        }
    }

    private fun Drawable.toScaledBitmap(maxWidth: Int): Bitmap {
        val w = intrinsicWidth.takeIf { it > 0 } ?: 320
        val h = intrinsicHeight.takeIf { it > 0 } ?: 180
        return if (w > maxWidth) {
            toBitmap(maxWidth, (h * maxWidth.toFloat() / w).toInt().coerceAtLeast(1))
        } else {
            toBitmap(w, h)
        }
    }
}
