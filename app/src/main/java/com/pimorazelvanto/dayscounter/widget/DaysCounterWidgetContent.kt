package com.pimorazelvanto.dayscounter.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.unit.ColorProvider
import com.pimorazelvanto.dayscounter.R
import com.pimorazelvanto.dayscounter.domain.WidgetUiState

private val CONTENT_PADDING = 4.dp

// KEEP IN SYNC WITH the two other renderings of this design:
//   res/layout/widget_preview.xml  the static launcher preview, restated in view XML; it cannot
//                                  read Kotlin, so it keeps its own copy of the text sizes
//   config/WidgetPreview.kt        the Compose mirror on the configuration screen, which cannot
//                                  use Glance because Glance does not render inside an activity;
//                                  it shares com.pimorazelvanto.dayscounter.ui.TextSizes with
//                                  WidgetTextViews.kt, so the text sizes cannot drift from each other
// What can still drift, and where:
//   the palette in WidgetTextViews.kt  <-> @color/widget_header_text, @color/widget_sheet_text and
//                                          @color/widget_placeholder_text, plus values-night
//   TextSizes.TITLE               <-> @dimen/widget_title_text_size
//   TextSizes.value(TWO_DIGITS)   <-> @dimen/widget_value_text_size_two_digits
//   CONTENT_PADDING         <-> WidgetPreview.HEADER_PADDING
// @dimen/widget_header_height and @color/widget_sheet_background are read as resources by all
// three renderings and cannot drift.

// The widget content always fills its host cell; a caller-supplied GlanceModifier
// would have nothing meaningful to change.
@Suppress("ModifierMissing")
@Composable
fun DaysCounterWidgetContent(
    state: WidgetUiState,
    onClick: Action,
) {
    Column(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .background(R.color.widget_sheet_background)
                .appWidgetBackground()
                .cornerRadius(android.R.dimen.system_app_widget_background_radius)
                .clickable(onClick)
                .semantics { testTag = "root" },
    ) {
        Header(state)
        Sheet(state)
    }
}

@Composable
private fun Header(state: WidgetUiState) {
    Box(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .height(R.dimen.widget_header_height)
                .background(ColorProvider(Color(state.color.argb)))
                .padding(horizontal = CONTENT_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        AndroidRemoteViews(
            titleRemoteViews(LocalContext.current, state.title),
            modifier = GlanceModifier.fillMaxWidth().semantics { testTag = "title" },
        )
    }
}

@Composable
private fun ColumnScope.Sheet(state: WidgetUiState) {
    Box(
        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        contentAlignment = Alignment.Center,
    ) {
        AndroidRemoteViews(
            valueRemoteViews(LocalContext.current, state),
            modifier = GlanceModifier.fillMaxWidth().semantics { testTag = "value" },
        )
    }
}
