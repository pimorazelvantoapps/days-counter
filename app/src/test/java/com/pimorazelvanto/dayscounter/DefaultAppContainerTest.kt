package com.pimorazelvanto.dayscounter

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DefaultAppContainerTest {
    private val originalHandler = Thread.getDefaultUncaughtExceptionHandler()

    @Volatile
    private var uncaught: Throwable? = null

    @Before
    fun captureUncaughtExceptions() {
        Thread.setDefaultUncaughtExceptionHandler { _, throwable -> uncaught = throwable }
    }

    @After
    fun restoreUncaughtExceptionHandler() {
        Thread.setDefaultUncaughtExceptionHandler(originalHandler)
    }

    @Test
    fun `a failing background job neither reaches the uncaught handler nor stops later jobs`() {
        val container = DefaultAppContainer(ApplicationProvider.getApplicationContext<Context>())
        var laterJobRan = false

        runBlocking {
            container.backgroundScope.launch { throw IOException("disk full") }.join()
            container.backgroundScope.launch { laterJobRan = true }.join()
        }

        assertNull(uncaught)
        assertTrue(laterJobRan)
    }
}
