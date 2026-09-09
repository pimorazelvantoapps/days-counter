package com.pimorazelvanto.dayscounter.data

import com.pimorazelvanto.dayscounter.domain.WidgetConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

interface WidgetConfigRepository {
    /** Emits the configuration stored for [appWidgetId], null while there is none, on every change. */
    fun observe(appWidgetId: Int): Flow<WidgetConfig?>

    suspend fun load(appWidgetId: Int): WidgetConfig? = observe(appWidgetId).first()

    suspend fun save(
        appWidgetId: Int,
        config: WidgetConfig,
    )

    suspend fun delete(appWidgetId: Int)
}
