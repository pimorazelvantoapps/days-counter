package com.pimorazelvanto.dayscounter.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class WidgetUiStateTest {
    private val today = LocalDate.of(2026, 9, 8)

    @Test
    fun `configured widget shows remaining days`() {
        val config = WidgetConfig("Urlaub", today.plusDays(42), HeaderColor.BLUE)

        val state = WidgetUiState.from(config, today, defaultTitle = "Tage")

        assertEquals(
            WidgetUiState("Urlaub", "42", DigitSizeTier.TWO_DIGITS, HeaderColor.BLUE, isPlaceholder = false),
            state,
        )
    }

    @Test
    fun `elapsed target shows its day count`() {
        val config = WidgetConfig("Umzug", today.minusDays(1), HeaderColor.BLUE)

        assertEquals("1", WidgetUiState.from(config, today, "Tage").valueText)
    }

    @Test
    fun `target day itself shows zero on the one-digit tier`() {
        val config = WidgetConfig("Umzug", today, HeaderColor.GREEN)

        val state = WidgetUiState.from(config, today, "Tage")

        assertEquals("0", state.valueText)
        assertEquals(DigitSizeTier.ONE_DIGIT, state.sizeTier)
    }

    @Test
    fun `three digit values use three-digit tier`() {
        val config = WidgetConfig("Abschluss", today.plusDays(365), HeaderColor.TEAL)

        val state = WidgetUiState.from(config, today, "Tage")

        assertEquals("365", state.valueText)
        assertEquals(DigitSizeTier.THREE_DIGITS, state.sizeTier)
    }

    @Test
    fun `four digit values use four-digit tier`() {
        val config = WidgetConfig("Rente", today.plusDays(4000), HeaderColor.GREY)

        assertEquals(DigitSizeTier.FOUR_DIGITS, WidgetUiState.from(config, today, "Tage").sizeTier)
    }

    @Test
    fun `missing config yields placeholder with default title and color`() {
        val state = WidgetUiState.from(null, today, defaultTitle = "Tage")

        assertTrue(state.isPlaceholder)
        assertEquals("Tage", state.title)
        assertEquals(WidgetUiState.PLACEHOLDER_TEXT, state.valueText)
        assertEquals(HeaderColor.DEFAULT, state.color)
        assertEquals(DigitSizeTier.ONE_DIGIT, state.sizeTier)
        assertFalse(WidgetUiState.from(WidgetConfig("x", today, HeaderColor.RED), today, "Tage").isPlaceholder)
    }

    @Test
    fun `placeholder is an en dash`() {
        assertEquals("–", WidgetUiState.PLACEHOLDER_TEXT)
    }
}
