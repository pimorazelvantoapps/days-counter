package com.pimorazelvanto.dayscounter.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DateChangeReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) = Unit

    companion object {
        const val ACTION_MIDNIGHT = "com.pimorazelvanto.dayscounter.action.MIDNIGHT"
    }
}
