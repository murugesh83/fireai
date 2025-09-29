package com.smartshare.transfer.fireai.ui

import android.Manifest
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.smartshare.transfer.fireai.data.CloudSttRecorder
import com.smartshare.transfer.fireai.data.GeminiClient

@Composable
fun MainScreen(
    recorder: CloudSttRecorder,
    gemini: GeminiClient,
    micGranted: Boolean,
    requestMicPermission: () -> Unit,
) {
    val context = LocalContext.current
    var textValue by remember { mutableStateOf("") }
    var partialText by remember { mutableStateOf("") }
    var aiResponse by remember { mutableStateOf("") }
    var aiLoading by remember { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Content column with bottom padding so it stays above buttons
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 16.dp, top = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 0.dp)
        ) {
            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.75f)
                    .verticalScroll(scrollState)
                    .padding(16.dp)
            ) { if (aiResponse.isNotBlank()) Text(aiResponse) }

            TextField(
                value = listOf(textValue, partialText).filter { it.isNotBlank() }.joinToString(" "),
                onValueChange = { textValue = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 0.dp),
                minLines = 3,
                maxLines = 3,
                label = { Text("Enter text") }
            )
        }

        // Bottom buttons aligned to bottom center
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = {
                if (micGranted) {
                    if (isListening) {
                        recorder.stop()
                        isListening = false
                    } else {
                        startListening(recorder, onPartial = { partialText = it }) { final ->
                            textValue = listOf(textValue, final).filter { it.isNotBlank() }.joinToString(" ")
                            partialText = ""
                        }
                        isListening = true
                    }
                } else {
                    Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
                }
            }) { Text(if (isListening) "Stop" else "Listen") }

            Button(onClick = {
                val prompt = listOf(textValue, partialText).filter { it.isNotBlank() }.joinToString(" ")
                if (prompt.isBlank()) {
                    Toast.makeText(context, "Enter or speak something first", Toast.LENGTH_SHORT).show()
                } else {
                    aiLoading = true
                    aiResponse = ""
                    gemini.streamAnswer(
                        prompt = prompt,
                        onChunk = {
                            aiLoading = false
                            aiResponse = it
                        },
                        onError = {
                            aiLoading = false
                            aiResponse = "Error: $it"
                        }
                    )
                }
            }) { Text(if (aiLoading) "Thinking…" else "Answar") }

            Button(onClick = { textValue = ""; partialText = ""; aiResponse = "" }) {
                Text("Clear")
            }
        }

        if (!micGranted) {
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 56.dp)) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = "Permission required to continue", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@RequiresPermission(Manifest.permission.RECORD_AUDIO)
private fun startListening(
    recorder: CloudSttRecorder,
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit
) {
    recorder.start(onPartial = onPartial, onFinal = onFinal)
}
