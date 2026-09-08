package com.pimorazelvanto.dayscounter.config

import androidx.compose.material3.ExperimentalMaterial3Api
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
class FutureOnlySelectableDatesTest {
    private val today = LocalDate.of(2026, 12, 31)
    private val selectable = FutureOnlySelectableDates(today)

    @Test
    fun `tomorrow is selectable`() {
        assertTrue(selectable.isSelectableDate(today.plusDays(1).toUtcStartOfDayMillis()))
    }

    @Test
    fun `today and yesterday are not selectable`() {
        assertFalse(selectable.isSelectableDate(today.toUtcStartOfDayMillis()))
        assertFalse(selectable.isSelectableDate(today.minusDays(1).toUtcStartOfDayMillis()))
    }

    @Test
    fun `current and later years are selectable, earlier not`() {
        assertTrue(selectable.isSelectableYear(2026))
        assertTrue(selectable.isSelectableYear(2027))
        assertFalse(selectable.isSelectableYear(2025))
    }
}
