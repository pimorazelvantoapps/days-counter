package com.pimorazelvanto.dayscounter

import android.app.Application

class DaysCounterApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}
