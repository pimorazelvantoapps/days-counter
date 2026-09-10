package com.pimorazelvanto.dayscounter.widget

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.testing.unit.hasStartActivityClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.assertHasTextEqualTo
import androidx.glance.testing.unit.hasTestTag
import androidx.test.core.app.ApplicationProvider
import com.pimorazelvanto.dayscounter.domain.DigitSizeTier
import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.WidgetUiState
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DaysCounterWidgetContentTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val clickIntent = Intent(Intent.ACTION_VIEW)
    private val anyClick = actionStartActivity(clickIntent)

    @Test
    fun `renders title and value`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable {
                DaysCounterWidgetContent(
                    WidgetUiState("Urlaub", "42", DigitSizeTier.ONE_DIGIT, HeaderColor.BLUE, isPlaceholder = false),
                    onClick = anyClick,
                )
            }

            onNode(hasTestTag("title")).assertHasTextEqualTo("Urlaub")
            onNode(hasTestTag("value")).assertHasTextEqualTo("42")
        }

    @Test
    fun `renders placeholder text`() =
        runGlanceAppWidgetUnitTest {
            setContext(context)
            provideComposable {
                DaysCounterWidgetContent(
                    WidgetUiState(
                        "Tage",
                        WidgetUiState.PLACEHOLDER_TEXT,
                        DigitSizeTier.ONE_DIGIT,
                        HeaderColor.RED,
                        isPlaceholder = true,
                    ),
                    onClick = anyClick,
                )
            }

            onNode(hasTestTag("value")).assertHasTextEqualTo(WidgetUiState.PLACEHOLDER_TEXT)
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
}
