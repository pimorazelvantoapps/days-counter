package com.pimorazelvanto.dayscounter.testsupport

import com.pimorazelvanto.dayscounter.AppContainer
import com.pimorazelvanto.dayscounter.data.WidgetConfigRepository
import com.pimorazelvanto.dayscounter.domain.Clock
import com.pimorazelvanto.dayscounter.domain.MidnightUpdateScheduler
import com.pimorazelvanto.dayscounter.domain.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.time.LocalDate

class FakeAppContainer(
    override val clock: Clock = FixedClock(LocalDate.of(2026, 9, 8)),
    override val repository: WidgetConfigRepository = FakeWidgetConfigRepository(),
    override val widgetUpdater: FakeWidgetUpdater = FakeWidgetUpdater(),
    override val midnightUpdateScheduler: FakeMidnightUpdateScheduler = FakeMidnightUpdateScheduler(),
    override val backgroundScope: CoroutineScope = CoroutineScope(Dispatchers.Unconfined),
) : AppContainer
