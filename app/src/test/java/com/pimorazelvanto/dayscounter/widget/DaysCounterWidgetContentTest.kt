package com.pimorazelvanto.dayscounter.widget

import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.testing.unit.hasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasTestTag
import androidx.test.core.app.ApplicationProvider
import com.pimorazelvanto.dayscounter.domain.DigitSizeTier
import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.WidgetUiState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DaysCounterWidgetContentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clickIntent = Intent(Intent.ACTION_VIEW)
    private val anyClick = actionStartActivity(clickIntent)

    @Test
    fun `renders title and value`() {
        val state = WidgetUiState("Urlaub", "42", DigitSizeTier.TWO_DIGITS, HeaderColor.BLUE, isPlaceholder = false)

        assertEquals(listOf("Urlaub", "42"), renderedTexts(state))
    }

    @Test
    fun `renders placeholder text`() {
        val state =
            WidgetUiState(
                "Tage",
                WidgetUiState.PLACEHOLDER_TEXT,
                DigitSizeTier.ONE_DIGIT,
                HeaderColor.RED,
                isPlaceholder = true,
            )

        assertEquals(listOf("Tage", WidgetUiState.PLACEHOLDER_TEXT), renderedTexts(state))
    }

    @Test
    fun `whole widget opens the supplied action`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable {
                DaysCounterWidgetContent(
                    WidgetUiState("Tage", "3", DigitSizeTier.ONE_DIGIT, HeaderColor.RED, isPlaceholder = false),
                    onClick = anyClick,
                )
            }

            onNode(hasTestTag("root")).assert(hasStartActivityClickAction(clickIntent))
        }

    /** Title and value are plain views inside the Glance tree, so they are read after inflating. */
    private fun renderedTexts(state: WidgetUiState): List<String> {
        val remoteViews =
            runBlocking {
                GlanceRemoteViews().compose(context, DpSize(60.dp, 60.dp)) {
                    DaysCounterWidgetContent(state, onClick = anyClick)
                }
            }.remoteViews
        return buildList { collectTexts(remoteViews.apply(context, FrameLayout(context)), this) }
    }

    private fun collectTexts(
        view: View,
        into: MutableList<String>,
    ) {
        if (view is TextView) into += view.text.toString()
        if (view is ViewGroup) (0 until view.childCount).forEach { collectTexts(view.getChildAt(it), into) }
    }
}
