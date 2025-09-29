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
import androidx.compose.material3.*
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FireAITheme {
                AppContent()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedItem by remember { mutableStateOf(0) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    text = "Menu",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(16.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Home") },
                    selected = selectedItem == 0,
                    onClick = { selectedItem = 0; scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Outlined.Home, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("History") },
                    selected = selectedItem == 1,
                    onClick = { selectedItem = 1; scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Outlined.History, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = selectedItem == 2,
                    onClick = { selectedItem = 2; scope.launch { drawerState.close() } },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
                    modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
                )
            }
        }
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text("FireAI") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Outlined.Menu, contentDescription = "Menu")
                        }
                    }
                )
            }
        ) { innerPadding ->
            MainScreen(
                recorder = recorder,
                gemini = gemini,
                micGranted = micGranted,
                requestMicPermission = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
            )
        }
    }
}