package com.pimorazelvanto.dayscounter.config

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pimorazelvanto.dayscounter.DaysCounterApplication
import com.pimorazelvanto.dayscounter.R
import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.WidgetConfig
import com.pimorazelvanto.dayscounter.testsupport.FakeAppContainer
import com.pimorazelvanto.dayscounter.testsupport.FakeWidgetConfigRepository
import com.pimorazelvanto.dayscounter.testsupport.FixedClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ConfigActivityTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val today = LocalDate.now()
    private val tomorrow = today.plusDays(1)
    private val repository = FakeWidgetConfigRepository()
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        (context as DaysCounterApplication).container =
            FakeAppContainer(clock = FixedClock(today), repository = repository)
    }

    private fun launch(appWidgetId: Int = 42): ActivityScenario<ConfigActivity> =
        ActivityScenario.launchActivityForResult(ConfigActivity.createIntent(context, appWidgetId))

    private fun string(
        resId: Int,
        vararg args: Any,
    ): String = context.getString(resId, *args)

    /**
     * The picker opens on the month of the first selectable day and offers only future dates, so
     * its first enabled day cell is tomorrow. A day cell carries the full localized date as its
     * semantics text rather than the bare day number, which is why it is addressed by that
     * position instead of by text; that the picked day really is tomorrow is asserted by the
     * caller through the saved configuration.
     */
    private fun pickTomorrow() {
        composeRule.onNodeWithTag(ConfigTestTags.DATE_FIELD).performClick()
        composeRule.onAllNodes(isSelectable() and isEnabled()).onFirst().performClick()
        composeRule.onNodeWithText(string(R.string.config_action_ok)).performClick()
    }

    @Test
    fun fullFlow_savesConfigAndReturnsOk() {
        val scenario = launch()

        composeRule.onNodeWithTag(ConfigTestTags.TITLE_FIELD).performTextClearance()
        composeRule.onNodeWithTag(ConfigTestTags.TITLE_FIELD).performTextInput("Urlaub")
        pickTomorrow()
        composeRule.onNodeWithTag(ConfigTestTags.COLOR_FIELD).performClick()
        composeRule.onNodeWithTag(ConfigTestTags.colorOption(HeaderColor.GREEN)).performClick()
        composeRule.onNodeWithTag(ConfigTestTags.SAVE_BUTTON).assertIsEnabled().performClick()

        composeRule.waitUntil(5_000) { scenario.state == Lifecycle.State.DESTROYED }
        assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
        assertEquals(42, scenario.result.resultData.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        assertEquals(WidgetConfig("Urlaub", tomorrow, HeaderColor.GREEN), repository.saved[42])
    }

    @Test
    fun saveIsDisabledWithoutDate() {
        launch()

        composeRule.onNodeWithTag(ConfigTestTags.SAVE_BUTTON).assertIsNotEnabled()
        composeRule.onNodeWithTag(ConfigTestTags.PREVIEW).assertIsDisplayed()
    }

    @Test
    fun cancelReturnsCanceledAndWritesNothing() {
        val scenario = launch()

        composeRule.onNodeWithTag(ConfigTestTags.CANCEL_BUTTON).performClick()

        composeRule.waitUntil(5_000) { scenario.state == Lifecycle.State.DESTROYED }
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
        assertTrue(repository.saved.isEmpty())
    }

    @Test
    fun colorDialogShowsTwelveOptionsAndSelectionUpdatesField() {
        launch()

        composeRule.onNodeWithTag(ConfigTestTags.COLOR_FIELD).performClick()
        HeaderColor.entries.forEach { composeRule.onNodeWithTag(ConfigTestTags.colorOption(it)).assertIsDisplayed() }
        composeRule.onNodeWithTag(ConfigTestTags.colorOption(HeaderColor.TEAL)).performClick()

        composeRule.onAllNodesWithTag(ConfigTestTags.COLOR_GRID).assertCountEquals(0)
        composeRule.onNodeWithText(string(R.string.color_name_teal)).assertIsDisplayed()
    }

    @Test
    fun existingConfigIsPreloaded() {
        repository.saved[42] = WidgetConfig("Geburtstag", today.plusDays(30), HeaderColor.PINK)

        launch()

        composeRule.onNodeWithText("Geburtstag").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.color_name_pink)).assertIsDisplayed()
        composeRule.onNodeWithTag(ConfigTestTags.SAVE_BUTTON).assertIsEnabled()
    }

    @Test
    fun missingWidgetIdFinishesWithCanceled() {
        val scenario =
            ActivityScenario.launchActivityForResult<ConfigActivity>(Intent(context, ConfigActivity::class.java))

        composeRule.waitUntil(5_000) { scenario.state == Lifecycle.State.DESTROYED }
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
    }
}
