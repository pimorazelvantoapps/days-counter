package com.pimorazelvanto.dayscounter.config

import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.WidgetUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ConfigUiStateTest {
    private val today = LocalDate.of(2026, 9, 8)
    private val base =
        ConfigUiState(
            title = "Urlaub",
            targetDate = null,
            color = HeaderColor.BLUE,
            today = today,
            defaultTitle = "Tage",
        )

    @Test
    fun `without date state is invalid and preview is placeholder`() {
        assertFalse(base.isValid)
        assertTrue(base.preview.isPlaceholder)
    }

    @Test
    fun `future date is valid and preview shows remaining days`() {
        val state = base.copy(targetDate = today.plusDays(3))

        assertTrue(state.isValid)
        assertEquals("3", state.preview.valueText)
        assertEquals("Urlaub", state.preview.title)
    }

    @Test
    fun `past date is valid and preview shows elapsed days`() {
        val state = base.copy(targetDate = today.minusDays(3))

        assertTrue(state.isValid)
        assertEquals("3", state.preview.valueText)
    }

    @Test
    fun `today is valid and preview shows zero`() {
        val state = base.copy(targetDate = today)

        assertTrue(state.isValid)
        assertEquals("0", state.preview.valueText)
    }

    @Test
    fun `blank title previews with default title`() {
        val state = base.copy(title = "   ", targetDate = today.plusDays(1))

        assertEquals("Tage", state.preview.title)
    }

    @Test
    fun `preview uses selected color`() {
        val state = base.copy(targetDate = today.plusDays(1), color = HeaderColor.PINK)

        assertEquals(
            WidgetUiState
                .from(
                    null,
                    today,
                    "Tage",
                ).copy(title = "Urlaub", valueText = "1", color = HeaderColor.PINK, isPlaceholder = false),
            state.preview,
        )
    }
}
