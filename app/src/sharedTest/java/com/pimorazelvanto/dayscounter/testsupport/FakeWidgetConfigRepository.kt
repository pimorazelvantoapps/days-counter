package com.pimorazelvanto.dayscounter.testsupport

import com.pimorazelvanto.dayscounter.data.WidgetConfigRepository
import com.pimorazelvanto.dayscounter.domain.WidgetConfig
import java.io.IOException

class FakeWidgetConfigRepository : WidgetConfigRepository {
    val saved = mutableMapOf<Int, WidgetConfig>()
    var failOnSave = false

    override suspend fun load(appWidgetId: Int): WidgetConfig? = saved[appWidgetId]

    override suspend fun save(
        appWidgetId: Int,
        config: WidgetConfig,
    ) {
        if (failOnSave) throw IOException("simulated write failure")
        saved[appWidgetId] = config
    }

    override suspend fun delete(appWidgetId: Int) {
        saved.remove(appWidgetId)
    }
}
