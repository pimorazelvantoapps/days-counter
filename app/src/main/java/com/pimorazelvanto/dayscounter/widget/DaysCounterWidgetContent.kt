package com.pimorazelvanto.dayscounter.widget

import androidx.annotation.ColorRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.Action
import androidx.glance.action.clickable
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.pimorazelvanto.dayscounter.R
import com.pimorazelvanto.dayscounter.domain.DigitSizeTier
import com.pimorazelvanto.dayscounter.domain.WidgetUiState

private val TITLE_TEXT_SIZE = 10.sp
private val VALUE_TEXT_SIZE_LARGE = 22.sp
private val VALUE_TEXT_SIZE_MEDIUM = 17.sp
private val VALUE_TEXT_SIZE_SMALL = 13.sp
private val CONTENT_PADDING = 4.dp
private val HEADER_HEIGHT = 18.dp

// FunctionNaming: Compose mandates PascalCase for composables.
// ModifierMissing: the widget content always fills its host cell; a caller-supplied
// GlanceModifier would have nothing meaningful to change.
@Suppress("FunctionNaming", "ModifierMissing")
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
                .cornerRadius(android.R.dimen.system_app_widget_background_radius)
                .clickable(onClick)
                .semantics { testTag = "root" },
    ) {
        Header(state)
        Sheet(state)
    }
}

@Suppress("FunctionNaming") // Compose mandates PascalCase for composables.
@Composable
private fun Header(state: WidgetUiState) {
    Box(
        modifier =
            GlanceModifier
                .fillMaxWidth()
                .height(HEADER_HEIGHT)
                .background(ColorProvider(Color(state.color.argb)))
                .padding(horizontal = CONTENT_PADDING),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = state.title,
            maxLines = 1,
            modifier = GlanceModifier.semantics { testTag = "title" },
            style =
                TextStyle(
                    color = colorResource(R.color.widget_header_text),
                    fontSize = TITLE_TEXT_SIZE,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
        )
    }
}

@Suppress("FunctionNaming") // Compose mandates PascalCase for composables.
@Composable
private fun ColumnScope.Sheet(state: WidgetUiState) {
    val textColor = if (state.isPlaceholder) R.color.widget_placeholder_text else R.color.widget_sheet_text
    Box(
        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = state.valueText,
            maxLines = 1,
            modifier = GlanceModifier.semantics { testTag = "value" },
            style =
                TextStyle(
                    color = colorResource(textColor),
                    fontSize = state.sizeTier.textSize(),
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
        )
    }
}

// Glance 1.2.0 marks the @ColorRes ColorProvider factory @RestrictTo(LIBRARY_GROUP), even though the
// public background(@ColorRes Int) overload builds the very same ResourceColorProvider. Routing the
// resource colours through one helper keeps widget and XML preview on a single palette that
// values-night can override, and confines the suppression to a single line.
@Suppress("RestrictedApi")
private fun colorResource(
    @ColorRes resId: Int,
): ColorProvider = ColorProvider(resId)

private fun DigitSizeTier.textSize(): TextUnit =
    when (this) {
        DigitSizeTier.LARGE -> VALUE_TEXT_SIZE_LARGE
        DigitSizeTier.MEDIUM -> VALUE_TEXT_SIZE_MEDIUM
        DigitSizeTier.SMALL -> VALUE_TEXT_SIZE_SMALL
    }
