package com.pimorazelvanto.dayscounter.widget

import android.content.Context
import android.widget.FrameLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import com.pimorazelvanto.dayscounter.domain.DigitSizeTier
import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.WidgetUiState
import com.pimorazelvanto.dayscounter.ui.TextSizes
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetTextViewsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val state = WidgetUiState("Urlaub", "42", DigitSizeTier.TWO_DIGITS, HeaderColor.BLUE, isPlaceholder = false)

    @Test
    fun `title and value keep their size in dp when the system font is doubled`() {
        RuntimeEnvironment.setFontScale(2f)

        val title = inflate(titleRemoteViews(context, state.title))
        val value = inflate(valueRemoteViews(context, state))

        assertEquals("Urlaub", title.text.toString())
        assertEquals("42", value.text.toString())
        assertEquals(TextSizes.TITLE.value * density(), title.textSize, TOLERANCE_PX)
        assertEquals(TextSizes.value(DigitSizeTier.TWO_DIGITS).value * density(), value.textSize, TOLERANCE_PX)
    }

    @Test
    fun `value takes the day colour in light mode and the night colour in dark mode`() {
        val dayColor = inflate(valueRemoteViews(context, state)).currentTextColor
        RuntimeEnvironment.setQualifiers("+night")
        val nightColor = inflate(valueRemoteViews(context, state)).currentTextColor

        assertEquals(SHEET_TEXT_DAY, dayColor)
        assertEquals(SHEET_TEXT_NIGHT, nightColor)
    }

    @Test
    fun `placeholder value takes the placeholder colour`() {
        val placeholder = state.copy(valueText = WidgetUiState.PLACEHOLDER_TEXT, isPlaceholder = true)

        val value = inflate(valueRemoteViews(context, placeholder))

        assertEquals(PLACEHOLDER_TEXT_DAY, value.currentTextColor)
    }

    private fun inflate(remoteViews: RemoteViews): TextView {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        return remoteViews.apply(appContext, FrameLayout(appContext)) as TextView
    }

    private fun density(): Float =
        ApplicationProvider
            .getApplicationContext<Context>()
            .resources.displayMetrics.density

    private companion object {
        const val TOLERANCE_PX = 0.01f
    }
}
