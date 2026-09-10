package com.pimorazelvanto.dayscounter.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DigitSizeTierTest {
    @ParameterizedTest(name = "{0} days -> {1}")
    @CsvSource(
        "9, ONE_DIGIT",
        "10, TWO_DIGITS",
        "99, TWO_DIGITS",
        "100, THREE_DIGITS",
        "999, THREE_DIGITS",
        "1000, FOUR_DIGITS",
        "9999, FOUR_DIGITS",
        "10000, FIVE_OR_MORE_DIGITS",
    )
    fun `tier depends on digit count`(
        days: Int,
        expected: DigitSizeTier,
    ) {
        assertEquals(expected, DisplayValue.Remaining(days).sizeTier())
    }

    @Test
    fun `reached uses the one-digit tier`() {
        assertEquals(DigitSizeTier.ONE_DIGIT, DisplayValue.Reached.sizeTier())
    }
}
