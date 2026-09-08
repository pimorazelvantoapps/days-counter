package com.pimorazelvanto.dayscounter.domain

import java.time.LocalDate

data class WidgetConfig(
    val title: String,
    val targetDate: LocalDate,
    val color: HeaderColor,
)
