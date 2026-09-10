package com.pimorazelvanto.dayscounter.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pimorazelvanto.dayscounter.R
import com.pimorazelvanto.dayscounter.domain.WidgetUiState
import com.pimorazelvanto.dayscounter.ui.TextSizes

private val PREVIEW_SIZE = 72.dp

// Deliberately not android.R.dimen.system_app_widget_background_radius, which the widget itself
// uses: that is an absolute 16dp to 28dp depending on the device and would eat most of the edge
// of a box this small. The preview is not shown at the widget's real size, so its corner is not
// the widget's corner either.
private val PREVIEW_CORNER = 12.dp
private val HEADER_PADDING = 4.dp
private const val SHEET_WEIGHT = 1f

/**
 * Restates the Glance sheet of `widget/DaysCounterWidgetContent.kt` in ordinary Compose,
 * because Glance composables cannot render inside an activity. Both are driven by the same
 * [WidgetUiState] and the same [TextSizes]; the shared colour and dimension resources keep the
 * rest of the design from drifting. Only [HEADER_PADDING], a dp constant, is still duplicated
 * against the Glance file's `CONTENT_PADDING` - the KEEP IN SYNC block there lists it.
 */
@Composable
fun WidgetPreview(
    state: WidgetUiState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .size(PREVIEW_SIZE)
                .clip(RoundedCornerShape(PREVIEW_CORNER))
                .background(colorResource(R.color.widget_sheet_background)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(dimensionResource(R.dimen.widget_header_height))
                    .background(Color(state.color.argb))
                    .padding(horizontal = HEADER_PADDING),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = state.title,
                color = colorResource(R.color.widget_header_text),
                fontSize = TextSizes.TITLE,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(SHEET_WEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            val textColor =
                if (state.isPlaceholder) R.color.widget_placeholder_text else R.color.widget_sheet_text
            Text(
                text = state.valueText,
                color = colorResource(textColor),
                fontSize = TextSizes.value(state.sizeTier),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}
