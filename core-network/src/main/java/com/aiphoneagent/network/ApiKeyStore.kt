package com.aiphoneagent.network

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aiphoneagent.common.SafeLogger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiKeyStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "agent_api_keys",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun setKey(provider: Provider, key: String) {
        SafeLogger.d("ApiKeyStore", "setKey provider=$provider length=${key.length}")
        prefs.edit().putString(keyName(provider), key.trim()).apply()
    }

    fun getKey(provider: Provider): String? = prefs.getString(keyName(provider), null)?.takeIf { it.isNotBlank() }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun setProvider(provider: Provider) {
        prefs.edit().putString("provider", provider.name).apply()
    }

    fun getProvider(): Provider {
        val raw = prefs.getString("provider", Provider.OPENAI.name)
        return runCatching { Provider.valueOf(raw ?: Provider.OPENAI.name) }.getOrDefault(Provider.OPENAI)
    }

    fun setModel(provider: Provider, model: String) {
        prefs.edit().putString(modelName(provider), model.trim()).apply()
    }

    fun getModel(provider: Provider = getProvider()): String {
        return prefs.getString(modelName(provider), null)?.takeIf { it.isNotBlank() } ?: provider.defaultModel()
    }

    private fun keyName(provider: Provider) = "key_${provider.name}"
    private fun modelName(provider: Provider) = "model_${provider.name}"
}
