package com.pimorazelvanto.dayscounter.config

import com.pimorazelvanto.dayscounter.domain.HeaderColor
import com.pimorazelvanto.dayscounter.domain.WidgetConfig
import com.pimorazelvanto.dayscounter.testsupport.ControlledClock
import com.pimorazelvanto.dayscounter.testsupport.FakeMidnightUpdateScheduler
import com.pimorazelvanto.dayscounter.testsupport.FakeWidgetConfigRepository
import com.pimorazelvanto.dayscounter.testsupport.FakeWidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ConfigViewModelTest {
    private val today = LocalDate.of(2026, 9, 8)
    private val repository = FakeWidgetConfigRepository()
    private val widgetUpdater = FakeWidgetUpdater()
    private val scheduler = FakeMidnightUpdateScheduler()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(appWidgetId: Int = 7) =
        ConfigViewModel(
            appWidgetId,
            repository,
            ControlledClock(today),
            widgetUpdater,
            scheduler,
            defaultTitle = "Tage",
        )

    @Test
    fun `new widget starts with default title, no date and default color`() {
        val state = viewModel().uiState.value

        assertEquals("Tage", state.title)
        assertNull(state.targetDate)
        assertEquals(HeaderColor.DEFAULT, state.color)
        assertEquals(today, state.today)
        assertFalse(state.isValid)
    }

    @Test
    fun `existing config is loaded as start state`() {
        repository.saved[7] = WidgetConfig("Urlaub", today.plusDays(10), HeaderColor.TEAL)

        val state = viewModel().uiState.value

        assertEquals("Urlaub", state.title)
        assertEquals(today.plusDays(10), state.targetDate)
        assertEquals(HeaderColor.TEAL, state.color)
    }

    @Test
    fun `unreadable storage leaves the screen on its defaults and raises nothing`() {
        repository.saved[7] = WidgetConfig("Urlaub", today.plusDays(10), HeaderColor.TEAL)
        repository.failOnRead = true
        // The unconfined dispatcher runs the load on this thread, so an exception escaping the
        // coroutine is handed to this thread's uncaught exception handler: the same hand-off that
        // ends the process on a device.
        val uncaught = mutableListOf<Throwable>()
        val thread = Thread.currentThread()
        val previousHandler = thread.uncaughtExceptionHandler
        thread.setUncaughtExceptionHandler { _, throwable -> uncaught += throwable }

        val state =
            try {
                viewModel().uiState.value
            } finally {
                thread.setUncaughtExceptionHandler(previousHandler)
            }

        assertEquals(emptyList<Throwable>(), uncaught)
        assertEquals("Tage", state.title)
        assertNull(state.targetDate)
        assertEquals(HeaderColor.DEFAULT, state.color)
    }

    @Test
    fun `title is truncated to max length`() {
        val vm = viewModel()

        vm.onTitleChanged("a".repeat(30))

        assertEquals(ConfigViewModel.MAX_TITLE_LENGTH, vm.uiState.value.title.length)
    }

    @Test
    fun `save persists, updates widgets and schedules alarm`() {
        val vm = viewModel()
        vm.onTitleChanged("Urlaub")
        vm.onTargetDateChanged(today.plusDays(5))
        vm.onColorChanged(HeaderColor.GREEN)

        vm.save()

        assertEquals(WidgetConfig("Urlaub", today.plusDays(5), HeaderColor.GREEN), repository.saved[7])
        assertEquals(1, widgetUpdater.updateCount)
        assertEquals(1, scheduler.scheduleCount)
        assertEquals(SaveState.Saved, vm.uiState.value.saveState)
    }

    @Test
    fun `blank title is saved as default title`() {
        val vm = viewModel()
        vm.onTitleChanged("  ")
        vm.onTargetDateChanged(today.plusDays(5))

        vm.save()

        assertEquals("Tage", repository.saved[7]?.title)
    }

    @Test
    fun `save without valid date does nothing`() {
        val vm = viewModel()
        vm.onTargetDateChanged(today)

        vm.save()

        assertTrue(repository.saved.isEmpty())
        assertEquals(0, widgetUpdater.updateCount)
        assertEquals(0, scheduler.scheduleCount)
        assertEquals(SaveState.Idle, vm.uiState.value.saveState)
    }

    @Test
    fun `failed save reports failure and can be acknowledged`() {
        repository.failOnSave = true
        val vm = viewModel()
        vm.onTargetDateChanged(today.plusDays(1))

        vm.save()
        assertEquals(SaveState.Failed, vm.uiState.value.saveState)
        assertEquals(0, widgetUpdater.updateCount)
        assertEquals(0, scheduler.scheduleCount)

        vm.onSaveFailureShown()
        assertEquals(SaveState.Idle, vm.uiState.value.saveState)
    }

    @Test
    fun `second save while one is already in flight is ignored`() {
        // A StandardTestDispatcher defers the launched coroutine instead of running it eagerly,
        // so the second save() call genuinely observes saveState still at Saving.
        val dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val vm = viewModel()
        vm.onTargetDateChanged(today.plusDays(5))

        vm.save()
        vm.save()
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, widgetUpdater.updateCount)
        assertEquals(1, scheduler.scheduleCount)
        assertEquals(SaveState.Saved, vm.uiState.value.saveState)
    }

    @Test
    fun `refreshToday invalidates a date that became today`() {
        val clock = ControlledClock(today)
        val vm = ConfigViewModel(7, repository, clock, widgetUpdater, scheduler, "Tage")
        vm.onTargetDateChanged(today.plusDays(1))
        assertTrue(vm.uiState.value.isValid)

        clock.moveTo(today.plusDays(1))
        vm.refreshToday()

        assertFalse(vm.uiState.value.isValid)
    }
}
