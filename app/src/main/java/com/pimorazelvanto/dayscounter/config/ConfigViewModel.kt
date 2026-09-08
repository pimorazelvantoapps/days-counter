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

    fun refreshToday() {
        mutableUiState.update { it.copy(today = clock.today()) }
    }

    fun save() {
        val state = uiState.value
        val targetDate = state.targetDate
        if (!state.isValid || targetDate == null) return
        viewModelScope.launch {
            mutableUiState.update { it.copy(saveState = SaveState.Saving) }
            val result = persist(WidgetConfig(state.effectiveTitle, targetDate, state.color))
            mutableUiState.update { it.copy(saveState = result) }
        }
    }

    fun onSaveFailureShown() {
        mutableUiState.update { it.copy(saveState = SaveState.Idle) }
    }

    private suspend fun loadExistingConfig() {
        val existing = repository.load(appWidgetId) ?: return
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
