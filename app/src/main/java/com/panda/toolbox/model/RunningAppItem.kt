package com.panda.toolbox.model

import android.graphics.drawable.Drawable

data class RunningAppItem(
    val packageName: String,
    val appName: String,
    val isSystemApp: Boolean,
    val pid: Int? = null,
    val importanceLabel: String? = null,
    val memoryMb: Int? = null,
    val versionName: String? = null,
    val icon: Drawable? = null
)

data class TerminateResult(
    val terminatedCount: Int,
    val failedPackages: List<String>
)
