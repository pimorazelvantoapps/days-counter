package com.pimorazelvanto.dayscounter.widget

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.pimorazelvanto.dayscounter.domain.Clock
import com.pimorazelvanto.dayscounter.domain.MidnightUpdateScheduler

class AlarmManagerMidnightUpdateScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager,
    private val clock: Clock,
) : MidnightUpdateScheduler {
    // Lint's MissingPermission check for setExactAndAllowWhileIdle only recognises
    // SCHEDULE_EXACT_ALARM, not the USE_EXACT_ALARM permission declared in the manifest.
    // minSdk 33 makes USE_EXACT_ALARM a normal, always-granted permission that covers this call.
    @SuppressLint("MissingPermission")
    override fun schedule() {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextMidnightEpochMillis(),
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
    }
}
