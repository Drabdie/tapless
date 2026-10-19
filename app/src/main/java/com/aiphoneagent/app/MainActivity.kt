package com.aiphoneagent.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aiphoneagent.agent.AgentRunRoute
import com.aiphoneagent.chat.ChatRoute
import com.aiphoneagent.onboarding.OnboardingRoute
import com.aiphoneagent.scenarios.ScenarioRoute
import com.aiphoneagent.ui.AiAgentTheme
import com.aiphoneagent.ui.LoadingIndicator
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(
    private val prefs: UserPrefs,
) : ViewModel() {
    val onboarded = prefs.onboarded

    fun finishOnboarding() {
        viewModelScope.launch { prefs.setOnboarded(true) }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AiAgentTheme {
                val viewModel: AppViewModel = hiltViewModel()
                val onboarded by produceState<Boolean?>(initialValue = null, viewModel) {
                    viewModel.onboarded.collect { value = it }
                }
                if (onboarded == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                } else {
                    val nav = rememberNavController()
                    val start = remember { if (onboarded == true) "chat" else "onboarding" }
                    NavHost(
                        navController = nav,
                        startDestination = start,
                    ) {
                        composable("onboarding") {
                            OnboardingRoute(
                                onFinished = {
                                    viewModel.finishOnboarding()
                                    nav.navigate("chat") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                },
                            )
                        }
                        composable("chat") {
                            ChatRoute(
                                onOpenTask = { id -> nav.navigate("agent/$id") },
                                onSettings = { nav.navigate("settings") },
                                onScenarios = { nav.navigate("scenarios") },
                            )
                        }
                        composable("agent/{taskId}") {
                            AgentRunRoute(onBack = { nav.popBackStack() })
                        }
                        composable("scenarios") {
                            ScenarioRoute(
                                onRun = { id -> nav.navigate("agent/$id") },
                                onBack = { nav.popBackStack() },
                            )
                        }
                        composable("settings") {
                            SettingsRoute(
                                onBack = { nav.popBackStack() },
                                onOpenApiKey = { nav.navigate("onboarding") },
                            )
                        }
                    }
                }
            }
        }
    }
}
