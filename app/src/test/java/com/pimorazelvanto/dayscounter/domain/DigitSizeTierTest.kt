package com.pimorazelvanto.dayscounter.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DigitSizeTierTest {
    @ParameterizedTest(name = "{0} days -> {1}")
    @CsvSource(
        "1, LARGE",
        "99, LARGE",
        "100, MEDIUM",
        "999, MEDIUM",
        "1000, SMALL",
        "36500, SMALL",
    )
    fun `tier depends on digit count`(
        days: Int,
        expected: DigitSizeTier,
    ) {
        assertEquals(expected, DisplayValue.Days(days).sizeTier())
    }

    @ParameterizedTest(name = "{0} uses LARGE")
    @CsvSource("Reached", "Passed")
    fun `reached and passed use large tier`(name: String) {
        val value = if (name == "Reached") DisplayValue.Reached else DisplayValue.Passed
        assertEquals(DigitSizeTier.LARGE, value.sizeTier())
    }
}
