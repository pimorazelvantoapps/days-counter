package com.pimorazelvanto.dayscounter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.pimorazelvanto.dayscounter.data.DataStoreWidgetConfigRepository
import com.pimorazelvanto.dayscounter.domain.SystemClock
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DaysCounterApplicationTest {
    @Test
    fun `application exposes a default container`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val container = context.appContainer

        assertTrue(container.clock is SystemClock)
        assertTrue(container.repository is DataStoreWidgetConfigRepository)
    }

    @Test
    fun `container can be replaced for tests`() {
        val application = ApplicationProvider.getApplicationContext<DaysCounterApplication>()
        val fake =
            com.pimorazelvanto.dayscounter.testsupport
                .FakeAppContainer()

        application.container = fake

        assertSame(fake, application.appContainer)
    }
}
