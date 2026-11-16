package net.bluebeetle.launcher

import android.app.ActivityOptions
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.widget.Toast
import java.util.concurrent.Executors

/** One launchable app. The icon is pre-rendered to a bitmap so it draws fast and can be shared. */
data class AppEntry(val label: String, val component: ComponentName, val icon: Bitmap) {
    val key: String = component.flattenToString()
}

/**
 * The app list, loaded once per process and kept in memory. It only reloads when Android
 * reports that an app was installed, removed or changed, so coming back to the home screen
 * costs nothing.
 */
object Apps {

    private const val ICON_DP = 72

    private val main = Handler(Looper.getMainLooper())
    private val exec = Executors.newSingleThreadExecutor()
    private var stale = true
    private var loading = false
    private var watching = false

    /** Current list, sorted by name. Until [complete] it may hold just the pinned apps. */
    var all: List<AppEntry> = emptyList()
        private set

    /** True once the full list has loaded at least once. */
    var complete = false
        private set

    /** Bumps every time [all] is replaced. */
    var generation = 0
        private set

    /** Called on the main thread whenever [all] changes. */
    var onChange: (() -> Unit)? = null

    /** Main thread. Starts a background load if the list is missing or out of date. */
    fun ensureLoaded(context: Context) {
        val ctx = context.applicationContext
        watch(ctx)
        if (!stale || loading) return
        stale = false
        loading = true
        exec.execute {
            // Pinned apps first, so the home row fills in almost immediately on a cold start.
            if (!complete) {
                val pinned = Prefs(ctx).favorites.mapNotNull { entryFor(ctx, it) }
                if (pinned.isNotEmpty()) main.post { if (!complete) publish(pinned) }
            }
            val loaded = try { load(ctx) } catch (e: Exception) { null }
            main.post {
                loading = false
                if (loaded != null) {
                    complete = true
                    publish(loaded)
                } else {
                    stale = true
                }
                if (stale) ensureLoaded(ctx)
            }
        }
    }

    private fun publish(list: List<AppEntry>) {
        all = list
        generation++
        onChange?.invoke()
    }

    private fun watch(ctx: Context) {
        if (watching) return
        watching = true
        val refresh = {
            stale = true
            ensureLoaded(ctx)
        }
        try {
            ctx.getSystemService(LauncherApps::class.java).registerCallback(object : LauncherApps.Callback() {
                override fun onPackageRemoved(packageName: String?, user: UserHandle?) = refresh()
                override fun onPackageAdded(packageName: String?, user: UserHandle?) = refresh()
                override fun onPackageChanged(packageName: String?, user: UserHandle?) = refresh()
                override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
                override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) = refresh()
            }, main)
        } catch (e: Exception) {
            watching = false
        }
    }

    private fun load(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val size = iconSize(ctx)
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map {
                AppEntry(
                    it.loadLabel(pm).toString(),
                    ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                    render(it.loadIcon(pm), size),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    private fun entryFor(ctx: Context, key: String): AppEntry? = try {
        val pm = ctx.packageManager
        val component = ComponentName.unflattenFromString(key)!!
        val info = pm.getActivityInfo(component, 0)
        AppEntry(info.loadLabel(pm).toString(), component, render(info.loadIcon(pm), iconSize(ctx)))
    } catch (e: Exception) {
        null
    }

    private fun iconSize(ctx: Context) = (ICON_DP * ctx.resources.displayMetrics.density).toInt()

    private fun render(icon: Drawable, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        icon.setBounds(0, 0, size, size)
        icon.draw(Canvas(bmp))
        return bmp
    }

    /** Skips the system's app-opening animation, which only smears on e-ink and costs time. */
    private fun instant(ctx: Context): Bundle = ActivityOptions.makeCustomAnimation(ctx, 0, 0).toBundle()

    fun launch(ctx: Context, component: ComponentName) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            ctx.startActivity(intent, instant(ctx))
        } catch (e: Exception) {
            Toast.makeText(ctx, "Couldn't open that app", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchPackage(ctx: Context, pkg: String): Boolean {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), instant(ctx))
            true
        } catch (e: Exception) {
            false
        }
    }

    fun appInfo(ctx: Context, pkg: String) {
        try {
            ctx.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            Toast.makeText(ctx, "Couldn't open app info", Toast.LENGTH_SHORT).show()
        }
    }
}
