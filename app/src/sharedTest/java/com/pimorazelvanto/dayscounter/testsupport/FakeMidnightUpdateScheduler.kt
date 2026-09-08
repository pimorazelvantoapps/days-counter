package com.pimorazelvanto.dayscounter.testsupport

import com.pimorazelvanto.dayscounter.widget.MidnightUpdateScheduler

class FakeMidnightUpdateScheduler : MidnightUpdateScheduler {
    var scheduleCount = 0
    var cancelCount = 0

    override fun schedule() {
        scheduleCount++
    }

    override fun cancel() {
        cancelCount++
    }
}
