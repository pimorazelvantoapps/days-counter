package com.pimorazelvanto.dayscounter.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DateConversionsTest {
    @Test
    fun `local date round trips through utc millis`() {
        val date = LocalDate.of(2027, 3, 15)

        assertEquals(date, utcMillisToLocalDate(date.toUtcStartOfDayMillis()))
    }

    @Test
    fun `epoch day zero maps to 1970-01-01`() {
        assertEquals(LocalDate.of(1970, 1, 1), utcMillisToLocalDate(0L))
        assertEquals(0L, LocalDate.of(1970, 1, 1).toUtcStartOfDayMillis())
    }
}
