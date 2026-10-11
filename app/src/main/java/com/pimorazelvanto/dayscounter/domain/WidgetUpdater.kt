package com.pimorazelvanto.dayscounter.domain

interface WidgetUpdater {
    suspend fun updateAll()

    /** Whether the user still has at least one widget placed. */
    suspend fun hasWidgets(): Boolean
}
