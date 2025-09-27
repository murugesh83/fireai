package com.smartshare.transfer.fireai

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material3.TextField
import androidx.compose.ui.tooling.preview.Preview
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
    var textValue by remember { mutableStateOf("") }

    Column(modifier = modifier.padding(16.dp)) {
        Box(modifier = Modifier.fillMaxWidth()) {
            TextField(
                value = textValue,
                onValueChange = { textValue = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 112.dp),
                minLines = 3,
                maxLines = 3,
                label = { Text("Enter text") }
            )

            Button(
                onClick = {
                    Toast.makeText(context, "Answer: ${'$'}textValue", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text("Answar")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = { textValue = "" },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("clean edit test")
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