package com.jevfast.control

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/** A launchable app installed on the device. */
data class InstalledApp(val label: String, val packageName: String)

/**
 * Direct app launching — replaces the go_home → hunt-the-icon dance that
 * cost several steps per run and died whenever the icon wasn't visible.
 */
object AppLauncher {

    private const val PLAY_STORE_PKG = "com.android.vending"

    /** Every launchable app on the device, sorted by label. */
    fun installedApps(ctx: Context): List<InstalledApp> {
        val pm = ctx.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return resolved.mapNotNull { ri ->
            val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
            InstalledApp(ri.loadLabel(pm)?.toString() ?: pkg, pkg)
        }.distinctBy { it.packageName }.sortedBy { it.label.lowercase() }
    }

    /** Bring an app's launcher activity to the foreground. */
    fun launch(ctx: Context, packageName: String): Boolean {
        val intent = ctx.packageManager.getLaunchIntentForPackage(packageName)
            ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    /** Open the Play Store — used when the goal needs an app that isn't installed. */
    fun launchPlayStore(ctx: Context): Boolean {
        if (launch(ctx, PLAY_STORE_PKG)) return true
        return try {
            ctx.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (_: Exception) {
            false
        }
    }
}
