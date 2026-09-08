package com.pimorazelvanto.dayscounter.config

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity

class ConfigActivity : ComponentActivity() {
    companion object {
        fun createIntent(
            context: Context,
            appWidgetId: Int,
        ): Intent =
            Intent(context, ConfigActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}
