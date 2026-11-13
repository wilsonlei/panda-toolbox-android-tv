package com.panda.toolbox.model

import android.graphics.drawable.Drawable

data class RunningAppItem(
    val packageName: String,
    val appName: String,
    val isStopped: Boolean,
    val versionName: String? = null,
    val icon: Drawable? = null
)
