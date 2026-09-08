package com.pimorazelvanto.dayscounter.config

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pimorazelvanto.dayscounter.AppContainer

class ConfigViewModelFactory(
    private val appWidgetId: Int,
    private val container: AppContainer,
    private val defaultTitle: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ConfigViewModel::class.java)) { "Unknown ViewModel ${modelClass.name}" }
        return ConfigViewModel(
            appWidgetId,
            container.repository,
            container.clock,
            container.widgetUpdater,
            container.midnightUpdateScheduler,
            defaultTitle,
        ) as T
    }
}
