package com.pimorazelvanto.dayscounter.testsupport

import com.pimorazelvanto.dayscounter.widget.WidgetUpdater

class FakeWidgetUpdater : WidgetUpdater {
    var updateCount = 0

    override suspend fun updateAll() {
        updateCount++
    }
}
