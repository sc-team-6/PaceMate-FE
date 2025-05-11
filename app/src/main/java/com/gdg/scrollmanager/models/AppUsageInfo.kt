package com.gdg.scrollmanager.models

data class AppUsageInfo(
    val packageName: String,
    val appName: String,
    val timeInForeground: Long,
    val color: Int
)