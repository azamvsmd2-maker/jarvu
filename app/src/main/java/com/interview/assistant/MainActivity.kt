package com.interview.assistant

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.material3.Material3RichText
import com.interview.assistant.audio.VoiceDetectionManager
import com.interview.assistant.data.InterviewRepository
import com.interview.assistant.ui.InterviewViewModel
import com.interview.assistant.ui.Message

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = InterviewRepository(applicationContext)
        val voice = VoiceDetectionManager(applicationContext)
        val viewModel = InterviewViewModel(repo, voice)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = Color(0xFF121212),
                    surface = Color(0xFF1E1E1E)
                )
            ) {
                InterviewScreen(viewModel)
            }
        }
    }
}

@Composable
fun InterviewScreen(viewModel: InterviewViewModel) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var textInput by remember { mutableStateOf("") }
    var showApiKeyDialog by remember { mutableStateOf(state.apiKey.isBlank()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) viewModel.toggleListening()
    }

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.answer) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    if (showApiKeyDialog) {
        ApiKeyDialog(
            currentKey = state.apiKey,
            onDismiss = { showApiKeyDialog = false },
            onSave = { newKey ->
                viewModel.saveApiKey(newKey)
                showApiKeyDialog = false
            }
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = Color(0xFF0F0F12),
        topBar = {
            TopAppBar(
                state = state,
                onOpenApiKeyDialog = { showApiKeyDialog = true },
                onToggleListening = {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            )
        },
        bottomBar = {
            InputBar(
                text = textInput,
                onTextChanged = { textInput = it },
                onSend = {
                    viewModel.submitQuestion(textInput)
                    textInput = ""
                }
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(state.messages, key = { it.id }) { message ->
                InterviewCard(message = message)
            }
        }
    }
}

@Composable
fun ApiKeyDialog(currentKey: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var keyInput by remember { mutableStateOf(currentKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF1E2028),
        title = {
            Text("Set Gemini API Key", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "Paste your Google AI Studio Gemini API key. It is stored locally on your device.",
                    color = Color(0xFF8E95A5),
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    placeholder = { Text("AIzaSy...", color = Color(0xFF555865)) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF2F66EE),
                        unfocusedBorderColor = Color(0xFF3A3D4A)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(keyInput) },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F66EE))
            ) {
                Text("Save Key")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF8E95A5))
            }
        }
    )
}

@Composable
fun TopAppBar(
    state: com.interview.assistant.ui.UiState,
    onOpenApiKeyDialog: () -> Unit,
    onToggleListening: () -> Unit
) {
    val indicatorColor by animateColorAsState(
        targetValue = when {
            state.isListening -> Color(0xFF00E676)
            state.isProcessing -> Color(0xFFFF9100)
            else -> Color(0xFF757575)
        }, label = "statusColor"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 8.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = when {
                    state.isListening -> "LISTENING"
                    state.isProcessing -> "GENERATING..."
                    else -> "STANDBY"
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = indicatorColor
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(
                onClick = onOpenApiKeyDialog,
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        if (state.apiKey.isBlank()) Color(0xFFB71C1C) else Color(0xFF26262D),
                        CircleShape
                    )
            ) {
                Text("KEY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            IconButton(
                onClick = onToggleListening,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF26262D), CircleShape)
            ) {
                Text(
                    text = if (state.isListening) "STOP" else "MIC",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}
