package com.pimorazelvanto.dayscounter.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object DaysCalculator {
    fun calculate(
        today: LocalDate,
        target: LocalDate,
    ): DisplayValue {
        val remainingDays = ChronoUnit.DAYS.between(today, target).toInt()
        return when {
            remainingDays > 0 -> DisplayValue.Days(remainingDays)
            remainingDays == 0 -> DisplayValue.Reached
            else -> DisplayValue.Passed
        }
    }
}
