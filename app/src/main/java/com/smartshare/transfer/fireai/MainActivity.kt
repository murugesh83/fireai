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
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.fillMaxHeight
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
// Removed icon-only options for system/both; cloud-only keeps UI simple
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
import java.util.concurrent.TimeUnit

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

    // Permissions state
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val httpClient = remember { 
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
    }

    // Cloud-only: no screen capture

    var isListening by remember { mutableStateOf(false) }
    var useCloud by remember { mutableStateOf(true) }
    val apiKey = BuildConfig.GCP_API_KEY
    var aiResponse by remember { mutableStateOf("") }
    var aiLoading by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(16.dp)) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = {
                if (micGranted) {
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

        // Mic permission notice (cloud-only)
        if (!micGranted) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Permission required to continue",
                color = MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.height(4.dp))
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("Grant mic") }
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
                    val prompt = (listOf(textValue, partialText).filter { it.isNotBlank() }).joinToString(" ")
                    if (prompt.isBlank()) {
                        Toast.makeText(context, "Enter or speak something first", Toast.LENGTH_SHORT).show()
                    } else {
                        aiLoading = true
                        aiResponse = ""
                        callGemini(
                            httpClient = httpClient,
                            apiKey = apiKey,
                            prompt = prompt,
                            onSuccess = { answer ->
                                aiLoading = false
                                aiResponse = answer
                            },
                            onError = { err ->
                                aiLoading = false
                                aiResponse = "Error: $err"
                            }
                        )
                    }
                }
            ) {
                Text(if (aiLoading) "Thinking…" else "Answar")
            }

            Button(
                onClick = { textValue = ""; partialText = ""; aiResponse = "" }
            ) {
                Text("clean edit test")
            }
        }

        if (aiResponse.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .verticalScroll(scrollState)
                    .padding(8.dp)
            ) {
                Text(aiResponse)
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
                    .url("https://speech.googleapis.com/v1/speech:recognize")
                    .addHeader("x-goog-api-key", apiKey)
                    .post(body)
                    .build()
                try {
                    httpClient.newCall(request).execute().use { resp ->
                        if (!resp.isSuccessful) {
                            // Log STT error for debugging
                            val errBody = resp.body?.string().orEmpty()
                            android.util.Log.e("STT", "HTTP ${resp.code}: $errBody")
                            return@use
                        }
                        val respStr = resp.body?.string().orEmpty()
                        android.util.Log.d("STT", "Response: $respStr")
                        val root = JSONObject(respStr)
                        val results = root.optJSONArray("results")
                        if (results != null && results.length() > 0) {
                            val first = results.getJSONObject(0)
                            val alts = first.optJSONArray("alternatives")
                            if (alts != null && alts.length() > 0) {
                                val transcript = alts.getJSONObject(0).optString("transcript")
                                if (transcript.isNotBlank()) {
                                    android.util.Log.d("STT", "Transcript: $transcript")
                                    onFinal(transcript)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("STT", "Request failed", e)
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

private fun callGemini(
    httpClient: OkHttpClient,
    apiKey: String,
    prompt: String,
    onSuccess: (String) -> Unit,
    onError: (String) -> Unit
) {
    val mediaType = "application/json; charset=utf-8".toMediaType()
    val reqJson = JSONObject().apply {
        put("contents", org.json.JSONArray().apply {
            put(JSONObject().apply {
                put("parts", org.json.JSONArray().apply {
                    put(JSONObject().apply { put("text", prompt) })
                })
            })
        })
    }
    val body: RequestBody = reqJson.toString().toRequestBody(mediaType)
    val request = Request.Builder()
        .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:streamGenerateContent?alt=sse")
        .addHeader("x-goog-api-key", apiKey)
        .post(body)
        .build()
    val mainHandler = Handler(Looper.getMainLooper())
    httpClient.newCall(request).enqueue(object : okhttp3.Callback {
        override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
            mainHandler.post { onError(e.message ?: "request failed") }
        }
        override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
            response.use {
                if (!response.isSuccessful) {
                    val errBody = response.body?.string().orEmpty()
                    var msg = "HTTP ${response.code}"
                    try {
                        val errJson = JSONObject(errBody).optJSONObject("error")
                        val emsg = errJson?.optString("message").orEmpty()
                        if (emsg.isNotBlank()) msg = "$msg: $emsg"
                    } catch (_: Throwable) {}
                    mainHandler.post { onError(msg) }
                    return
                }
                
                // Parse Server-Sent Events stream
                val responseBody = response.body?.source()
                var accumulatedAnswer = ""
                
                try {
                    while (!responseBody?.exhausted()!!) {
                        val line = responseBody.readUtf8Line()
                        if (line == null) break
                        
                        android.util.Log.d("Gemini", "SSE Line: $line")
                        
                        if (line.startsWith("data: ")) {
                            val jsonData = line.substring(6).trim()
                            if (jsonData == "[DONE]") break
                            if (jsonData.isEmpty()) continue
                            
                            try {
                                android.util.Log.d("Gemini", "JSON chunk: $jsonData")
                                val chunk = JSONObject(jsonData)
                                val candidates = chunk.optJSONArray("candidates")
                                if (candidates != null && candidates.length() > 0) {
                                    val content = candidates.getJSONObject(0).optJSONObject("content")
                                    val parts = content?.optJSONArray("parts")
                                    val text = parts?.optJSONObject(0)?.optString("text").orEmpty()
                                    if (text.isNotBlank()) {
                                        accumulatedAnswer += text
                                        android.util.Log.d("Gemini", "Accumulated: $accumulatedAnswer")
                                        mainHandler.post { onSuccess(accumulatedAnswer) }
                                    }
                                }
                            } catch (e: Throwable) {
                                android.util.Log.e("Gemini", "JSON parse error: ${e.message}")
                            }
                        }
                    }
                } catch (t: Throwable) {
                    android.util.Log.e("Gemini", "Stream parse error: ${t.message}")
                    mainHandler.post { onError("stream parse error: ${t.message}") }
                }
                
                if (accumulatedAnswer.isBlank()) {
                    android.util.Log.w("Gemini", "No content received")
                    mainHandler.post { onSuccess("(no answer)") }
                }
            }
        }
    })
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