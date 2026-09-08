package com.pimorazelvanto.dayscounter.domain

fun interface WidgetUpdater {
    suspend fun updateAll()
}
