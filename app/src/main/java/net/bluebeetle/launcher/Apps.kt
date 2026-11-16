package net.bluebeetle.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

data class AppEntry(val label: String, val component: ComponentName, val icon: Drawable) {
    val key: String = component.flattenToString()

    /** A separate drawable instance, so the same app can be shown in two places at once. */
    fun freshIcon(): Drawable = icon.constantState?.newDrawable()?.mutate() ?: icon
}

object Apps {

    /** Every launchable app except this launcher, sorted by name. Slow: call off the main thread. */
    fun load(ctx: Context): List<AppEntry> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map {
                AppEntry(
                    it.loadLabel(pm).toString(),
                    ComponentName(it.activityInfo.packageName, it.activityInfo.name),
                    it.loadIcon(pm),
                )
            }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(ctx: Context, component: ComponentName) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Couldn't open that app", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchPackage(ctx: Context, pkg: String): Boolean {
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        return try {
            ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
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
