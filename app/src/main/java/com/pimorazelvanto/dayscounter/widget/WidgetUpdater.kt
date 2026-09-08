package com.pimorazelvanto.dayscounter.widget

fun interface WidgetUpdater {
    suspend fun updateAll()
}
