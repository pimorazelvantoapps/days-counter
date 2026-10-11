package com.pimorazelvanto.dayscounter.config

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pimorazelvanto.dayscounter.domain.DigitSizeTier
import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.WidgetUiState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WidgetPreviewTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun titleIsFullyVisibleAtNormalFontSize() = assertTitleFullyVisible(fontScale = 1f)

    @Test
    fun titleIsFullyVisibleWhenTheSystemFontIsDoubled() = assertTitleFullyVisible(fontScale = 2f)

    private fun assertTitleFullyVisible(fontScale: Float) {
        val state = WidgetUiState("Urlaub", "10", DigitSizeTier.TWO_DIGITS, HeaderColor.RED, isPlaceholder = false)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                // The theme supplies the line height the screen really uses.
                MaterialTheme { WidgetPreview(state) }
            }
        }

        val layouts = mutableListOf<TextLayoutResult>()
        composeRule
            .onNodeWithText(
                "Urlaub",
            ).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()

        val visibleEnd = layout.getLineEnd(0, visibleEnd = true)
        assertTrue(
            "overflow=${layout.didOverflowHeight} visibleEnd=$visibleEnd size=${layout.size} lines=${layout.lineCount}",
            !layout.didOverflowHeight && visibleEnd == state.title.length,
        )
    }
}
