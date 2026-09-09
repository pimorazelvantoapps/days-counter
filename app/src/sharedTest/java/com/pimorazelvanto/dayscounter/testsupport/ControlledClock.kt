package com.pimorazelvanto.dayscounter.testsupport

import com.pimorazelvanto.dayscounter.domain.Clock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId

class ControlledClock(
    private var currentDay: LocalDate,
    private val fixedZone: ZoneId = ZoneId.of("Europe/Berlin"),
) : Clock {
    private val dateChanges = MutableStateFlow(0)

    val dateChangedCount: Int get() = dateChanges.value

    override fun today(): LocalDate = currentDay

    override fun zone(): ZoneId = fixedZone

    override fun days(): Flow<LocalDate> = dateChanges.map { currentDay }.distinctUntilChanged()

    override fun dateChanged() {
        dateChanges.update { it + 1 }
    }

    /** Moves the day without announcing it, the way a real day boundary passes unnoticed. */
    fun moveTo(day: LocalDate) {
        currentDay = day
    }
}
