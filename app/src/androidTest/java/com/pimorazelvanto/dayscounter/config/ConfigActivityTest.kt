package com.pimorazelvanto.dayscounter.config

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.pimorazelvanto.dayscounter.AppContainer
import com.pimorazelvanto.dayscounter.DaysCounterApplication
import com.pimorazelvanto.dayscounter.R
import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.WidgetConfig
import com.pimorazelvanto.dayscounter.testsupport.ControlledClock
import com.pimorazelvanto.dayscounter.testsupport.FakeAppContainer
import com.pimorazelvanto.dayscounter.testsupport.FakeWidgetConfigRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@RunWith(AndroidJUnit4::class)
class ConfigActivityTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    private val today = LocalDate.now()
    private val repository = FakeWidgetConfigRepository()
    private val scenarios = mutableListOf<ActivityScenario<ConfigActivity>>()
    private lateinit var context: Context
    private lateinit var applicationContainer: AppContainer

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val application = context as DaysCounterApplication
        applicationContainer = application.container
        application.container = FakeAppContainer(clock = ControlledClock(today), repository = repository)
    }

    /**
     * The container is process-global, so leaving the fake in place would leak this run's frozen
     * date into any instrumentation class that follows.
     */
    @After
    fun tearDown() {
        scenarios.forEach { it.close() }
        (context as DaysCounterApplication).container = applicationContainer
    }

    private fun launch(appWidgetId: Int = 42): ActivityScenario<ConfigActivity> =
        launchWith(ConfigActivity.createIntent(context, appWidgetId))

    private fun launchWith(intent: Intent): ActivityScenario<ConfigActivity> =
        ActivityScenario.launchActivityForResult<ConfigActivity>(intent).also { scenarios += it }

    private fun string(
        resId: Int,
        vararg args: Any,
    ): String = context.getString(resId, *args)

    /**
     * Navigates the picker one month back before picking, rather than picking yesterday in the
     * month it opens on: every month has a [MID_MONTH_DAY]th day, so this reaches a date in the
     * past regardless of today's day-of-month, whereas picking yesterday in today's own month
     * would fall outside it on the first of any month. The day cell is addressed by its full
     * localized date text rather than by position: the picker's month pager keeps a neighbouring
     * month's cells around after a navigation click, so a position- or count-based query can see
     * more than one month's days at once, but the target date's own text is unique regardless.
     */
    private fun pickMidPreviousMonth(): LocalDate {
        val target = today.minusMonths(1).withDayOfMonth(MID_MONTH_DAY)
        val targetCellText = target.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL))
        composeRule.onNodeWithTag(ConfigTestTags.DATE_FIELD).performClick()
        composeRule
            .onNode(hasContentDescription(PREVIOUS_MONTH_LABEL, substring = true, ignoreCase = true))
            .performClick()
        composeRule.onNodeWithText(targetCellText).performClick()
        composeRule.onNodeWithText(string(R.string.config_action_ok)).performClick()
        return target
    }

    @Test
    fun fullFlow_savesConfigAndReturnsOk() {
        val scenario = launch()

        composeRule.onNodeWithTag(ConfigTestTags.TITLE_FIELD).performTextClearance()
        composeRule.onNodeWithTag(ConfigTestTags.TITLE_FIELD).performTextInput("Urlaub")
        val pastDate = pickMidPreviousMonth()
        composeRule.onNodeWithTag(ConfigTestTags.COLOR_FIELD).performClick()
        composeRule.onNodeWithTag(ConfigTestTags.colorOption(HeaderColor.GREEN)).performClick()
        composeRule.onNodeWithTag(ConfigTestTags.SAVE_BUTTON).assertIsEnabled().performClick()

        composeRule.waitUntil(5_000) { scenario.state == Lifecycle.State.DESTROYED }
        assertEquals(Activity.RESULT_OK, scenario.result.resultCode)
        assertEquals(42, scenario.result.resultData.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        assertEquals(WidgetConfig("Urlaub", pastDate, HeaderColor.GREEN), repository.saved[42])
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
        composeRule
            .onAllNodes(isSelectable() and hasAnyAncestor(hasTestTag(ConfigTestTags.COLOR_GRID)))
            .assertCountEquals(12)
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
        val scenario = launchWith(Intent(context, ConfigActivity::class.java))

        composeRule.waitUntil(5_000) { scenario.state == Lifecycle.State.DESTROYED }
        assertEquals(Activity.RESULT_CANCELED, scenario.result.resultCode)
    }

    private companion object {
        const val MID_MONTH_DAY = 15

        /** The current label Material 3's date picker gives its previous-month button. */
        const val PREVIOUS_MONTH_LABEL = "previous month"
    }
}
