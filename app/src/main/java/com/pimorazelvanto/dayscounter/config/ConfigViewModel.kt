package com.pimorazelvanto.dayscounter.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pimorazelvanto.dayscounter.data.WidgetConfigRepository
import com.pimorazelvanto.dayscounter.domain.Clock
import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.MidnightUpdateScheduler
import com.pimorazelvanto.dayscounter.domain.WidgetConfig
import com.pimorazelvanto.dayscounter.domain.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.time.LocalDate

class ConfigViewModel(
    private val appWidgetId: Int,
    private val repository: WidgetConfigRepository,
    private val clock: Clock,
    private val widgetUpdater: WidgetUpdater,
    private val midnightUpdateScheduler: MidnightUpdateScheduler,
    private val defaultTitle: String,
) : ViewModel() {
    private val mutableUiState =
        MutableStateFlow(
            ConfigUiState(
                title = defaultTitle,
                targetDate = null,
                color = HeaderColor.DEFAULT,
                today = clock.today(),
                defaultTitle = defaultTitle,
            ),
        )
    val uiState: StateFlow<ConfigUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch { loadExistingConfig() }
    }

    fun onTitleChanged(title: String) {
        mutableUiState.update { it.copy(title = title.take(MAX_TITLE_LENGTH)) }
    }

    fun onTargetDateChanged(targetDate: LocalDate) {
        mutableUiState.update { it.copy(targetDate = targetDate) }
    }

    fun onColorChanged(color: HeaderColor) {
        mutableUiState.update { it.copy(color = color) }
    }

    /**
     * Re-reads today's date so the live preview does not go stale if the configuration screen
     * sits open across midnight; the screen does not observe the clock the way the widget does.
     */
    fun refreshToday() {
        mutableUiState.update { it.copy(today = clock.today()) }
    }

    fun save() {
        val state = uiState.value
        if (state.saveState == SaveState.Saving) return
        val targetDate = state.targetDate ?: return
        mutableUiState.update { it.copy(saveState = SaveState.Saving) }
        viewModelScope.launch {
            val result = persist(WidgetConfig(state.effectiveTitle, targetDate, state.color))
            mutableUiState.update { it.copy(saveState = result) }
        }
    }

    fun onSaveFailureShown() {
        mutableUiState.update { it.copy(saveState = SaveState.Idle) }
    }

    /**
     * A read failure leaves the screen on its defaults, so that the widget stays configurable:
     * an exception escaping here would reach `viewModelScope` and crash the only entry point
     * the app has.
     */
    @Suppress("SwallowedException") // Reading defaults instead is the whole point of the catch.
    private suspend fun loadExistingConfig() {
        val existing =
            try {
                repository.load(appWidgetId)
            } catch (_: IOException) {
                null
            } ?: return
        mutableUiState.update {
            it.copy(title = existing.title, targetDate = existing.targetDate, color = existing.color)
        }
    }

    @Suppress("SwallowedException") // A write failure is deliberately turned into SaveState.Failed for the screen.
    private suspend fun persist(config: WidgetConfig): SaveState =
        try {
            repository.save(appWidgetId, config)
            widgetUpdater.updateAll()
            midnightUpdateScheduler.schedule()
            SaveState.Saved
        } catch (_: IOException) {
            SaveState.Failed
        }

    companion object {
        const val MAX_TITLE_LENGTH = 20
    }
}
