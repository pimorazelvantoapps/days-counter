package com.pimorazelvanto.dayscounter.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId

/**
 * @param instantSource the wall clock to read, replaceable so that a test can move the day.
 *   Only its instant is used: the zone is re-read on every call, because the process outlives a
 *   device time-zone change and a zone captured at construction would keep [today] and the
 *   midnight alarm in the old zone.
 */
class SystemClock(
    private val instantSource: java.time.Clock = java.time.Clock.systemDefaultZone(),
) : Clock {
    private val dateChanges = MutableStateFlow(0)

    override fun today(): LocalDate = instantSource.instant().atZone(zone()).toLocalDate()

    override fun zone(): ZoneId = ZoneId.systemDefault()

    override fun days(): Flow<LocalDate> = dateChanges.map { today() }.distinctUntilChanged()

    override fun dateChanged() {
        dateChanges.update { it + 1 }
    }
}
