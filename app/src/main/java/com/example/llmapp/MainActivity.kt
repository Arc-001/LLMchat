package com.example.llmapp

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.llmapp.ui.theme.LLMAppTheme

class MainActivity : ComponentActivity() {
    private val viewModel: LlmViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LLMAppTheme {
                val currentUiState by viewModel.uiState.collectAsState()
                val currentPersonality by viewModel.selectedPersonality.collectAsState()
                val conversationHistory by viewModel.conversationHistory.collectAsState()
                val personalitiesList by remember(viewModel.personalities) { mutableStateOf(viewModel.personalities) }
                val useWebSearch by viewModel.useWebSearch.collectAsState()
                val temperature by viewModel.temperature.collectAsState()
                val topK by viewModel.topK.collectAsState()
                val topP by viewModel.topP.collectAsState()

                LlmAppScreen(
                    uiState = currentUiState,
                    personalities = personalitiesList,
                    selectedPersonality = currentPersonality,
                    conversationHistory = conversationHistory,
                    onPersonalitySelected = { viewModel.updateSelectedPersonality(it) },
                    onSend = { viewModel.generateResponse(it) },
                    useWebSearch = useWebSearch,
                    onUseWebSearchChanged = { viewModel.setUseWebSearch(it) },
                    temperature = temperature,
                    onTemperatureChanged = { viewModel.setTemperature(it) },
                    topK = topK,
                    onTopKChanged = { viewModel.setTopK(it) },
                    topP = topP,
                    onTopPChanged = { viewModel.setTopP(it) },
                    onAddPersonality = { name, prompt -> viewModel.addCustomPersonality(name, prompt) },
                    onRemovePersonality = { viewModel.removeCustomPersonality(it) },
                    onClearHistory = { viewModel.clearConversationHistory() },
                    onReinitializeLlm = { viewModel.reInitializeLlm() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LlmAppScreen(
    uiState: LlmUiState,
    personalities: List<LlmPersonality>,
    selectedPersonality: LlmPersonality,
    conversationHistory: ConversationHistory,
    onPersonalitySelected: (LlmPersonality) -> Unit,
    onSend: (String) -> Unit,
    useWebSearch: Boolean,
    onUseWebSearchChanged: (Boolean) -> Unit,
    temperature: Float,
    onTemperatureChanged: (Float) -> Unit,
    topK: Int,
    onTopKChanged: (Int) -> Unit,
    topP: Float,
    onTopPChanged: (Float) -> Unit,
    onAddPersonality: (String, String) -> Unit,
    onRemovePersonality: (LlmPersonality) -> Unit,
    onClearHistory: () -> Unit,
    onReinitializeLlm: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val context = LocalContext.current
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showAddPersonalityDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState) {
        if (uiState is LlmUiState.Error) {
            Toast.makeText(context, uiState.message, Toast.LENGTH_LONG).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column {
                        Text("LLM Explorer", fontWeight = FontWeight.Bold)
                        Text(
                            text = selectedPersonality.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = { showHistoryDialog = true }) {
                        Icon(Icons.Filled.List, contentDescription = "Conversation History")
                    }
                    IconButton(onClick = { showAddPersonalityDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Personality")
                    }
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        bottomBar = {
            BottomInteractionBar(
                uiState = uiState, 
                inputText = inputText, 
                onInputTextChange = { inputText = it }, 
                onSend = { 
                    onSend(it)
                    inputText = "" // Clear input after sending
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            PersonalitySelector(personalities, selectedPersonality, onPersonalitySelected, onRemovePersonality)

            Spacer(modifier = Modifier.height(16.dp))

            // Main response area
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                )
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxHeight()
                ) {
                    Text(
                        text = "LLM Response",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    
                    val responseText = when (uiState) {
                        is LlmUiState.Initializing -> "🔄 Initializing LLM... Please wait."
                        is LlmUiState.Error -> "❌ Error: ${uiState.message}"
                        is LlmUiState.Ready -> uiState.llmResponse
                        is LlmUiState.Generating -> "🤔 ${uiState.llmResponse}"
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = responseText, 
                            style = MaterialTheme.typography.bodyLarge,
                            color = when (uiState) {
                                is LlmUiState.Error -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    if (showHistoryDialog) {
        ConversationHistoryDialog(
            conversationHistory = conversationHistory,
            onDismiss = { showHistoryDialog = false },
            onClearHistory = onClearHistory
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            useWebSearch = useWebSearch,
            onUseWebSearchChanged = onUseWebSearchChanged,
            temperature = temperature,
            onTemperatureChanged = onTemperatureChanged,
            topK = topK,
            onTopKChanged = onTopKChanged,
            topP = topP,
            onTopPChanged = onTopPChanged,
            onDismiss = { showSettingsDialog = false },
            onReinitializeLlm = onReinitializeLlm
        )
    }

    if (showAddPersonalityDialog) {
        AddPersonalityDialog(
            onDismiss = { showAddPersonalityDialog = false },
            onAddPersonality = { name, prompt -> 
                onAddPersonality(name, prompt)
                showAddPersonalityDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationHistoryDialog(
    conversationHistory: ConversationHistory,
    onDismiss: () -> Unit,
    onClearHistory: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxHeight(0.8f)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Conversation History",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Row {
                        if (conversationHistory.messages.isNotEmpty()) {
                            IconButton(onClick = onClearHistory) {
                                Icon(
                                    Icons.Filled.Clear,
                                    contentDescription = "Clear History",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
                
                Divider()
                
                // Conversation list
                if (conversationHistory.messages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No conversations yet",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        reverseLayout = true, // Show newest first
                        state = rememberLazyListState()
                    ) {
                        items(conversationHistory.messages.reversed()) { message ->
                            ConversationMessageItem(message)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationMessageItem(message: ConversationMessage) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Timestamp and personality
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = message.personality,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = java.text.SimpleDateFormat("MMM dd, HH:mm", java.util.Locale.getDefault())
                    .format(java.util.Date(message.timestamp)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // User message
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "You: ${message.userMessage}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // AI response
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Assistant: ${message.aiResponse}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

@Composable
fun PersonalitySelector(
    personalities: List<LlmPersonality>,
    selectedPersonality: LlmPersonality,
    onPersonalitySelected: (LlmPersonality) -> Unit,
    onRemovePersonality: (LlmPersonality) -> Unit
) {
    var personalityMenuExpanded by remember { mutableStateOf(false) }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "AI Personality", 
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            Box(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { personalityMenuExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedPersonality.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select Personality")
                    }
                }
                DropdownMenu(
                    expanded = personalityMenuExpanded,
                    onDismissRequest = { personalityMenuExpanded = false },
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    personalities.forEach { personality ->
                        DropdownMenuItem(
                            text = { 
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = personality.name,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (personality.isCustom) {
                                        IconButton(
                                            onClick = {
                                                onRemovePersonality(personality)
                                                personalityMenuExpanded = false
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Delete,
                                                contentDescription = "Remove",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            onClick = {
                                onPersonalitySelected(personality)
                                personalityMenuExpanded = false
                            }
                        )
                    }
                }
            }
            
            if (selectedPersonality.systemPrompt.isNotEmpty()) {
                Text(
                    text = selectedPersonality.systemPrompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 8.dp),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomInteractionBar(
    uiState: LlmUiState,
    inputText: String,
    onInputTextChange: (String) -> Unit,
    onSend: (String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 8.dp, // Add some shadow for a floating effect
        color = MaterialTheme.colorScheme.surface // Use surface color for the bar
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .navigationBarsPadding(), // Respect navigation bar insets
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TextField(
                value = inputText,
                onValueChange = onInputTextChange,
                label = { Text("Enter your prompt") },
                modifier = Modifier.weight(1f),
                enabled = uiState !is LlmUiState.Initializing && uiState !is LlmUiState.Error,
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                singleLine = true
            )

            Button(
                onClick = {
                    if (inputText.isNotBlank() && uiState !is LlmUiState.Generating) {
                        onSend(inputText)
                    }
                },
                enabled = inputText.isNotBlank() && (uiState is LlmUiState.Ready || uiState is LlmUiState.Generating),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp), // Remove default padding
                modifier = Modifier.size(48.dp) // Make button circular and sized
            ) {
                if (uiState is LlmUiState.Generating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Filled.Send, contentDescription = "Send Prompt")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    useWebSearch: Boolean,
    onUseWebSearchChanged: (Boolean) -> Unit,
    temperature: Float,
    onTemperatureChanged: (Float) -> Unit,
    topK: Int,
    onTopKChanged: (Int) -> Unit,
    topP: Float,
    onTopPChanged: (Float) -> Unit,
    onDismiss: () -> Unit,
    onReinitializeLlm: () -> Unit
) {
    var tempTemperature by remember { mutableStateOf(temperature) }
    var tempTopK by remember { mutableStateOf(topK.toFloat()) }
    var tempTopP by remember { mutableStateOf(topP) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "LLM Settings", 
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Bold
                )
                
                Divider()

                // Web Search Toggle
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Web Search",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Enable internet search for queries",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = useWebSearch, 
                            onCheckedChange = onUseWebSearchChanged
                        )
                    }
                }

                // Model Parameters Card
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Model Parameters",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        
                        // Temperature
                        Column {
                            Text(
                                "Temperature: ${String.format("%.2f", tempTemperature)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = tempTemperature,
                                onValueChange = { tempTemperature = it },
                                valueRange = 0f..1f,
                                steps = 19
                            )
                            Text(
                                "Controls randomness. Lower = more deterministic",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        
                        Divider()
                        
                        // Top-K
                        Column {
                            Text(
                                "Top-K: ${tempTopK.toInt()}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = tempTopK,
                                onValueChange = { tempTopK = it },
                                valueRange = 1f..100f,
                                steps = 98
                            )
                            Text(
                                "Limits selection to K most likely tokens",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                        
                        Divider()
                        
                        // Top-P
                        Column {
                            Text(
                                "Top-P: ${String.format("%.2f", tempTopP)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Slider(
                                value = tempTopP,
                                onValueChange = { tempTopP = it },
                                valueRange = 0f..1f,
                                steps = 19
                            )
                            Text(
                                "Nucleus sampling. Uses tokens with cumulative probability P",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            onTemperatureChanged(tempTemperature)
                            onTopKChanged(tempTopK.toInt())
                            onTopPChanged(tempTopP)
                            onReinitializeLlm()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Filled.Refresh, 
                            contentDescription = "Apply",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apply")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPersonalityDialog(
    onDismiss: () -> Unit,
    onAddPersonality: (String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var systemPrompt by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Create AI Personality", 
                    style = MaterialTheme.typography.titleLarge, 
                    fontWeight = FontWeight.Bold
                )
                
                Divider()

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Personality Name") },
                    placeholder = { Text("e.g., Helpful Tutor") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                )
                
                OutlinedTextField(
                    value = systemPrompt,
                    onValueChange = { systemPrompt = it },
                    label = { Text("System Prompt") },
                    placeholder = { 
                        Text("Describe how the AI should behave, e.g., 'You are a patient teacher who explains concepts clearly with examples.'")
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    maxLines = 6,
                    supportingText = {
                        Text(
                            "This defines how the AI will respond. Be specific about tone, style, and behavior.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    Button(
                        onClick = {
                            if (name.isNotBlank() && systemPrompt.isNotBlank()) {
                                onAddPersonality(name, systemPrompt)
                            }
                        },
                        enabled = name.isNotBlank() && systemPrompt.isNotBlank(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Filled.Add, 
                            contentDescription = "Add",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create")
                    }
                }
            }
        }
    }
}


// --- Previews ---
@Preview(showBackground = true, name = "LLM Ready State")
@Composable
fun LlmInteractionScreenReadyPreview() {
    LLMAppTheme {
        LlmAppScreen(
            uiState = LlmUiState.Ready(llmResponse = "This is a preview response from the LLM. It can be quite long and should wrap nicely within the designated area, demonstrating how multi-line text is handled."),
            personalities = defaultPersonalities,
            selectedPersonality = defaultPersonalities.first(),
            conversationHistory = ConversationHistory(),
            onPersonalitySelected = {},
            onSend = { },
            useWebSearch = true,
            onUseWebSearchChanged = {},
            temperature = 0.7f,
            onTemperatureChanged = {},
            topK = 40,
            onTopKChanged = {},
            topP = 0.9f,
            onTopPChanged = {},
            onAddPersonality = { _, _ -> },
            onRemovePersonality = {},
            onClearHistory = {},
            onReinitializeLlm = {}
        )
    }
}

@Preview(showBackground = true, name = "Settings Dialog Preview")
@Composable
fun SettingsDialogPreview() {
    LLMAppTheme {
        SettingsDialog(
            useWebSearch = true,
            onUseWebSearchChanged = {},
            temperature = 0.75f,
            onTemperatureChanged = {},
            topK = 64,
            onTopKChanged = {},
            topP = 0.95f,
            onTopPChanged = {},
            onDismiss = {},
            onReinitializeLlm = {}
        )
    }
}

@Preview(showBackground = true, name = "Add Personality Dialog Preview")
@Composable
fun AddPersonalityDialogPreview() {
    LLMAppTheme {
        AddPersonalityDialog(
            onDismiss = {},
            onAddPersonality = { _, _ -> }
        )
    }
}