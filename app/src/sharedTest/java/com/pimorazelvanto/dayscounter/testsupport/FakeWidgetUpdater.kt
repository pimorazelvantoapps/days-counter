package com.pimorazelvanto.dayscounter.testsupport

import com.pimorazelvanto.dayscounter.domain.WidgetUpdater

class FakeWidgetUpdater : WidgetUpdater {
    var updateCount = 0
    var widgetCount = 1

    override suspend fun updateAll() {
        updateCount++
    }

    override suspend fun hasWidgets(): Boolean = widgetCount > 0
}
