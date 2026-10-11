package com.pimorazelvanto.dayscounter.testsupport

import com.pimorazelvanto.dayscounter.domain.WidgetUpdater
import java.io.IOException

class FakeWidgetUpdater : WidgetUpdater {
    var updateCount = 0
    var widgetCount = 1
    var failOnUpdate = false

    override suspend fun updateAll() {
        updateCount++
        if (failOnUpdate) throw IOException("update failed")
    }

    override suspend fun hasWidgets(): Boolean = widgetCount > 0
}
