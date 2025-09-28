package com.smartshare.transfer.fireai

import android.os.Bundle
import android.widget.Toast
import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
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
import androidx.annotation.RequiresPermission
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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean

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
    val httpClient = remember { OkHttpClient() }

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

    var isListening by remember { mutableStateOf(false) }
    var useCloud by remember { mutableStateOf(true) }
    val apiKey = BuildConfig.GCP_API_KEY

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
            // Mic listening toggle (Cloud only)
            Button(onClick = {
                if ((selectedAudio == "mic" || selectedAudio == "both") && micGranted) {
                    if (isListening) stopCloudMic() else startCloudMic(
                        httpClient = httpClient,
                        apiKey = apiKey,
                        onPartial = { partial -> partialText = partial },
                        onFinal = { final ->
                            textValue = (listOf(textValue, final).filter { it.isNotBlank() }).joinToString(" ")
                            partialText = ""
                        }
                    )
                    isListening = !isListening
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

// --- Simple Google Cloud STT mic capture using short non-streaming requests ---
private var cloudThread: Thread? = null
private var cloudRun = AtomicBoolean(false)
private var cloudRecorder: AudioRecord? = null

@RequiresPermission(Manifest.permission.RECORD_AUDIO)
private fun startCloudMic(
    httpClient: OkHttpClient,
    apiKey: String,
    onPartial: (String) -> Unit,
    onFinal: (String) -> Unit
) {
    if (cloudThread != null) return
    val sampleRate = 16000
    val minBuf = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )
    val bufferSize = maxOf(minBuf, sampleRate /* 1 sec */ * 2)
    val recorder = AudioRecord(
        MediaRecorder.AudioSource.MIC,
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT,
        bufferSize
    )
    cloudRecorder = recorder
    cloudRun.set(true)
    recorder.startRecording()
    cloudThread = Thread {
        try {
            val chunkMs = 2000
            val bytesPerSec = sampleRate * 2
            val targetBytes = bytesPerSec * chunkMs / 1000
            val readBuffer = ByteArray(targetBytes)
            while (cloudRun.get()) {
                var readTotal = 0
                while (readTotal < targetBytes && cloudRun.get()) {
                    val r = recorder.read(readBuffer, readTotal, targetBytes - readTotal)
                    if (r > 0) readTotal += r else Thread.sleep(10)
                }
                if (!cloudRun.get()) break
                onPartial("…")
                val contentB64 = Base64.encodeToString(readBuffer, 0, readTotal, Base64.NO_WRAP)
                val reqJson = JSONObject().apply {
                    put("config", JSONObject().apply {
                        put("encoding", "LINEAR16")
                        put("sampleRateHertz", sampleRate)
                        put("languageCode", "en-US")
                        put("enableAutomaticPunctuation", true)
                    })
                    put("audio", JSONObject().apply {
                        put("content", contentB64)
                    })
                }
                val mediaType = "application/json; charset=utf-8".toMediaType()
                val body: RequestBody = reqJson.toString().toRequestBody(mediaType)
                val request = Request.Builder()
                    .url("https://speech.googleapis.com/v1/speech:recognize?key=$apiKey")
                    .post(body)
                    .build()
                try {
                    httpClient.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful) return@use
                        val respStr = resp.body?.string().orEmpty()
                        val root = JSONObject(respStr)
                        val results = root.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            val first = results.getJSONObject(0)
                            val alts = first.optJSONArray("alternatives")
                            if (alts != null && alts.length() > 0) {
                                val transcript = alts.getJSONObject(0).optString("transcript")
                                if (transcript.isNotBlank()) onFinal(transcript)
                            }
                        }
                    }
                } catch (_: Exception) {
                    // ignore errors for now
                }
                onPartial("")
            }
        } finally {
            try { recorder.stop() } catch (_: Exception) {}
            try { recorder.release() } catch (_: Exception) {}
            cloudRecorder = null
        }
    }.also { it.start() }
}

private fun stopCloudMic() {
    cloudRun.set(false)
    cloudThread?.join(500)
    cloudThread = null
    try { cloudRecorder?.stop() } catch (_: Exception) {}
    try { cloudRecorder?.release() } catch (_: Exception) {}
    cloudRecorder = null
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