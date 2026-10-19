package com.aiphoneagent.app

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "agent_prefs")

@Singleton
class UserPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val onboardedKey = booleanPreferencesKey("onboarded")
    val onboarded: Flow<Boolean> = context.dataStore.data.map { prefs -> prefs[onboardedKey] ?: false }

    suspend fun setOnboarded(value: Boolean) {
        context.dataStore.edit { prefs -> prefs[onboardedKey] = value }
    }
}
