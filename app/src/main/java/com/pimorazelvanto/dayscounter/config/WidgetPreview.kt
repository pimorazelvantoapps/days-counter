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
import androidx.compose.ui.unit.sp
import com.pimorazelvanto.dayscounter.R
import com.pimorazelvanto.dayscounter.domain.DigitSizeTier
import com.pimorazelvanto.dayscounter.domain.WidgetUiState

private val PREVIEW_SIZE = 72.dp
private val HEADER_PADDING = 4.dp
private val TITLE_TEXT_SIZE = 10.sp
private val VALUE_TEXT_SIZE_LARGE = 22.sp
private val VALUE_TEXT_SIZE_MEDIUM = 17.sp
private val VALUE_TEXT_SIZE_SMALL = 13.sp
private const val SHEET_WEIGHT = 1f

/**
 * Restates the Glance sheet of `widget/DaysCounterWidgetContent.kt` in ordinary Compose,
 * because Glance composables cannot render inside an activity. Both are driven by the same
 * [WidgetUiState]; the shared colour and dimension resources keep most of the design from
 * drifting. The four text sizes above are a second copy, because Compose can read a dp
 * dimension resource but not an sp one.
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
                .clip(RoundedCornerShape(dimensionResource(android.R.dimen.system_app_widget_background_radius)))
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
                fontSize = TITLE_TEXT_SIZE,
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
                fontSize =
                    when (state.sizeTier) {
                        DigitSizeTier.LARGE -> VALUE_TEXT_SIZE_LARGE
                        DigitSizeTier.MEDIUM -> VALUE_TEXT_SIZE_MEDIUM
                        DigitSizeTier.SMALL -> VALUE_TEXT_SIZE_SMALL
                    },
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}
