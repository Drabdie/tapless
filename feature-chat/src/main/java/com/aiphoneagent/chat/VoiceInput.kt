package com.aiphoneagent.chat

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/** Thin wrapper over Android's [SpeechRecognizer] driven from Compose. */
class VoiceController(
    private val context: Context,
    private val onResult: (String) -> Unit,
    private val onState: (Boolean) -> Unit,
) {
    private var recognizer: SpeechRecognizer? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        if (!isAvailable()) return
        if (recognizer == null) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                setRecognitionListener(listener)
            }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        onState(true)
        recognizer?.startListening(intent)
    }

    fun stop() {
        recognizer?.stopListening()
        onState(false)
    }

    fun release() {
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = onState(false)
        override fun onError(error: Int) = onState(false)
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onResults(results: Bundle?) {
            val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
            onState(false)
            if (!text.isNullOrBlank()) onResult(text)
        }
    }
}

/** A microphone button that requests RECORD_AUDIO and streams the recognised text. */
@Composable
fun VoiceMicButton(
    onResult: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var listening by remember { mutableStateOf(false) }
    val controller = remember {
        VoiceController(context, onResult) { listening = it }
    }
    DisposableEffect(Unit) { onDispose { controller.release() } }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) controller.start() }

    IconButton(
        onClick = {
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            } else if (listening) {
                controller.stop()
            } else {
                controller.start()
            }
        },
        modifier = modifier,
    ) {
        Icon(
            imageVector = if (listening) Icons.Filled.Mic else Icons.Outlined.Mic,
            contentDescription = if (listening) "Остановить запись" else "Голосовой ввод",
        )
    }
}
