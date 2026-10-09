package com.pimorazelvanto.dayscounter.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.pimorazelvanto.dayscounter.domain.Clock
import com.pimorazelvanto.dayscounter.domain.MidnightUpdateScheduler
import java.time.Duration

class AlarmManagerMidnightUpdateScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager,
    private val clock: Clock,
) : MidnightUpdateScheduler {
    /**
     * Fires within [MIDNIGHT_WINDOW] after midnight, never before it. Exact alarms would need
     * USE_EXACT_ALARM, which Google Play reserves for alarm clock and calendar apps. A plain
     * `set()` is no alternative: Android derives its window from the remaining time and could
     * delay an alarm scheduled a day ahead by many hours. In Doze the alarm waits for the next
     * maintenance window or for the device to wake up, when the screen is off anyway.
     */
    override fun schedule() {
        alarmManager.setWindow(
            AlarmManager.RTC_WAKEUP,
            nextMidnightEpochMillis(),
            MIDNIGHT_WINDOW.toMillis(),
            midnightPendingIntent(),
        )
    }

    override fun cancel() {
        alarmManager.cancel(midnightPendingIntent())
    }

    private fun nextMidnightEpochMillis(): Long =
        clock
            .today()
            .plusDays(1)
            .atStartOfDay(clock.zone())
            .toInstant()
            .toEpochMilli()

    private fun midnightPendingIntent(): PendingIntent {
        val intent = Intent(context, DateChangeReceiver::class.java).setAction(DateChangeReceiver.ACTION_MIDNIGHT)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE_MIDNIGHT,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private companion object {
        const val REQUEST_CODE_MIDNIGHT = 1

        /** The shortest window Android grants an inexact alarm since API 31. */
        val MIDNIGHT_WINDOW: Duration = Duration.ofMinutes(10)
    }
}
