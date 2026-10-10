package com.pimorazelvanto.dayscounter.widget

import android.app.AlarmManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pimorazelvanto.dayscounter.testsupport.ControlledClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AlarmManagerMidnightUpdateSchedulerTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private val today = LocalDate.of(2026, 9, 8)
    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var scheduler: AlarmManagerMidnightUpdateScheduler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        alarmManager = context.getSystemService(AlarmManager::class.java)
        scheduler = AlarmManagerMidnightUpdateScheduler(context, alarmManager, ControlledClock(today, zone))
    }

    @Test
    fun `schedules wakeup alarm in a ten-minute window starting at next local midnight`() {
        scheduler.schedule()

        val alarm = shadowOf(alarmManager).scheduledAlarms.single()
        val expectedMidnight =
            today
                .plusDays(1)
                .atStartOfDay(zone)
                .toInstant()
                .toEpochMilli()
        assertEquals(expectedMidnight, alarm.triggerAtMs)
        assertEquals(AlarmManager.RTC_WAKEUP, alarm.getType())
        assertEquals(TEN_MINUTES_MS, alarm.windowLengthMs)
    }

    @Test
    fun `on the 23-hour day clocks go forward in Berlin the alarm targets the following midnight`() {
        assertNextMidnight(LocalDate.of(2026, 3, 29), zone, Instant.parse("2026-03-29T22:00:00Z"))
    }

    @Test
    fun `on the 25-hour day clocks go back in Berlin the alarm targets the following midnight`() {
        assertNextMidnight(LocalDate.of(2026, 10, 25), zone, Instant.parse("2026-10-25T23:00:00Z"))
    }

    @Test
    fun `when the next midnight is skipped in Santiago the alarm targets the first hour of that day`() {
        assertNextMidnight(
            LocalDate.of(2026, 9, 5),
            ZoneId.of("America/Santiago"),
            Instant.parse("2026-09-06T04:00:00Z"),
        )
    }

    @Test
    fun `rescheduling replaces instead of duplicating`() {
        scheduler.schedule()
        scheduler.schedule()

        assertEquals(1, shadowOf(alarmManager).scheduledAlarms.size)
    }

    @Test
    fun `cancel removes the alarm`() {
        scheduler.schedule()

        scheduler.cancel()

        assertTrue(shadowOf(alarmManager).scheduledAlarms.isEmpty())
    }

    @Suppress("DEPRECATION")
    @Test
    fun `alarm targets DateChangeReceiver with midnight action`() {
        scheduler.schedule()

        // ScheduledAlarm.operation has no non-deprecated accessor in this Robolectric version.
        val operation = shadowOf(alarmManager).scheduledAlarms.single().operation!!
        val intent = shadowOf(operation).savedIntent
        assertEquals(DateChangeReceiver.ACTION_MIDNIGHT, intent.action)
        assertEquals(DateChangeReceiver::class.java.name, intent.component?.className)
    }

    private fun assertNextMidnight(
        day: LocalDate,
        dayZone: ZoneId,
        expected: Instant,
    ) {
        AlarmManagerMidnightUpdateScheduler(context, alarmManager, ControlledClock(day, dayZone)).schedule()

        assertEquals(expected.toEpochMilli(), shadowOf(alarmManager).scheduledAlarms.single().triggerAtMs)
    }

    private companion object {
        const val TEN_MINUTES_MS = 10 * 60 * 1000L
    }
}
