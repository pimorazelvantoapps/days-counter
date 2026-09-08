package com.pimorazelvanto.dayscounter.domain

interface MidnightUpdateScheduler {
    fun schedule()

    fun cancel()
}
