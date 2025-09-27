package com.smartshare.transfer.fireai

import android.os.Bundle
import android.widget.Toast
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.foundation.BorderStroke
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TextField
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.ui.Alignment
import androidx.compose.foundation.shape.CircleShape
import androidx.core.content.ContextCompat
import com.smartshare.transfer.fireai.ui.theme.FireAITheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FireAITheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var textValue by remember { mutableStateOf("") } // committed transcript
    var partialText by remember { mutableStateOf("") } // live hypothesis
    var selectedAudio by remember { mutableStateOf("system") }

    // Permissions state
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var screenGranted by remember { mutableStateOf(false) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
    }

    val mediaProjectionManager = remember {
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        screenGranted = result.resultCode == Activity.RESULT_OK
    }

    // Speech recognizer setup
    val speechRecognizer = remember { SpeechRecognizer.createSpeechRecognizer(context) }
    val listenIntent = remember {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 60000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 4000)
        }
    }
    var isListening by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        speechRecognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                if (isListening) {
                    speechRecognizer.cancel()
                    speechRecognizer.startListening(listenIntent)
                }
            }
            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val best = matches.first()
                    textValue = (listOf(textValue, best).filter { it.isNotBlank() }).joinToString(" ")
                    partialText = ""
                }
                if (isListening) {
                    speechRecognizer.startListening(listenIntent)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val partials = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!partials.isNullOrEmpty()) {
                    partialText = partials.first()
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        onDispose {
            isListening = false
            speechRecognizer.stopListening()
            speechRecognizer.cancel()
            speechRecognizer.destroy()
        }
    }

    // Auto manage listening for mic modes
    androidx.compose.runtime.LaunchedEffect(selectedAudio, micGranted) {
        val needsMic = selectedAudio == "mic" || selectedAudio == "both"
        if (needsMic && micGranted && !isListening) {
            isListening = true
            speechRecognizer.startListening(listenIntent)
        }
        if (!needsMic && isListening) {
            isListening = false
            speechRecognizer.stopListening()
        }
    }

    Column(modifier = modifier.padding(16.dp)) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AudioOptionIcon(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                contentDesc = "System audio",
                selected = selectedAudio == "system",
                onClick = { selectedAudio = "system" }
            )
            AudioOptionIcon(
                icon = Icons.Filled.Mic,
                contentDesc = "Micro phone",
                selected = selectedAudio == "mic",
                onClick = { selectedAudio = "mic" }
            )
            AudioOptionIcon(
                icon = Icons.Filled.GraphicEq,
                contentDesc = "System/Micro Audio",
                selected = selectedAudio == "both",
                onClick = { selectedAudio = "both" }
            )
            // Mic listening toggle when mic-related modes selected
            Button(onClick = {
                if ((selectedAudio == "mic" || selectedAudio == "both") && micGranted) {
                    if (isListening) {
                        isListening = false
                        speechRecognizer.stopListening()
                    } else {
                        isListening = true
                        speechRecognizer.startListening(listenIntent)
                    }
                } else {
                    Toast.makeText(context, "Microphone permission required", Toast.LENGTH_SHORT).show()
                }
            }) { Text(if (isListening) "Stop" else "Listen") }
        }

        // Permission requirement notice and grant buttons based on selection
        val needsMic = selectedAudio == "mic" || selectedAudio == "both"
        val needsScreen = selectedAudio == "system" || selectedAudio == "both"
        val missingPermission = (needsMic && !micGranted) || (needsScreen && !screenGranted)

        if (missingPermission) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (needsScreen && !screenGranted && !needsMic) {
                    "System audio transcription isn't supported by built-in recognizer"
                } else "Permission required to continue",
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(4.dp))
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (needsMic && !micGranted) {
                    Button(onClick = {
                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }) { Text("Grant mic") }
                }
                if (needsScreen && !screenGranted) {
                    Button(onClick = {
                        screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                    }) { Text("Grant system") }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextField(
            value = (listOf(textValue, partialText).filter { it.isNotBlank() }).joinToString(" "),
            onValueChange = { textValue = it },
            modifier = Modifier
                .fillMaxWidth(),
            minLines = 3,
            maxLines = 3,
            label = { Text("Enter text") }
        )

        Spacer(modifier = Modifier.height(8.dp))

        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(
                onClick = {
                    Toast.makeText(context, "Answer: ${'$'}textValue", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("Answar")
            }

            Button(
                onClick = { textValue = "" }
            ) {
                Text("clean edit test")
            }
        }
    }
}

@Composable
private fun AudioOptionIcon(
    icon: ImageVector,
    contentDesc: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface

    Surface(
        modifier = Modifier
            .size(48.dp)
            .clickable { onClick() },
        shape = CircleShape,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = contentDesc)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    FireAITheme {
        MainScreen()
    }
}