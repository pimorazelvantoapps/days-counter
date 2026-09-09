package com.pimorazelvanto.dayscounter.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.ZoneId

class SystemClock : Clock {
    private val dateChanges = MutableStateFlow(0)

    override fun today(): LocalDate = LocalDate.now(zone())

    override fun zone(): ZoneId = ZoneId.systemDefault()

    override fun days(): Flow<LocalDate> = dateChanges.map { today() }.distinctUntilChanged()

    override fun dateChanged() {
        dateChanges.update { it + 1 }
    }
}
