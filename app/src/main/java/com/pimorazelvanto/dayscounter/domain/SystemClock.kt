package com.pimorazelvanto.dayscounter.domain

import java.time.LocalDate
import java.time.ZoneId

class SystemClock : Clock {
    override fun today(): LocalDate = LocalDate.now(zone())

    override fun zone(): ZoneId = ZoneId.systemDefault()
}
