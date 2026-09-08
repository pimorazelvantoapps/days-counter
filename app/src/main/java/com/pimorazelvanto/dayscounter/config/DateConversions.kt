package com.pimorazelvanto.dayscounter.config

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Material 3 date pickers speak epoch millis at UTC midnight, so the offset is fixed
 * rather than taken from the device zone.
 */
fun LocalDate.toUtcStartOfDayMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun utcMillisToLocalDate(utcMillis: Long): LocalDate =
    Instant.ofEpochMilli(utcMillis).atOffset(ZoneOffset.UTC).toLocalDate()
