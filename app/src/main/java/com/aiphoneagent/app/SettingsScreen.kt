package com.aiphoneagent.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aiphoneagent.accessibility.PermissionHelper
import com.aiphoneagent.bubble.BubbleController
import com.aiphoneagent.mcp.McpServer
import com.aiphoneagent.network.ApiKeyStore
import com.aiphoneagent.ui.PrimaryButton
import com.aiphoneagent.ui.SecondaryButton
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsState(
    val mcpRunning: Boolean = false,
    val mcpPort: Int = 8765,
    val bubbleOn: Boolean = false,
    val accessibilityOn: Boolean = false,
    val provider: String = "",
    val model: String = "",
    val companionInstalled: Boolean = false,
    val companionHealthy: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val server: McpServer,
    private val keys: ApiKeyStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.value = SettingsState(
            mcpRunning = server.isRunning,
            mcpPort = server.boundPort(),
            bubbleOn = BubbleController.isRunning,
            accessibilityOn = PermissionHelper.isAccessibilityEnabled(context),
            provider = keys.getProvider().label(),
            model = keys.getModel(),
            companionInstalled = CompanionBridge.isInstalled(context),
            companionHealthy = _state.value.companionHealthy,
        )
        viewModelScope.launch {
            val healthy = CompanionBridge.isServerHealthy()
            _state.value = _state.value.copy(companionHealthy = healthy)
        }
    }

    fun toggleMcp() {
        viewModelScope.launch {
            if (server.isRunning) {
                server.stop()
            } else {
                server.start()
            }
            refresh()
        }
    }

    fun toggleBubble() {
        if (BubbleController.isRunning) {
            BubbleController.stop(context)
        } else if (BubbleController.canDrawOverlays(context)) {
            BubbleController.start(context)
        }
        refresh()
    }

    fun canDrawOverlays(): Boolean = BubbleController.canDrawOverlays(context)
    fun overlayIntent() = BubbleController.overlayPermissionIntent(context)
    fun accessibilityIntent() = PermissionHelper.accessibilitySettingsIntent()
    fun launchCompanion() {
        CompanionBridge.launch(context)
    }
}

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    onOpenApiKey: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    SettingsScreen(
        state = state,
        onBack = onBack,
        onToggleMcp = viewModel::toggleMcp,
        onToggleBubble = viewModel::toggleBubble,
        canDrawOverlays = viewModel::canDrawOverlays,
        onRequestOverlay = { viewModel.overlayIntent().let { viewModel.refresh() } },
        onOpenAccessibility = { viewModel.accessibilityIntent().let { viewModel.refresh() } },
        onOpenApiKey = onOpenApiKey,
        onLaunchCompanion = viewModel::launchCompanion,
    )
}

@Composable
fun SettingsScreen(
    state: SettingsState,
    onBack: () -> Unit,
    onToggleMcp: () -> Unit,
    onToggleBubble: () -> Unit,
    canDrawOverlays: () -> Boolean,
    onRequestOverlay: () -> Unit,
    onOpenAccessibility: () -> Unit,
    onOpenApiKey: () -> Unit,
    onLaunchCompanion: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
            }
            Text("Настройки", style = MaterialTheme.typography.titleLarge)
        }

        SettingsCard(
            title = "Служба спецвозможностей",
            subtitle = if (state.accessibilityOn) {
                "Включена — агент может управлять экраном"
            } else {
                "Выключена — включите, чтобы агент мог нажимать и вводить текст"
            },
            trailing = {
                SecondaryButton(text = "Открыть", onClick = onOpenAccessibility)
            },
        )

        SettingsCard(
            title = "MCP-сервер (управление из Claude Desktop / Cursor)",
            subtitle = if (state.mcpRunning) {
                "Запущен: http://127.0.0.1:${state.mcpPort}/mcp"
            } else {
                "Выключен. Включите, чтобы управлять телефоном из внешних AI-клиентов."
            },
            trailing = {
                Switch(checked = state.mcpRunning, onCheckedChange = { onToggleMcp() })
            },
        )

        SettingsCard(
            title = "Плавающий пузырь",
            subtitle = if (state.bubbleOn) {
                "Показан. Нажмите — откроется агент, долгое нажатие — СТОП."
            } else if (!canDrawOverlays()) {
                "Нужно разрешение «Поверх других приложений»."
            } else {
                "Выключен."
            },
            trailing = {
                Switch(
                    checked = state.bubbleOn,
                    onCheckedChange = {
                        if (!canDrawOverlays()) onRequestOverlay() else onToggleBubble()
                    },
                )
            },
        )

        SettingsCard(
            title = "Приложение-компаньон",
            subtitle = when {
                !state.companionInstalled ->
                    "Не установлено. Отдельное приложение с собственным сервисом спецвозможностей и MCP-сервером (обход лимитов Google Play)."
                state.companionHealthy ->
                    "Установлено, MCP-сервер доступен на 127.0.0.1:8765 — телефон можно вести через компаньон."
                else ->
                    "Установлено. Запустите компаньон и включите в нём сервер."
            },
            trailing = {
                SecondaryButton(text = "Открыть", onClick = onLaunchCompanion)
            },
        )

        SettingsCard(
            title = "Модель ИИ",
            subtitle = "${state.provider} · ${state.model}\nКлюч хранится в зашифрованном виде (Keystore).",
            trailing = {
                SecondaryButton(text = "Настроить", onClick = onOpenApiKey)
            },
        )

        Text(
            "AI Phone Agent 1.0.0 · Управление телефоном через спецвозможности и Vision.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(20.dp),
        )
    }
}

@Composable
private fun SettingsCard(
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            trailing()
        }
    }
}
