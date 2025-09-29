package com.smartshare.transfer.fireai.ui

import android.Manifest
import android.widget.Toast
import androidx.annotation.RequiresPermission
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        // Answer area fills available space between bars and input/buttons
        val scrollState = rememberScrollState()
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            androidx.compose.foundation.layout.Box(modifier = Modifier.padding(16.dp)) {
                if (aiResponse.isNotBlank()) {
                    SelectionContainer { Text(aiResponse, style = MaterialTheme.typography.bodyLarge) }
                } else {
                    Text("Answer will appear here…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // Input just above the buttons
        OutlinedTextField(
            value = listOf(textValue, partialText).filter { it.isNotBlank() }.joinToString(" "),
            onValueChange = { textValue = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            minLines = 3,
            maxLines = 3,
            placeholder = { Text("Enter text") },
            leadingIcon = { Icon(imageVector = Icons.Outlined.Mic, contentDescription = null) },
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Bottom buttons row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            FilledTonalButton(
                onClick = {
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
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.filledTonalButtonColors()
            ) {
                Icon(Icons.Outlined.Mic, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text(if (isListening) "Stop" else "Listen")
            }

            FilledTonalButton(
                onClick = {
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
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.filledTonalButtonColors()
            ) {
                Icon(Icons.Outlined.Send, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text(if (aiLoading) "Thinking…" else "Answar")
            }

            FilledTonalButton(
                onClick = { textValue = ""; partialText = ""; aiResponse = "" },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.filledTonalButtonColors()
            ) {
                Icon(Icons.Outlined.Delete, contentDescription = null)
                Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                Text("Clear")
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
