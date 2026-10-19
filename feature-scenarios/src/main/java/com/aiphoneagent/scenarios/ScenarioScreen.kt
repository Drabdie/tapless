package com.aiphoneagent.scenarios

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.aiphoneagent.common.SavedScenario
import com.aiphoneagent.common.ScenarioRepository
import com.aiphoneagent.ui.SecondaryButton
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class ScenarioViewModel @Inject constructor(
    private val repository: ScenarioRepository,
) : ViewModel() {
    val scenarios = repository.observeAll()

    fun create(name: String, prompt: String) {
        if (name.isBlank() || prompt.isBlank()) return
        viewModelScope.launch { repository.create(name, prompt) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repository.delete(id) }
    }
}

@Composable
fun ScenarioRoute(
    onRun: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ScenarioViewModel = hiltViewModel(),
) {
    val scenarios by viewModel.scenarios.collectAsStateWithLifecycle(emptyList())
    ScenarioScreen(
        scenarios = scenarios,
        onRun = onRun,
        onDelete = viewModel::delete,
        onCreate = viewModel::create,
        onBack = onBack,
    )
}

@Composable
fun ScenarioScreen(
    scenarios: List<SavedScenario>,
    onRun: (String) -> Unit,
    onDelete: (Long) -> Unit,
    onCreate: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    var showDialog by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Назад")
            }
            Column(Modifier.weight(1f)) {
                Text("Мои сценарии", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Сохранённые команды в один тап",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { showDialog = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "Добавить сценарий")
            }
        }

        if (scenarios.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Пока нет сценариев", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Сохраните часто используемую команду, например «Напиши маме привет».",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(scenarios, key = { it.id }) { scenario ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(16.dp),
                    ) {
                        Text(scenario.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            scenario.prompt,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            SecondaryButton(
                                text = "Запустить",
                                onClick = { onRun(scenario.prompt) },
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(onClick = { onDelete(scenario.id) }) {
                                Icon(Icons.Outlined.Delete, contentDescription = "Удалить")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        var name by remember { mutableStateOf("") }
        var prompt by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Новый сценарий") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Название") },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("Команда агенту") },
                        minLines = 2,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onCreate(name, prompt)
                    showDialog = false
                }) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) { Text("Отмена") }
            },
        )
    }
}
