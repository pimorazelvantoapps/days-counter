package com.pimorazelvanto.dayscounter.data

import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.WidgetConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class WidgetConfigDataStoreTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `a corrupt settings file reads as empty and accepts a new configuration`() =
        runBlocking {
            val file = temporaryFolder.newFile("widget_configs.preferences_pb")
            file.writeBytes(Random(seed = 1).nextBytes(256))
            val repository = DataStoreWidgetConfigRepository(widgetConfigDataStore(scope) { file })
            val config = WidgetConfig("Urlaub", LocalDate.of(2027, 3, 15), HeaderColor.BLUE)

            val beforeSave = repository.load(1)
            repository.save(1, config)

            assertNull(beforeSave)
            assertEquals(config, repository.load(1))
        }
}
