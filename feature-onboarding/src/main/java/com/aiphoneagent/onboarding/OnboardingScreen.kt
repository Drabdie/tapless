package com.aiphoneagent.onboarding

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aiphoneagent.accessibility.PermissionHelper
import com.aiphoneagent.common.AgentResult
import com.aiphoneagent.network.ApiKeyStore
import com.aiphoneagent.network.LlmClientFactory
import com.aiphoneagent.network.LlmMessage
import com.aiphoneagent.network.LlmRequest
import com.aiphoneagent.network.Provider
import com.aiphoneagent.network.ResponseFormat
import com.aiphoneagent.network.Role
import com.aiphoneagent.ui.ErrorBanner
import com.aiphoneagent.ui.LoadingIndicator
import com.aiphoneagent.ui.PrimaryButton
import com.aiphoneagent.ui.SecondaryButton
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val keys: ApiKeyStore,
    private val factory: LlmClientFactory,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val checks = MutableStateFlow("")
    val checkMessage = checks.asStateFlow()
    private val busy = MutableStateFlow(false)
    val checking = busy.asStateFlow()

    fun accessibilityOn(): Boolean = PermissionHelper.isAccessibilityEnabled(context)

    fun openAccessibility() {
        context.startActivity(PermissionHelper.accessibilitySettingsIntent())
    }

    fun save(provider: Provider, model: String, key: String) {
        keys.setProvider(provider)
        if (model.isNotBlank()) keys.setModel(provider, model)
        if (key.isNotBlank()) keys.setKey(provider, key)
    }

    fun test(provider: Provider, model: String, key: String) {
        save(provider, model, key)
        viewModelScope.launch {
            busy.value = true
            val result = factory.create(provider, model.ifBlank { provider.defaultModel() }).complete(
                LlmRequest(
                    model = model.ifBlank { provider.defaultModel() },
                    messages = listOf(LlmMessage(Role.USER, "Reply with the single word pong")),
                    responseFormat = ResponseFormat.TEXT,
                    maxTokens = 16,
                ),
            )
            checks.value = when (result) {
                is AgentResult.Success -> "Ключ работает: ${result.value.content.take(80)}"
                is AgentResult.Failure -> result.message
            }
            busy.value = false
        }
    }
}

@Composable
fun OnboardingRoute(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    var providerName by rememberSaveable { mutableStateOf(Provider.OPENAI.name) }
    var model by rememberSaveable { mutableStateOf(Provider.OPENAI.defaultModel()) }
    var apiKey by rememberSaveable { mutableStateOf("") }
    val message by viewModel.checkMessage.collectAsStateWithLifecycle()
    val checking by viewModel.checking.collectAsStateWithLifecycle()
    val provider = runCatching { Provider.valueOf(providerName) }.getOrDefault(Provider.OPENAI)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Шаг ${page + 1} из 3", color = MaterialTheme.colorScheme.onSurfaceVariant)
        when (page) {
            0 -> {
                Text("Агент для вашего телефона", style = MaterialTheme.typography.headlineMedium)
                Text("Опишите задачу обычными словами. Агент откроет приложение, нажмёт кнопку или введёт текст. Опасные платежи спрашивают подтверждение, банки заблокированы.")
                PrimaryButton("Дальше", onClick = { page = 1 })
            }
            1 -> {
                Text("Разрешения", style = MaterialTheme.typography.headlineMedium)
                Text(if (viewModel.accessibilityOn()) "Служба специальных возможностей включена." else "Нужна служба специальных возможностей. Без неё агент видит команды, но не может нажимать по экрану.")
                Text("Уведомления и оверлей для этой версии не требуются.")
                SecondaryButton("Открыть настройки Accessibility", onClick = { viewModel.openAccessibility() })
                PrimaryButton(if (viewModel.accessibilityOn()) "Дальше" else "Я включу позже", onClick = { page = 2 })
            }
            else -> {
                Text("Ключ модели", style = MaterialTheme.typography.headlineMedium)
                Text("Можно пропустить: без ключа работает локальный разбор команд. С ключом план строит выбранная модель. Ключ хранится только на устройстве.")
                Provider.entries.forEach { item ->
                    SecondaryButton(item.label(), onClick = {
                        providerName = item.name
                        model = item.defaultModel()
                    })
                }
                Text("Сейчас: ${provider.label()}", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Модель") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API-ключ") },
                    singleLine = true,
                )
                if (checking) LoadingIndicator()
                if (message.isNotBlank()) {
                    if (message.startsWith("Ключ работает")) Text(message) else ErrorBanner(message)
                }
                SecondaryButton("Проверить", onClick = { viewModel.test(provider, model, apiKey) })
                PrimaryButton("Начать", onClick = {
                    viewModel.save(provider, model, apiKey)
                    onFinished()
                })
            }
        }
        if (page > 0) {
            SecondaryButton("Назад", onClick = { page -= 1 })
        }
        Text(
            "Установка из этого APK — сторонняя. Google Play ограничивает Accessibility не для помощи людям с инвалидностью.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
