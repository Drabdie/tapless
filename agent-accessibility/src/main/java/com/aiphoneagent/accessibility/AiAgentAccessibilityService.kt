package com.aiphoneagent.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import com.aiphoneagent.common.ScreenSnapshot
import com.aiphoneagent.common.UiTreeLimiter
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

object AccessibilityBridge {
    @Volatile var service: AiAgentAccessibilityService? = null
}

@Singleton
class SnapshotStore @Inject constructor() {
    private val screens = MutableStateFlow<ScreenSnapshot?>(null)
    val screen: StateFlow<ScreenSnapshot?> = screens.asStateFlow()

    fun publish(snapshot: ScreenSnapshot) {
        screens.value = snapshot
    }
}

@AndroidEntryPoint
class AiAgentAccessibilityService : AccessibilityService() {
    @Inject lateinit var store: SnapshotStore
    @Inject lateinit var parser: UiTreeParser

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val ticks = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var collectJob: Job? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        AccessibilityBridge.service = this
        collectJob = scope.launch {
            ticks.debounce(150).collect { publishTree() }
        }
        ticks.tryEmit(Unit)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        ticks.tryEmit(Unit)
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (AccessibilityBridge.service === this) AccessibilityBridge.service = null
        collectJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun publishTree() {
        val root = rootInActiveWindow ?: return
        try {
            val metrics = resources.displayMetrics
            val tree = UiTreeLimiter.pruneAndLimit(parser.parse(root))
            store.publish(
                ScreenSnapshot(
                    packageName = root.packageName?.toString().orEmpty(),
                    root = tree,
                    timestamp = System.currentTimeMillis(),
                    width = metrics.widthPixels,
                    height = metrics.heightPixels,
                ),
            )
        } catch (_: Exception) {
            // The node can be stale between event and read. The next event retries.
        } finally {
            root.recycle()
        }
    }
}
