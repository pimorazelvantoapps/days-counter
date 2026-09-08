package com.pimorazelvanto.dayscounter.data

import com.pimorazelvanto.dayscounter.domain.WidgetConfig

interface WidgetConfigRepository {
    suspend fun load(appWidgetId: Int): WidgetConfig?

    suspend fun save(
        appWidgetId: Int,
        config: WidgetConfig,
    )

    suspend fun delete(appWidgetId: Int)
}
