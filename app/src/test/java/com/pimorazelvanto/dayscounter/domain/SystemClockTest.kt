package com.pimorazelvanto.dayscounter.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.TimeZone

@OptIn(ExperimentalCoroutinesApi::class)
class SystemClockTest {
    private val firstDay = LocalDate.of(2026, 9, 8)
    private val secondDay = firstDay.plusDays(1)

    // Noon rather than midnight, so that adding a day crosses the date boundary even in a zone
    // whose clocks move by an hour that night.
    private val instantSource =
        MovableInstantSource(firstDay.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant())
    private val clock = SystemClock(instantSource)

    @Test
    fun `days emits the current day on collection`() =
        runTest {
            assertEquals(firstDay, clock.days().first())
        }

    @Test
    fun `days emits again after a date change that landed on another day`() =
        runTest {
            val emitted = collectDays()

            instantSource.advance(Duration.ofDays(1))
            clock.dateChanged()

            assertEquals(listOf(firstDay, secondDay), emitted)
        }

    @Test
    fun `days stays silent for date changes within the same day`() =
        runTest {
            val emitted = collectDays()

            clock.dateChanged()
            clock.dateChanged()

            assertEquals(listOf(firstDay), emitted)
        }

    @Test
    fun `today is still the previous day at 23 55 and the new day at midnight`() =
        withDefaultZone(BERLIN) {
            val lateEvening = MovableInstantSource(Instant.parse("2026-09-07T21:55:00Z"))
            val clock = SystemClock(lateEvening)

            val beforeMidnight = clock.today()
            lateEvening.advance(Duration.ofMinutes(5))

            assertEquals(LocalDate.of(2026, 9, 7), beforeMidnight)
            assertEquals(LocalDate.of(2026, 9, 8), clock.today())
        }

    @Test
    fun `today and zone follow a device time-zone change without a new clock`() =
        withDefaultZone(BERLIN) {
            val clock = SystemClock(MovableInstantSource(Instant.parse("2026-09-08T00:30:00Z")))
            val dayInBerlin = clock.today()

            TimeZone.setDefault(TimeZone.getTimeZone(NEW_YORK))

            assertEquals(LocalDate.of(2026, 9, 8), dayInBerlin)
            assertEquals(LocalDate.of(2026, 9, 7), clock.today())
            assertEquals(NEW_YORK, clock.zone())
        }

    private fun withDefaultZone(
        zone: ZoneId,
        block: () -> Unit,
    ) {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(zone))
        try {
            block()
        } finally {
            TimeZone.setDefault(original)
        }
    }

    /** Collects into a list that grows as the test drives the clock; cancelled with the test. */
    private fun TestScope.collectDays(): List<LocalDate> {
        val emitted = mutableListOf<LocalDate>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { clock.days().toList(emitted) }
        return emitted
    }

    private class MovableInstantSource(
        private var current: Instant,
    ) : java.time.Clock() {
        override fun instant(): Instant = current

        override fun getZone(): ZoneId = ZoneId.systemDefault()

        override fun withZone(zone: ZoneId): java.time.Clock = fixed(current, zone)

        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }

    private companion object {
        val BERLIN: ZoneId = ZoneId.of("Europe/Berlin")
        val NEW_YORK: ZoneId = ZoneId.of("America/New_York")
    }
}
