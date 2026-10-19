package com.aiphoneagent.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aiphoneagent.accessibility.PermissionHelper
import com.aiphoneagent.common.TaskRecord
import com.aiphoneagent.common.TaskRepository
import com.aiphoneagent.ui.ErrorBanner
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import javax.inject.Inject
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val tasks: TaskRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val history = tasks.observeAll()
    private val events = Channel<Long>(Channel.BUFFERED)
    val openTask = events.receiveAsFlow()

    fun accessibilityOn(): Boolean = PermissionHelper.isAccessibilityEnabled(context)

    fun send(prompt: String) {
        val text = prompt.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            val id = tasks.create(text, if (text.any { it in 'А'..'я' }) "RU" else "EN")
            events.send(id)
        }
    }
}

@Composable
fun ChatRoute(
    onOpenTask: (Long) -> Unit,
    onSettings: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val history by viewModel.history.collectAsStateWithLifecycle(emptyList())
    LaunchedEffect(viewModel) {
        viewModel.openTask.collect(onOpenTask)
    }
    ChatScreen(
        history = history,
        accessibilityOn = viewModel.accessibilityOn(),
        onSend = viewModel::send,
        onOpen = onOpenTask,
        onSettings = onSettings,
    )
}

@Composable
fun ChatScreen(
    history: List<TaskRecord>,
    accessibilityOn: Boolean,
    onSend: (String) -> Unit,
    onOpen: (Long) -> Unit,
    onSettings: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("AI Phone Agent", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Скажите, что сделать на телефоне",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Настройки")
            }
        }
        if (!accessibilityOn) {
            ErrorBanner(
                "Служба спецвозможностей выключена. Откройте настройки и включите AI Phone Agent — иначе нажатия не сработают.",
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (history.isEmpty()) {
                item { EmptyExamples(onPick = { draft = it }) }
            } else {
                items(history, key = { it.id }) { task ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable { onOpen(task.id) }
                            .padding(14.dp),
                    ) {
                        Text(task.prompt, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            task.status,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Открой настройки Wi-Fi") },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    onSend(draft)
                    draft = ""
                }),
                shape = RoundedCornerShape(16.dp),
                maxLines = 4,
            )
            IconButton(
                onClick = {
                    onSend(draft)
                    draft = ""
                },
                modifier = Modifier.padding(start = 4.dp),
            ) {
                Icon(Icons.Outlined.Send, contentDescription = "Отправить")
            }
        }
    }
}

@Composable
private fun EmptyExamples(onPick: (String) -> Unit) {
    val examples = listOf(
        "Открой YouTube и найди видео котиков",
        "Напиши в WhatsApp маме привет",
        "Открой настройки Wi-Fi",
        "Open Chrome and search Kotlin",
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Попробуйте команду", style = MaterialTheme.typography.titleMedium)
        examples.forEach { example ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onPick(example) }
                    .padding(14.dp),
            ) {
                Text(example, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
