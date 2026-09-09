package com.pimorazelvanto.dayscounter.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.compose
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pimorazelvanto.dayscounter.R
import com.pimorazelvanto.dayscounter.config.ConfigActivity
import com.pimorazelvanto.dayscounter.domain.DigitSizeTier
import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.WidgetUiState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalGlanceRemoteViewsApi::class)
@RunWith(AndroidJUnit4::class)
class DaysCounterWidgetRenderTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val widgetSize = DpSize(60.dp, 60.dp)

    @Test
    fun configuredContentInflatesWithTitleAndValue() =
        runBlocking {
            val state = WidgetUiState("Urlaub", "42", DigitSizeTier.LARGE, HeaderColor.BLUE, isPlaceholder = false)
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

            val defaultTitle = context.getString(R.string.default_title)
            assertTrue("texts were $texts", defaultTitle in texts && WidgetUiState.PLACEHOLDER_TEXT in texts)
        }

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
}
