package com.pimorazelvanto.dayscounter.domain

import java.time.LocalDate
import java.time.ZoneId

interface Clock {
    fun today(): LocalDate

    fun zone(): ZoneId
}
