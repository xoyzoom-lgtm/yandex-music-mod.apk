package io.github.xoyzoom.ymmod

import android.app.Application
import android.content.Context

class YmModApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as YmModApp).container
