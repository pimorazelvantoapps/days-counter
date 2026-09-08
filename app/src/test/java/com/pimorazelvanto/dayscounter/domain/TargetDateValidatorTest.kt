package com.pimorazelvanto.dayscounter.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

class TargetDateValidatorTest {
    private val today = LocalDate.of(2026, 9, 8)

    @Test
    fun `tomorrow is valid`() {
        assertEquals(ValidationResult.Valid, TargetDateValidator.validate(today, today.plusDays(1)))
    }

    @Test
    fun `today is not in future`() {
        assertEquals(ValidationResult.NotInFuture, TargetDateValidator.validate(today, today))
    }

    @Test
    fun `yesterday is not in future`() {
        assertEquals(ValidationResult.NotInFuture, TargetDateValidator.validate(today, today.minusDays(1)))
    }
}
