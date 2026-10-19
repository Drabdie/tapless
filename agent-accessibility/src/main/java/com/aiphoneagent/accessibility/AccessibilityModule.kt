package com.aiphoneagent.accessibility

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import com.aiphoneagent.common.ActionExecutor
import com.aiphoneagent.common.ScreenObserver
import com.aiphoneagent.common.ScreenObserverImpl
import com.aiphoneagent.common.ScreenshotProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AccessibilityModule {
    @Binds
    @Singleton
    abstract fun bindExecutor(impl: AccessibilityActionExecutor): ActionExecutor

    @Binds
    @Singleton
    abstract fun bindScreenshotProvider(impl: AccessibilityScreenshotProvider): ScreenshotProvider

    companion object {
        @Provides
        @Singleton
        fun screenObserver(store: SnapshotStore): ScreenObserver = ScreenObserverImpl(store.screen)
    }
}

object PermissionHelper {
    fun isAccessibilityEnabled(context: Context): Boolean {
        val expected = ComponentName(context, AiAgentAccessibilityService::class.java)
        val setting = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(setting)
        while (splitter.hasNext()) {
            val component = ComponentName.unflattenFromString(splitter.next())
            if (component == expected) return true
        }
        return false
    }

    fun accessibilitySettingsIntent() = android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
}
