package com.pimorazelvanto.dayscounter.domain

import java.time.LocalDate

sealed interface ValidationResult {
    data object Valid : ValidationResult

    data object NotInFuture : ValidationResult
}

object TargetDateValidator {
    fun validate(
        today: LocalDate,
        candidate: LocalDate,
    ): ValidationResult = if (candidate.isAfter(today)) ValidationResult.Valid else ValidationResult.NotInFuture
}
