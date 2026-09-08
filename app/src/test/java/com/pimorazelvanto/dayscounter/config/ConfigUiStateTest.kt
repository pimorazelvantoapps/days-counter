package com.pimorazelvanto.dayscounter.config

import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.ValidationResult
import com.pimorazelvanto.dayscounter.domain.WidgetUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
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
        assertNull(base.validation)
        assertFalse(base.isValid)
        assertTrue(base.preview.isPlaceholder)
    }

    @Test
    fun `future date is valid and preview shows days`() {
        val state = base.copy(targetDate = today.plusDays(3))

        assertEquals(ValidationResult.Valid, state.validation)
        assertTrue(state.isValid)
        assertEquals("3", state.preview.valueText)
        assertEquals("Urlaub", state.preview.title)
    }

    @Test
    fun `today is invalid`() {
        val state = base.copy(targetDate = today)

        assertEquals(ValidationResult.NotInFuture, state.validation)
        assertFalse(state.isValid)
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
