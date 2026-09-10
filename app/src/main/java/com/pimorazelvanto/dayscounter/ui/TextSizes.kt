package com.pimorazelvanto.dayscounter.ui

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.pimorazelvanto.dayscounter.domain.DigitSizeTier

/**
 * The single source for the text sizes shared by the Glance widget sheet
 * (`widget/DaysCounterWidgetContent.kt`) and its Compose mirror on the configuration screen
 * (`config/WidgetPreview.kt`). Both need [TextUnit], which is why this cannot live in `domain`.
 * The static launcher preview (`res/layout/widget_preview.xml`) is plain view XML and cannot
 * read Kotlin, so it keeps its own copy of the sizes it needs in `dimens.xml`.
 */
object TextSizes {
    val TITLE = 10.sp

    private val ONE_DIGIT = 26.sp
    private val TWO_DIGITS = 21.sp
    private val THREE_DIGITS = 17.sp
    private val FOUR_DIGITS = 14.sp
    private val FIVE_OR_MORE_DIGITS = 11.sp

    fun value(tier: DigitSizeTier): TextUnit =
        when (tier) {
            DigitSizeTier.ONE_DIGIT -> ONE_DIGIT
            DigitSizeTier.TWO_DIGITS -> TWO_DIGITS
            DigitSizeTier.THREE_DIGITS -> THREE_DIGITS
            DigitSizeTier.FOUR_DIGITS -> FOUR_DIGITS
            DigitSizeTier.FIVE_OR_MORE_DIGITS -> FIVE_OR_MORE_DIGITS
        }
}
