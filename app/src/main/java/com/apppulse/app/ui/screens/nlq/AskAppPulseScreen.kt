package com.apppulse.app.ui.screens.nlq

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apppulse.app.data.collector.DevicePerformanceCollector
import com.apppulse.app.data.collector.DevicePerformanceMetrics
import com.apppulse.app.data.remote.OllamaClient
import com.apppulse.app.data.repository.AppPulseRepository
import com.apppulse.app.domain.nlq.NaturalLanguageQueryEngine
import com.apppulse.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

enum class ChatSender {
    USER, AI
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: ChatSender,
    val text: String,
    val isOllamaGenerated: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskAppPulseScreen(
    repository: AppPulseRepository,
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val snapshots by repository.snapshotsFlow.collectAsState(initial = emptyList())
    val scoreResults by repository.scoreResultsFlow.collectAsState(initial = emptyList())
    val decisions by repository.decisionsFlow.collectAsState(initial = emptyList())

    val scoreMap = remember(scoreResults) { scoreResults.associateBy { it.packageName } }
    val decisionMap = remember(decisions) { decisions.associateBy { it.packageName } }

    val perfCollector = remember { DevicePerformanceCollector(context) }
    var deviceMetrics by remember { mutableStateOf<DevicePerformanceMetrics?>(null) }
    var ollamaStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showEndpointDialog by remember { mutableStateOf(false) }
    var endpointInput by remember { mutableStateOf(OllamaClient.baseUrl) }

    var inputText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                sender = ChatSender.AI,
                text = "Hello! I am your on-device AI performance & security assistant powered by Ollama Qwen. I have direct access to your phone's memory, storage telemetry, and app permission profiles. Ask me anything about your device's health or apps to optimize!",
                isOllamaGenerated = false
            )
        )
    }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            deviceMetrics = perfCollector.collectMetrics()
            ollamaStatus = OllamaClient.checkConnection()
        }
    }

    fun sendMessage(query: String) {
        if (query.isBlank() || isThinking) return
        val userMsg = ChatMessage(sender = ChatSender.USER, text = query.trim())
        messages.add(userMsg)
        inputText = ""
        isThinking = true

        coroutineScope.launch {
            listState.animateScrollToItem(messages.size - 1)

            val aiResponseText: String
            var wasFromOllama = false

            // Assemble device context for Qwen
            val metrics = deviceMetrics ?: withContext(Dispatchers.IO) { perfCollector.collectMetrics() }
            val topImpactApps = scoreResults
                .sortedByDescending { it.impactScore }
                .take(5)
                .joinToString(", ") { "${it.packageName} (${it.impactBand} Impact, Health: ${it.healthScore.toInt()})" }

            val prompt = """
                You are AppPulse AI, an intelligent, objective Android system health and hygiene advisor.
                Device Telemetry:
                - Device Model: ${metrics.deviceModel}
                - OS: ${metrics.androidVersion}
                - RAM Utilization: ${metrics.ramUsagePercent}% (${metrics.usedRamBytes / (1024 * 1024 * 1024)}GB / ${metrics.totalRamBytes / (1024 * 1024 * 1024)}GB)
                - Storage Utilization: ${metrics.storageUsagePercent}% (${metrics.usedStorageBytes / (1024 * 1024 * 1024)}GB / ${metrics.totalStorageBytes / (1024 * 1024 * 1024)}GB)
                - CPU & GPU: ${metrics.cpuPerformanceSummary} | ${metrics.gpuPerformanceSummary}
                - Total Installed Apps: ${snapshots.size}
                - Top Impact Apps: $topImpactApps
                
                User Question: "$query"
                
                Provide a concise, direct, helpful, and non-alarmist answer (maximum 4-5 bullet points or 2 paragraphs). Explain why it matters and give actionable steps.
            """.trimIndent()

            val ollamaResult: String? = withContext(Dispatchers.IO) {
                OllamaClient.generateAnalysis(prompt).getOrNull()
            }

            if (ollamaResult != null && ollamaResult.isNotBlank()) {
                aiResponseText = ollamaResult.trim()
                wasFromOllama = true
            } else {
                // Fallback to offline rule-based natural language intelligence
                val nlqResult = NaturalLanguageQueryEngine.execute(
                    query = query,
                    apps = snapshots,
                    scores = scoreMap,
                    storages = emptyMap(),
                    usages = emptyMap(),
                    permissions = emptyMap(),
                    indicators = emptyMap(),
                    decisions = decisionMap
                )
                aiResponseText = buildString {
                    append(nlqResult.explanation)
                    if (nlqResult.matchedApps.isNotEmpty()) {
                        append("\n\nRelevant Applications:")
                        nlqResult.matchedApps.take(4).forEach { app ->
                            val sc = scoreMap[app.packageName]
                            append("\n• ${app.label} (${app.packageName}) — Health: ${sc?.healthScore?.toInt() ?: 100}/100")
                        }
                    }
                }
            }

            messages.add(
                ChatMessage(
                    sender = ChatSender.AI,
                    text = aiResponseText,
                    isOllamaGenerated = wasFromOllama
                )
            )
            isThinking = false
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryBlue.copy(alpha = 0.2f),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("Ask AppPulse AI", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("Qwen Local Assistant", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Ollama status pill
                    val isConnected = ollamaStatus?.first == true
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isConnected) HealthHealthyBg else HealthAttentionBg,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clickable { showEndpointDialog = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isConnected) HealthHealthy else HealthAttention)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isConnected) "Qwen Ready" else "Ollama Config",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isConnected) HealthHealthy else HealthAttention,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    IconButton(onClick = {
                        messages.clear()
                        messages.add(
                            ChatMessage(
                                sender = ChatSender.AI,
                                text = "Chat cleared! How can I help analyze your apps and device health?",
                                isOllamaGenerated = false
                            )
                        )
                    }) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Clear Chat", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Preset Suggestion Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Why is my RAM high?",
                    "What apps should I review?",
                    "Explain storage usage",
                    "Sensitive permissions risk?",
                    "Is my phone running hot?"
                ).forEach { preset ->
                    SuggestionChip(
                        onClick = { sendMessage(preset) },
                        label = { Text(preset, fontSize = 12.sp) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

            // Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(message = msg)
                }

                if (isThinking) {
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = AccentCyan
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Qwen AI is formulating response...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Bottom Input Row
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask about performance or apps...", fontSize = 14.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        shape = RoundedCornerShape(20.dp),
                        singleLine = false,
                        maxLines = 3
                    )

                    FloatingActionButton(
                        onClick = { sendMessage(inputText) },
                        modifier = Modifier.size(46.dp),
                        shape = CircleShape,
                        containerColor = PrimaryBlue,
                        contentColor = DeepNavy
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }

    // Ollama Configuration Dialog
    if (showEndpointDialog) {
        AlertDialog(
            onDismissRequest = { showEndpointDialog = false },
            title = { Text("Ollama Qwen AI Setup") },
            text = {
                Column {
                    Text(
                        text = "Specify your local Ollama address running Qwen (e.g. qwen2.5-coder:3b):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = endpointInput,
                        onValueChange = { endpointInput = it },
                        label = { Text("Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "• Emulator: http://10.0.2.2:11434\n• Physical Phone: Use PC Wi-Fi IP or ADB reverse tcp:11434 tcp:11434 (http://localhost:11434)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    OllamaClient.baseUrl = endpointInput.trim()
                    coroutineScope.launch {
                        ollamaStatus = OllamaClient.checkConnection()
                        showEndpointDialog = false
                    }
                }) {
                    Text("Save & Connect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndpointDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.sender == ChatSender.USER

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (!isUser) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SmartToy,
                    contentDescription = null,
                    tint = AccentCyan,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (message.isOllamaGenerated) "Qwen 2.5 Coder (Ollama)" else "AppPulse Engine",
                    style = MaterialTheme.typography.labelSmall,
                    color = AccentCyan,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Surface(
            shape = if (isUser) {
                RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
            } else {
                RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
            },
            color = if (isUser) PrimaryBlue else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                text = message.text,
                color = if (isUser) DeepNavy else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        }
    }
}
