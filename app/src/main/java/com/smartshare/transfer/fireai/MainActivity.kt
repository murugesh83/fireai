package com.smartshare.transfer.fireai

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.platform.LocalContext
import com.smartshare.transfer.fireai.ui.theme.FireAITheme
import com.smartshare.transfer.fireai.data.CloudSttRecorder
import com.smartshare.transfer.fireai.data.GeminiClient
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.smartshare.transfer.fireai.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FireAITheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppContent()
                }
            }
        }
    }
}

@Composable
private fun AppContent() {
    val context = LocalContext.current
    val httpClient = remember {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    val recorder = remember { CloudSttRecorder(context, httpClient, BuildConfig.GCP_API_KEY) }
    val gemini = remember { GeminiClient(httpClient, BuildConfig.GCP_API_KEY) }

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> micGranted = granted }

    MainScreen(
        recorder = recorder,
        gemini = gemini,
        micGranted = micGranted,
        requestMicPermission = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
    )
}