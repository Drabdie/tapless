package com.aiphoneagent.app

import android.app.Application
import com.aiphoneagent.common.AppDispatchers
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers

@HiltAndroidApp
class AiAgentApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppDispatchers.main = Dispatchers.Main.immediate
        AppDispatchers.io = Dispatchers.IO
        AppDispatchers.default = Dispatchers.Default
    }
}
