package com.pimorazelvanto.dayscounter.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.WidgetConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DataStoreWidgetConfigRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var repository: DataStoreWidgetConfigRepository

    private val config = WidgetConfig("Urlaub", LocalDate.of(2027, 3, 15), HeaderColor.BLUE)

    @Before
    fun setUp() {
        dataStore =
            PreferenceDataStoreFactory.create(scope = scope) {
                temporaryFolder.newFile("configs.preferences_pb")
            }
        repository = DataStoreWidgetConfigRepository(dataStore)
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `save then load returns same config`() =
        runBlocking {
            repository.save(7, config)

            assertEquals(config, repository.load(7))
        }

    @Test
    fun `load of unknown id returns null`() =
        runBlocking {
            assertNull(repository.load(99))
        }

    @Test
    fun `configs of different ids are isolated`() =
        runBlocking {
            val other = config.copy(title = "Geburtstag", color = HeaderColor.PINK)
            repository.save(1, config)
            repository.save(2, other)

            assertEquals(config, repository.load(1))
            assertEquals(other, repository.load(2))
        }

    @Test
    fun `delete removes only the given id`() =
        runBlocking {
            repository.save(1, config)
            repository.save(2, config)

            repository.delete(1)

            assertNull(repository.load(1))
            assertEquals(config, repository.load(2))
        }

    @Test
    fun `observe emits the config saved while it is being collected`() =
        runBlocking {
            val collecting = CompletableDeferred<Unit>()
            val afterSave =
                async(Dispatchers.IO) {
                    repository
                        .observe(7)
                        .onEach { collecting.complete(Unit) }
                        .drop(1)
                        .first()
                }
            collecting.await()

            repository.save(7, config)

            assertEquals(config, afterSave.await())
        }

    @Test
    fun `corrupt date yields null`() =
        runBlocking {
            repository.save(5, config)
            dataStore.edit { it[stringPreferencesKey("target_date_5")] = "not-a-date" }

            assertNull(repository.load(5))
        }

    @Test
    fun `unknown color name yields null`() =
        runBlocking {
            repository.save(5, config)
            dataStore.edit { it[stringPreferencesKey("color_5")] = "MAUVE" }

            assertNull(repository.load(5))
        }

    @Test
    fun `missing title yields null`() =
        runBlocking {
            repository.save(5, config)
            dataStore.edit { it.remove(stringPreferencesKey("title_5")) }

            assertNull(repository.load(5))
        }
}
