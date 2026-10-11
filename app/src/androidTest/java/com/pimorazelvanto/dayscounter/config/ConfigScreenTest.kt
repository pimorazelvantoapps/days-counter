package com.pimorazelvanto.dayscounter.config

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pimorazelvanto.dayscounter.domain.HeaderColor
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ConfigScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun saveButtonCanBeScrolledIntoViewOnALandscapePhone() {
        val today = LocalDate.of(2026, 9, 8)
        val state = ConfigUiState("Urlaub", today.plusDays(10), HeaderColor.BLUE, today, defaultTitle = "days")
        composeRule.setContent {
            // The height a 412dp-wide phone leaves for the screen in landscape.
            Box(Modifier.size(width = 800.dp, height = 240.dp)) {
                ConfigScreen(
                    state = state,
                    onTitleChanged = {},
                    onTargetDateChanged = {},
                    onColorChanged = {},
                    onSave = {},
                    onCancel = {},
                    onSaveFailureShown = {},
                )
            }
        }

        composeRule.onNodeWithTag(ConfigTestTags.SAVE_BUTTON).performScrollTo().assertIsDisplayed()
    }
}
