package cc.opencar.assistant.feature.shortcuts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

data class LaunchableApp(
    val packageName: String,
    val label: String,
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "packageName" to packageName,
        "label" to label,
    )
}

class AppLauncher(private val context: Context) {
    fun listLaunchable(): List<LaunchableApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val resolve = if (android.os.Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0),
            )
        } else {
            pm.queryIntentActivities(intent, 0)
        }
        return resolve
            .mapNotNull { ri ->
                val pkg = ri.activityInfo?.packageName ?: return@mapNotNull null
                val label = try {
                    ri.loadLabel(pm)?.toString() ?: pkg
                } catch (_: Throwable) {
                    pkg
                }
                LaunchableApp(pkg, label)
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun launch(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: Intent(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    setPackage(packageName)
                }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) == null) {
                Log.w(TAG, "no launch activity for $packageName")
                return false
            }
            context.startActivity(intent)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "launch failed $packageName: ${t.message}")
            false
        }
    }

    companion object {
        private const val TAG = "AppLauncher"
    }
}
