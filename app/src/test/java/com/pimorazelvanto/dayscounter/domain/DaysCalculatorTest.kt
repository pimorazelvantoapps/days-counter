package com.pimorazelvanto.dayscounter.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class DaysCalculatorTest {
    private val today = LocalDate.of(2026, 9, 8)

    @Test
    fun `target tomorrow counts as one full remaining day`() {
        assertEquals(DisplayValue.Remaining(1), DaysCalculator.calculate(today, today.plusDays(1)))
    }

    @Test
    fun `target today is reached`() {
        assertEquals(DisplayValue.Reached, DaysCalculator.calculate(today, today))
    }

    @Test
    fun `target yesterday counts as one elapsed day`() {
        assertEquals(DisplayValue.Elapsed(1), DaysCalculator.calculate(today, today.minusDays(1)))
    }

    @Test
    fun `counts across year boundary`() {
        val newYear = LocalDate.of(2027, 1, 1)
        assertEquals(DisplayValue.Remaining(115), DaysCalculator.calculate(today, newYear))
    }

    @Test
    fun `counts leap day when crossing february 29`() {
        val beforeLeapDay = LocalDate.of(2028, 2, 28)
        val afterLeapDay = LocalDate.of(2028, 3, 1)
        assertEquals(DisplayValue.Remaining(2), DaysCalculator.calculate(beforeLeapDay, afterLeapDay))
    }

    @Test
    fun `counts distances of several years remaining`() {
        assertEquals(DisplayValue.Remaining(3653), DaysCalculator.calculate(today, today.plusYears(10)))
    }

    @Test
    fun `counts distances of several years elapsed`() {
        assertEquals(DisplayValue.Elapsed(3652), DaysCalculator.calculate(today, today.minusYears(10)))
    }
}
