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
            WidgetUiState("Urlaub", "42", DigitSizeTier.LARGE, HeaderColor.BLUE, isPlaceholder = false),
            state,
        )
    }

    @Test
    fun `passed target shows hyphen`() {
        val config = WidgetConfig("Urlaub", today.minusDays(1), HeaderColor.BLUE)

        assertEquals("-", WidgetUiState.from(config, today, "Tage").valueText)
    }

    @Test
    fun `four digit values use small tier`() {
        val config = WidgetConfig("Rente", today.plusDays(4000), HeaderColor.GREY)

        assertEquals(DigitSizeTier.SMALL, WidgetUiState.from(config, today, "Tage").sizeTier)
    }

    @Test
    fun `missing config yields placeholder with default title and color`() {
        val state = WidgetUiState.from(null, today, defaultTitle = "Tage")

        assertTrue(state.isPlaceholder)
        assertEquals("Tage", state.title)
        assertEquals(WidgetUiState.PLACEHOLDER_TEXT, state.valueText)
        assertEquals(HeaderColor.DEFAULT, state.color)
        assertEquals(DigitSizeTier.LARGE, state.sizeTier)
        assertFalse(WidgetUiState.from(WidgetConfig("x", today, HeaderColor.RED), today, "Tage").isPlaceholder)
    }
}
