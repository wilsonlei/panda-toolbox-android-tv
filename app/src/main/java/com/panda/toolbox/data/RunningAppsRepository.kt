package com.panda.toolbox.data

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.panda.toolbox.model.RunningAppItem
import java.util.Locale

class RunningAppsRepository(private val context: Context) {

    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val packageManager = context.packageManager

    fun getMemorySummary(): String {
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val availMb = memInfo.availMem / (1024 * 1024)
        val totalMb = memInfo.totalMem / (1024 * 1024)
        return String.format(Locale.US, "RAM: %d MB free / %d MB", availMb, totalMb)
    }

    fun getApps(activeOnly: Boolean): List<RunningAppItem> {
        val installedApps = try {
            packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        } catch (_: Exception) {
            emptyList()
        }

        val items = ArrayList<RunningAppItem>()
        for (appInfo in installedApps) {
            val pkg = appInfo.packageName
            if (pkg == context.packageName) continue

            val isSystem = (appInfo.flags and
                (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
            if (isSystem) continue

            val isStopped = (appInfo.flags and ApplicationInfo.FLAG_STOPPED) != 0
            if (activeOnly && isStopped) continue

            val label = try {
                packageManager.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                pkg
            }

            val versionName = try {
                packageManager.getPackageInfo(pkg, 0).versionName
            } catch (_: Exception) {
                null
            }

            val icon = try {
                packageManager.getApplicationIcon(appInfo)
            } catch (_: Exception) {
                null
            }

            items.add(
                RunningAppItem(
                    packageName = pkg,
                    appName = label,
                    isStopped = isStopped,
                    versionName = versionName,
                    icon = icon
                )
            )
        }

        return items.sortedWith(
            compareBy<RunningAppItem> { it.isStopped }
                .thenBy { it.appName.lowercase(Locale.ROOT) }
        )
    }
}
