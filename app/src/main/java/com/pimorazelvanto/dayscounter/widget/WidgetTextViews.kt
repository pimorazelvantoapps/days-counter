package com.pimorazelvanto.dayscounter.widget

import android.content.Context
import android.util.TypedValue
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import androidx.compose.ui.unit.Dp
import com.pimorazelvanto.dayscounter.R
import com.pimorazelvanto.dayscounter.domain.WidgetUiState
import com.pimorazelvanto.dayscounter.ui.TextSizes

// The widget palette. Literals rather than colour resources because the launcher process renders
// the widget and may resolve a resource against its own configuration instead of ours, which gets
// night mode silently wrong; setColorInt below picks day or night at the moment the launcher
// applies the views. KEEP IN SYNC WITH @color/widget_header_text, @color/widget_sheet_text and
// @color/widget_placeholder_text (plus their values-night overrides), which the two previews read.
internal const val HEADER_TEXT = 0xFFFFFFFF.toInt()
internal const val SHEET_TEXT_DAY = 0xFF212121.toInt()
internal const val SHEET_TEXT_NIGHT = 0xFFF5F5F5.toInt()
internal const val PLACEHOLDER_TEXT_DAY = 0xFF9E9E9E.toInt()
internal const val PLACEHOLDER_TEXT_NIGHT = 0xFF757575.toInt()

/**
 * Plain views rather than Glance `Text`, because Glance sizes text only in sp: in the fixed 1x1
 * cell, a title and day count that grew with the system font size were cut short or replaced by
 * an ellipsis. A view's size can be set in dp, which the font size leaves alone.
 */
internal fun titleRemoteViews(
    context: Context,
    title: String,
): RemoteViews = textRemoteViews(context, R.layout.widget_title_text, title, TextSizes.TITLE, HEADER_TEXT, HEADER_TEXT)

internal fun valueRemoteViews(
    context: Context,
    state: WidgetUiState,
): RemoteViews {
    val (day, night) =
        if (state.isPlaceholder) PLACEHOLDER_TEXT_DAY to PLACEHOLDER_TEXT_NIGHT else SHEET_TEXT_DAY to SHEET_TEXT_NIGHT
    return textRemoteViews(
        context,
        R.layout.widget_value_text,
        state.valueText,
        TextSizes.value(state.sizeTier),
        day,
        night,
    )
}

@Suppress("LongParameterList") // Each parameter is one property of the same text view.
private fun textRemoteViews(
    context: Context,
    @LayoutRes layout: Int,
    text: String,
    size: Dp,
    dayColor: Int,
    nightColor: Int,
): RemoteViews =
    RemoteViews(context.packageName, layout).apply {
        setTextViewText(R.id.widget_text, text)
        setTextViewTextSize(R.id.widget_text, TypedValue.COMPLEX_UNIT_DIP, size.value)
        setColorInt(R.id.widget_text, "setTextColor", dayColor, nightColor)
    }
