package com.pimorazelvanto.dayscounter.domain

import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId

interface Clock {
    fun today(): LocalDate

    fun zone(): ZoneId

    /** Emits the current day, and again after each [dateChanged] that lands on another day. */
    fun days(): Flow<LocalDate>

    /** Reports that the system date may have moved, so that [days] reads it again. */
    fun dateChanged()
}
