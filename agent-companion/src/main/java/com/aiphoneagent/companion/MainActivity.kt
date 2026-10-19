package com.aiphoneagent.companion

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.aiphoneagent.accessibility.PermissionHelper
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(Modifier.fillMaxSize(), color = Color(0xFF0E1422)) {
                    CompanionScreen(
                        onOpenAccessibility = { startActivity(PermissionHelper.accessibilitySettingsIntent()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompanionScreen(onOpenAccessibility: () -> Unit) {
    val context = LocalContext.current
    var accessibilityOn by remember { mutableStateOf(false) }
    var serverOn by remember { mutableStateOf(false) }
    var port by remember { mutableStateOf(8765) }

    LaunchedEffect(Unit) {
        while (true) {
            accessibilityOn = PermissionHelper.isAccessibilityEnabled(context)
            serverOn = CompanionServer.isRunning
            port = CompanionServer.port
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1422))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("AI Phone Agent Companion", style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Text(
            "Отдельное приложение-компаньон. Содержит службу спецвозможностей и локальный " +
                "MCP-сервер, чтобы основное приложение (и внешние AI-клиенты) могли управлять телефоном.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF9FB0C9),
        )

        InfoCard {
            Text("1. Служба спецвозможностей", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(
                if (accessibilityOn) {
                    "Включена — агент читает экран и выполняет действия."
                } else {
                    "Выключена — включите, чтобы управлять телефоном."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF9FB0C9),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(onClick = onOpenAccessibility) { Text("Открыть настройки") }
            }
        }

        InfoCard {
            Text("2. Локальный MCP-сервер", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(
                if (serverOn) "Запущен: http://127.0.0.1:$port/mcp" else "Остановлен.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF9FB0C9),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Button(
                    onClick = {
                        if (serverOn) CompanionServer.stop(context) else CompanionServer.start(context)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (serverOn) Color(0xFFB3261E) else Color(0xFF1FB6A6),
                    ),
                ) { Text(if (serverOn) "Остановить" else "Запустить") }
            }
        }

        InfoCard {
            Text("3. Подключение из Claude Desktop / Cursor", style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(
                "Добавьте MCP-сервер (через mcp-remote):",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF9FB0C9),
            )
            Text(
                "npx -y mcp-remote http://127.0.0.1:$port/mcp",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF7FE0D2),
                fontFamily = FontFamily.Monospace,
            )
            Text(
                "Доступные инструменты: get_screen, tap, set_text, scroll, global_action, " +
                    "launch_app, screenshot, list_apps.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF9FB0C9),
            )
        }

        Text(
            "AI Phone Agent Companion 1.0.0",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF6B7A90),
        )
    }
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF182034))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
