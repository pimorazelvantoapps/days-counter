package com.pimorazelvanto.dayscounter.config

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import com.pimorazelvanto.dayscounter.domain.TargetDateValidator
import com.pimorazelvanto.dayscounter.domain.ValidationResult
import java.time.LocalDate

/**
 * Keeps the picker from offering a day the target-date rule would reject. It is a
 * convenience, not the guarantee: a dialog left open across midnight can still hand back
 * a date that is no longer in the future, so [ConfigUiState.isValid] stays the real check.
 */
@OptIn(ExperimentalMaterial3Api::class)
class FutureOnlySelectableDates(
    private val today: LocalDate,
) : SelectableDates {
    override fun isSelectableDate(utcTimeMillis: Long): Boolean =
        TargetDateValidator.validate(today, utcMillisToLocalDate(utcTimeMillis)) == ValidationResult.Valid

    override fun isSelectableYear(year: Int): Boolean = year >= today.year
}
