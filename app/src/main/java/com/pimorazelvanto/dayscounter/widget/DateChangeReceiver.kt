package com.pimorazelvanto.dayscounter.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pimorazelvanto.dayscounter.appContainer
import kotlinx.coroutines.launch

class DateChangeReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action !in HANDLED_ACTIONS) return
        val container = context.appContainer
        val pendingResult: PendingResult? = goAsync()
        container.backgroundScope.launch {
            try {
                container.clock.dateChanged()
                container.widgetUpdater.updateAll()
                container.midnightUpdateScheduler.schedule()
            } finally {
                pendingResult?.finish()
            }
        }
    }

    companion object {
        const val ACTION_MIDNIGHT = "com.pimorazelvanto.dayscounter.action.MIDNIGHT"

        /** Internal so that a test can compare it against the intent filters of the manifest. */
        internal val HANDLED_ACTIONS =
            setOf(
                ACTION_MIDNIGHT,
                Intent.ACTION_TIMEZONE_CHANGED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_MY_PACKAGE_REPLACED,
            )
    }
}
