package com.pimorazelvanto.dayscounter.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ExperimentalGlanceApi
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.compose
import androidx.glance.appwidget.runComposition
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pimorazelvanto.dayscounter.AppContainer
import com.pimorazelvanto.dayscounter.DaysCounterApplication
import com.pimorazelvanto.dayscounter.R
import com.pimorazelvanto.dayscounter.config.ConfigActivity
import com.pimorazelvanto.dayscounter.domain.DigitSizeTier
import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.WidgetConfig
import com.pimorazelvanto.dayscounter.domain.WidgetUiState
import com.pimorazelvanto.dayscounter.testsupport.ControlledClock
import com.pimorazelvanto.dayscounter.testsupport.FakeAppContainer
import com.pimorazelvanto.dayscounter.testsupport.FakeWidgetConfigRepository
import kotlinx.coroutines.flow.collectIndexed
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@OptIn(ExperimentalGlanceRemoteViewsApi::class, ExperimentalGlanceApi::class)
@RunWith(AndroidJUnit4::class)
class DaysCounterWidgetRenderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val widgetSize = DpSize(60.dp, 60.dp)
    private val today = LocalDate.of(2026, 9, 8)
    private val tomorrow = today.plusDays(1)
    private val clock = ControlledClock(today)
    private val repository = FakeWidgetConfigRepository()
    private var applicationContainer: AppContainer? = null

    @After
    fun tearDown() {
        applicationContainer?.let { (context as DaysCounterApplication).container = it }
    }

    @Test
    fun configuredContentInflatesWithTitleAndValue() =
        runBlocking {
            val state = WidgetUiState("Urlaub", "42", DigitSizeTier.ONE_DIGIT, HeaderColor.BLUE, isPlaceholder = false)
            val result =
                GlanceRemoteViews().compose(context, widgetSize) {
                    DaysCounterWidgetContent(state, actionStartActivity(ConfigActivity.createIntent(context, 1)))
                }

            val texts = inflateTexts(result.remoteViews)

            assertTrue("texts were $texts", "Urlaub" in texts && "42" in texts)
        }

    @Test
    fun realWidgetWithoutConfigRendersPlaceholder() =
        runBlocking {
            val remoteViews = DaysCounterWidget().compose(context, size = widgetSize)

            val texts = inflateTexts(remoteViews)

            assertTrue("texts were $texts", defaultTitle() in texts && WidgetUiState.PLACEHOLDER_TEXT in texts)
        }

    /** Drives a save and a day boundary from inside the collection of one running composition. */
    @Test
    fun runningCompositionFollowsASaveAndTheNextDay() =
        runBlocking {
            installFakeContainer()
            val config = WidgetConfig("Urlaub", tomorrow, HeaderColor.BLUE)
            val renderings = mutableListOf<List<String>>()

            withTimeoutOrNull(COMPOSITION_TIMEOUT_MILLIS) {
                DaysCounterWidget().runComposition(context).take(EXPECTED_RENDERINGS).collectIndexed { index, views ->
                    renderings += inflateTexts(views)
                    when (index) {
                        0 -> {
                            repository.save(observedAppWidgetId(), config)
                        }

                        1 -> {
                            clock.moveTo(tomorrow)
                            clock.dateChanged()
                        }
                    }
                }
            }

            assertEquals(
                listOf(
                    listOf(defaultTitle(), WidgetUiState.PLACEHOLDER_TEXT),
                    listOf("Urlaub", "1"),
                    listOf("Urlaub", "0"),
                ),
                renderings,
            )
        }

    /** The container is process-global, so [tearDown] puts the real one back. */
    private fun installFakeContainer() {
        val application = context as DaysCounterApplication
        applicationContainer = application.container
        application.container = FakeAppContainer(clock = clock, repository = repository)
    }

    private fun defaultTitle(): String = context.getString(R.string.default_title)

    private fun observedAppWidgetId(): Int = requireNotNull(repository.observedAppWidgetId)

    private fun inflateTexts(remoteViews: android.widget.RemoteViews): List<String> {
        val texts = mutableListOf<String>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val parent = FrameLayout(context)
            val view = remoteViews.apply(context, parent)
            collectTexts(view, texts)
        }
        return texts
    }

    private fun collectTexts(
        view: View,
        into: MutableList<String>,
    ) {
        if (view is TextView) into += view.text.toString()
        if (view is ViewGroup) (0 until view.childCount).forEach { collectTexts(view.getChildAt(it), into) }
    }

    private companion object {
        const val EXPECTED_RENDERINGS = 3
        const val COMPOSITION_TIMEOUT_MILLIS = 15_000L
    }
}
