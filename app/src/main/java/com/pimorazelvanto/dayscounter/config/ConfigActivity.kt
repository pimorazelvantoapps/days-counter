package com.pimorazelvanto.dayscounter.config

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.pimorazelvanto.dayscounter.R
import com.pimorazelvanto.dayscounter.appContainer

/**
 * Hosts the configuration screen for one widget. The result is the contract with the launcher:
 * `RESULT_CANCELED` is set before anything else, so abandoning the screen makes the launcher
 * discard the pending widget, and only a completed save replaces it with `RESULT_OK`.
 */
class ConfigActivity : ComponentActivity() {
    private val appWidgetId: Int by lazy {
        intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
    }

    private val viewModel: ConfigViewModel by viewModels {
        ConfigViewModelFactory(appWidgetId, appContainer, getString(R.string.default_title))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            MaterialTheme {
                val state by viewModel.uiState.collectAsState()
                LaunchedEffect(state.saveState) {
                    if (state.saveState == SaveState.Saved) finishWithSuccess()
                }
                ConfigScreen(
                    state = state,
                    onTitleChanged = viewModel::onTitleChanged,
                    onTargetDateChanged = viewModel::onTargetDateChanged,
                    onColorChanged = viewModel::onColorChanged,
                    onSave = viewModel::save,
                    onCancel = ::finish,
                    onSaveFailureShown = viewModel::onSaveFailureShown,
                )
            }
        }
    }

    /** Re-validates a date that was picked before midnight against the day the user returns on. */
    override fun onResume() {
        super.onResume()
        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) viewModel.refreshToday()
    }

    private fun finishWithSuccess() {
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }

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
