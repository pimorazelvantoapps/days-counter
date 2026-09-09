package com.pimorazelvanto.dayscounter.testsupport

import com.pimorazelvanto.dayscounter.data.WidgetConfigRepository
import com.pimorazelvanto.dayscounter.domain.WidgetConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.io.IOException

class FakeWidgetConfigRepository : WidgetConfigRepository {
    /** Writing directly seeds a configuration without notifying observers, unlike [save]. */
    val saved = mutableMapOf<Int, WidgetConfig>()
    var failOnSave = false

    /** Makes the read stream fail, the way DataStore reports a file it cannot read. */
    var failOnRead = false

    /** The widget id a composition asked to observe, so that a test can configure that widget. */
    var observedAppWidgetId: Int? = null
        private set

    private val changes = MutableStateFlow(0)

    override fun observe(appWidgetId: Int): Flow<WidgetConfig?> {
        observedAppWidgetId = appWidgetId
        if (failOnRead) return flow { throw IOException("simulated read failure") }
        return changes.map { saved[appWidgetId] }.distinctUntilChanged()
    }

    override suspend fun save(
        appWidgetId: Int,
        config: WidgetConfig,
    ) {
        if (failOnSave) throw IOException("simulated write failure")
        saved[appWidgetId] = config
        changes.update { it + 1 }
    }

    override suspend fun delete(appWidgetId: Int) {
        saved.remove(appWidgetId)
        changes.update { it + 1 }
    }
}
