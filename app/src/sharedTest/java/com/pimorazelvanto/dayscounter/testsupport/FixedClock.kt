package com.pimorazelvanto.dayscounter.testsupport

import com.pimorazelvanto.dayscounter.domain.Clock
import java.time.LocalDate
import java.time.ZoneId

class FixedClock(
    private val fixedToday: LocalDate,
    private val fixedZone: ZoneId = ZoneId.of("Europe/Berlin"),
) : Clock {
    override fun today(): LocalDate = fixedToday

    override fun zone(): ZoneId = fixedZone
}
