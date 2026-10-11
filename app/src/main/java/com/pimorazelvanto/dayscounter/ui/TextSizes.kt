package com.pimorazelvanto.dayscounter.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pimorazelvanto.dayscounter.domain.DigitSizeTier

/**
 * The single source for the text sizes shared by the Glance widget sheet
 * (`widget/WidgetTextViews.kt`) and its Compose mirror on the configuration screen
 * (`config/WidgetPreview.kt`). The static launcher preview (`res/layout/widget_preview.xml`) is
 * plain view XML and cannot read Kotlin, so it keeps its own copy of the sizes it needs in
 * `dimens.xml`.
 *
 * The sizes are in dp, not sp: the widget is a fixed 1x1 cell, and text that grew with the
 * system font size cut the title short and replaced the day count with an ellipsis.
 */
object TextSizes {
    val TITLE = 10.dp

    private val ONE_DIGIT = 26.dp
    private val TWO_DIGITS = 21.dp
    private val THREE_DIGITS = 17.dp
    private val FOUR_DIGITS = 14.dp
    private val FIVE_OR_MORE_DIGITS = 11.dp

    fun value(tier: DigitSizeTier): Dp =
        when (tier) {
            DigitSizeTier.ONE_DIGIT -> ONE_DIGIT
            DigitSizeTier.TWO_DIGITS -> TWO_DIGITS
            DigitSizeTier.THREE_DIGITS -> THREE_DIGITS
            DigitSizeTier.FOUR_DIGITS -> FOUR_DIGITS
            DigitSizeTier.FIVE_OR_MORE_DIGITS -> FIVE_OR_MORE_DIGITS
        }
}
