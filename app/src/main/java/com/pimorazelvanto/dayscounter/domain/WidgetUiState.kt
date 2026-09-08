package com.pimorazelvanto.dayscounter.domain

import java.time.LocalDate

/**
 * Lives in `domain` so that `widget` and `config` can render the same display state
 * without knowing each other.
 */
data class WidgetUiState(
    val title: String,
    val valueText: String,
    val sizeTier: DigitSizeTier,
    val color: HeaderColor,
    val isPlaceholder: Boolean,
) {
    companion object {
        /** En dash, deliberately distinct from the hyphen a passed target shows. */
        const val PLACEHOLDER_TEXT = "–"

        fun from(
            config: WidgetConfig?,
            today: LocalDate,
            defaultTitle: String,
        ): WidgetUiState {
            if (config == null) {
                return WidgetUiState(
                    title = defaultTitle,
                    valueText = PLACEHOLDER_TEXT,
                    sizeTier = DigitSizeTier.LARGE,
                    color = HeaderColor.DEFAULT,
                    isPlaceholder = true,
                )
            }
            val value = DaysCalculator.calculate(today, config.targetDate)
            return WidgetUiState(
                title = config.title,
                valueText = value.text,
                sizeTier = value.sizeTier(),
                color = config.color,
                isPlaceholder = false,
            )
        }
    }
}
