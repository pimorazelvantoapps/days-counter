package com.pimorazelvanto.dayscounter.config

import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.WidgetConfig
import com.pimorazelvanto.dayscounter.domain.WidgetUiState
import java.time.LocalDate

sealed interface SaveState {
    data object Idle : SaveState

    data object Saving : SaveState

    data object Saved : SaveState

    data object Failed : SaveState
}

data class ConfigUiState(
    val title: String,
    val targetDate: LocalDate?,
    val color: HeaderColor,
    val today: LocalDate,
    val defaultTitle: String,
    val saveState: SaveState = SaveState.Idle,
) {
    val isValid: Boolean = targetDate != null

    val effectiveTitle: String = title.ifBlank { defaultTitle }

    val preview: WidgetUiState =
        WidgetUiState.from(
            config = targetDate?.let { WidgetConfig(effectiveTitle, it, color) },
            today = today,
            // The placeholder shown before a date is picked takes the typed title, so the
            // preview follows the title field from the first keystroke.
            defaultTitle = effectiveTitle,
        )
}
