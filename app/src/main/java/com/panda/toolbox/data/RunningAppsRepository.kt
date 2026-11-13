package com.panda.toolbox.data

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.panda.toolbox.model.RunningAppItem
import com.panda.toolbox.model.TerminateResult
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

    fun getRunningApps(
        includeSystemApps: Boolean,
        terminatedTimestamps: Map<String, Long> = emptyMap()
    ): List<RunningAppItem> {
        val processMap = loadRunningProcesses()

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
            if (!includeSystemApps && isSystem) continue

            val procInfo = processMap[pkg]
            val isNotStopped = (appInfo.flags and ApplicationInfo.FLAG_STOPPED) == 0

            if (terminatedTimestamps.containsKey(pkg) && procInfo == null) {
                continue
            }

            // Include if it has a live process reported by ActivityManager, or is not force-stopped.
            if (procInfo == null && !isNotStopped) continue

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
                    isSystemApp = isSystem,
                    pid = procInfo?.pid,
                    importanceLabel = procInfo?.importanceLabel,
                    memoryMb = procInfo?.memoryMb,
                    versionName = versionName,
                    icon = icon
                )
            )
        }

        return items.sortedWith(
            compareByDescending<RunningAppItem> { it.pid != null }
                .thenBy { it.appName.lowercase(Locale.ROOT) }
        )
    }

    fun terminateApps(packageNames: List<String>): TerminateResult {
        var terminated = 0
        val failed = ArrayList<String>()

        for (pkg in packageNames) {
            if (pkg == context.packageName) {
                failed.add(pkg)
                continue
            }
            try {
                activityManager.killBackgroundProcesses(pkg)
                terminated++
            } catch (_: Exception) {
                failed.add(pkg)
            }
        }

        return TerminateResult(
            terminatedCount = terminated,
            failedPackages = failed
        )
    }

    private data class ProcessDetails(
        val pid: Int,
        val importanceLabel: String,
        val memoryMb: Int?
    )

    private fun loadRunningProcesses(): Map<String, ProcessDetails> {
        val processes = try {
            activityManager.runningAppProcesses
        } catch (_: Exception) {
            null
        } ?: return emptyMap()

        val result = HashMap<String, ProcessDetails>()
        for (proc in processes) {
            val pkgList = proc.pkgList ?: continue
            val importanceLabel = importanceToLabel(proc.importance)
            val memoryMb = try {
                val memInfos = activityManager.getProcessMemoryInfo(intArrayOf(proc.pid))
                memInfos.firstOrNull()?.totalPss?.let { it / 1024 }
            } catch (_: Exception) {
                null
            }

            for (pkg in pkgList) {
                if (!result.containsKey(pkg)) {
                    result[pkg] = ProcessDetails(
                        pid = proc.pid,
                        importanceLabel = importanceLabel,
                        memoryMb = memoryMb
                    )
                }
            }
        }
        return result
    }

    private fun importanceToLabel(importance: Int): String {
        return when (importance) {
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "Foreground"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND_SERVICE -> "Foreground Service"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> "Visible"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "Service"
            ActivityManager.RunningAppProcessInfo.IMPORTANCE_CACHED -> "Cached"
            else -> "Background"
        }
    }
}
