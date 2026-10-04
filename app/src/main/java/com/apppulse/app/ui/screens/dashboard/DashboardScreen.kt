package com.apppulse.app.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apppulse.app.data.collector.AccessOverviewCollector
import com.apppulse.app.data.collector.AccessOverviewMetrics
import com.apppulse.app.data.collector.DevicePerformanceCollector
import com.apppulse.app.data.collector.DevicePerformanceMetrics
import com.apppulse.app.data.local.entities.AppSnapshotEntity
import com.apppulse.app.data.remote.OllamaClient
import com.apppulse.app.data.repository.AppPulseRepository
import com.apppulse.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatMessage(
    val sender: String, // "user" or "qwen"
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    repository: AppPulseRepository,
    onNavigateToReview: () -> Unit,
    onNavigateToAllApps: () -> Unit,
    onNavigateToNlq: () -> Unit,
    onNavigateToApkScan: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDetail: (String) -> Unit,
    onNavigateToAppTest: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snapshots by repository.snapshotsFlow.collectAsState(initial = emptyList())

    // Collectors
    val perfCollector = remember { DevicePerformanceCollector(context) }
    val accessCollector = remember { AccessOverviewCollector(context) }

    var deviceMetrics by remember { mutableStateOf<DevicePerformanceMetrics?>(null) }
    var accessMetrics by remember { mutableStateOf<AccessOverviewMetrics?>(null) }
    var isRefreshingMetrics by remember { mutableStateOf(false) }

    // Ollama & Chat State
    var ollamaStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showEndpointDialog by remember { mutableStateOf(false) }
    var endpointInput by remember { mutableStateOf(OllamaClient.baseUrl) }

    var chatInput by remember { mutableStateOf("") }
    var isAiReplying by remember { mutableStateOf(false) }
    var chatMessages by remember {
        mutableStateOf(
            listOf(
                ChatMessage(
                    sender = "qwen",
                    text = "Hello! I am AppPulse AI powered by local Qwen 2.5 Coder. I can answer any questions about your device's RAM, storage, or app permission risks based on real-time hardware telemetry."
                )
            )
        )
    }

    // App Picker Search Filter
    var appSearchQuery by remember { mutableStateOf("") }

    // Initial Telemetry Load
    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            deviceMetrics = perfCollector.collectMetrics()
        }
        launch(Dispatchers.IO) {
            accessMetrics = accessCollector.collectAccessSummary()
        }
        launch(Dispatchers.IO) {
            ollamaStatus = OllamaClient.checkConnection()
        }
    }

    fun refreshTelemetry() {
        coroutineScope.launch {
            isRefreshingMetrics = true
            launch(Dispatchers.IO) {
                deviceMetrics = perfCollector.collectMetrics()
            }
            launch(Dispatchers.IO) {
                accessMetrics = accessCollector.collectAccessSummary()
            }
            launch(Dispatchers.IO) {
                ollamaStatus = OllamaClient.checkConnection()
            }
            isRefreshingMetrics = false
        }
    }

    fun sendChatMessage(question: String) {
        if (question.isBlank() || isAiReplying) return
        val currentMetrics = deviceMetrics
        val currentAccess = accessMetrics

        val newMsgs = chatMessages + ChatMessage(sender = "user", text = question)
        chatMessages = newMsgs
        chatInput = ""
        isAiReplying = true

        coroutineScope.launch {
            val promptBuilder = StringBuilder()
            promptBuilder.append("Context from real device telemetry:\n")
            if (currentMetrics != null) {
                val totalRamGb = String.format("%.1f", currentMetrics.totalRamBytes / (1024.0 * 1024.0 * 1024.0))
                val usedRamGb = String.format("%.1f", currentMetrics.usedRamBytes / (1024.0 * 1024.0 * 1024.0))
                val freeRamGb = String.format("%.1f", currentMetrics.availableRamBytes / (1024.0 * 1024.0 * 1024.0))
                val totalStorageGb = String.format("%.1f", currentMetrics.totalStorageBytes / (1024.0 * 1024.0 * 1024.0))
                val usedStorageGb = String.format("%.1f", currentMetrics.usedStorageBytes / (1024.0 * 1024.0 * 1024.0))
                val freeStorageGb = String.format("%.1f", currentMetrics.freeStorageBytes / (1024.0 * 1024.0 * 1024.0))

                promptBuilder.append("- Device: ${currentMetrics.deviceModel}, ${currentMetrics.androidVersion}, ${currentMetrics.cpuCores} CPU Cores\n")
                promptBuilder.append("- RAM: ${usedRamGb} GB used / ${totalRamGb} GB total (${currentMetrics.ramUsagePercent}% load, ${freeRamGb} GB free)\n")
                promptBuilder.append("- Internal Storage: ${usedStorageGb} GB used / ${totalStorageGb} GB total (${currentMetrics.storageUsagePercent}% load, ${freeStorageGb} GB free)\n")
                promptBuilder.append("- Overall Vitality: ${currentMetrics.performanceScore}/100 (${currentMetrics.performanceStatus})\n")
            }
            if (currentAccess != null) {
                promptBuilder.append("- Sensitive Permissions across ${currentAccess.totalAppsAudited} apps:\n")
                promptBuilder.append("  • Location: ${currentAccess.locationAppsCount} apps\n")
                promptBuilder.append("  • Camera: ${currentAccess.cameraAppsCount} apps\n")
                promptBuilder.append("  • Microphone: ${currentAccess.micAppsCount} apps\n")
                promptBuilder.append("  • SMS & Calls: ${currentAccess.smsAppsCount} apps\n")
                promptBuilder.append("  • Contacts: ${currentAccess.contactsAppsCount} apps\n")
            }

            promptBuilder.append("\nUser Question: $question\n")
            promptBuilder.append("Provide a direct, factual, and concise answer based strictly on the real hardware metrics above without hype or alarmism.")

            val result = OllamaClient.generateAnalysis(
                prompt = promptBuilder.toString(),
                systemPrompt = "You are AppPulse AI Assistant powered by Qwen 2.5 Coder. You specialize in Android OS telemetry, memory, storage, and security permissions. Answer factually, clearly, and concisely."
            )

            val replyText = if (result.isSuccess) {
                result.getOrThrow()
            } else {
                "Unable to reach local Ollama instance (${result.exceptionOrNull()?.message ?: "offline"}). Check that Ollama is running with model 'qwen2.5-coder:3b'."
            }

            chatMessages = chatMessages + ChatMessage(sender = "qwen", text = replyText)
            isAiReplying = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryBlue,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Bolt, contentDescription = null, tint = DeepNavy, modifier = Modifier.size(18.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("AppPulse", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { refreshTelemetry() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Telemetry",
                            tint = if (isRefreshingMetrics) PrimaryBlue else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(6.dp))

            // ==========================================
            // 1. MOBILE PHONE PERFORMANCE (FIRST SECTION)
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "1. Mobile Phone Performance",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Real-time hardware & vitality metrics",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                deviceMetrics?.let { metrics ->
                    val statusColor = when (metrics.performanceStatus) {
                        "Optimal" -> HealthHealthy
                        "Good" -> AccentCyan
                        "Moderate" -> HealthFair
                        else -> HealthAttention
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = statusColor.copy(alpha = 0.15f),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = metrics.performanceStatus,
                            color = statusColor,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    val metrics = deviceMetrics

                    if (metrics != null) {
                        // Device Model & Spec header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.PhoneAndroid, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = metrics.deviceModel,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${metrics.androidVersion} • ${metrics.cpuCores} Cores",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Vitality score circle
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${metrics.performanceScore}",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = PrimaryBlue
                                )
                                Text(
                                    text = "Vitality Index",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(18.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        Spacer(modifier = Modifier.height(16.dp))

                        // RAM Usage Row
                        val totalRamGb = String.format("%.1f", metrics.totalRamBytes / (1024.0 * 1024.0 * 1024.0))
                        val usedRamGb = String.format("%.1f", metrics.usedRamBytes / (1024.0 * 1024.0 * 1024.0))
                        val freeRamGb = String.format("%.1f", metrics.availableRamBytes / (1024.0 * 1024.0 * 1024.0))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Memory, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("RAM Memory", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                text = "$usedRamGb GB / $totalRamGb GB (${metrics.ramUsagePercent}%)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (metrics.ramUsagePercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (metrics.ramUsagePercent > 80) HealthAttention else AccentCyan,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$freeRamGb GB headroom available",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Internal Storage Row
                        val totalStorageGb = String.format("%.1f", metrics.totalStorageBytes / (1024.0 * 1024.0 * 1024.0))
                        val usedStorageGb = String.format("%.1f", metrics.usedStorageBytes / (1024.0 * 1024.0 * 1024.0))
                        val freeStorageGb = String.format("%.1f", metrics.freeStorageBytes / (1024.0 * 1024.0 * 1024.0))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Storage, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Internal Storage", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text(
                                text = "$usedStorageGb GB / $totalStorageGb GB (${metrics.storageUsagePercent}%)",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (metrics.storageUsagePercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (metrics.storageUsagePercent > 85) HealthAttention else PrimaryBlue,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$freeStorageGb GB free space remaining",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        // Loading placeholder
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Reading phone hardware telemetry...", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // 2. ACCESS PART OF THE APPLICATIONS (SECOND SECTION)
            // ==========================================
            Column {
                Text(
                    text = "2. Application Access & Permissions",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Aggregated sensitive privileges across installed apps",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            val access = accessMetrics
            if (access != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AccessStatCard(
                        icon = Icons.Default.LocationOn,
                        title = "Location",
                        count = access.locationAppsCount,
                        modifier = Modifier.weight(1f),
                        isSensitive = true
                    )
                    AccessStatCard(
                        icon = Icons.Default.CameraAlt,
                        title = "Camera",
                        count = access.cameraAppsCount,
                        modifier = Modifier.weight(1f),
                        isSensitive = false
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AccessStatCard(
                        icon = Icons.Default.Mic,
                        title = "Microphone",
                        count = access.micAppsCount,
                        modifier = Modifier.weight(1f),
                        isSensitive = true
                    )
                    AccessStatCard(
                        icon = Icons.Default.Chat,
                        title = "SMS & Calls",
                        count = access.smsAppsCount,
                        modifier = Modifier.weight(1f),
                        isSensitive = true
                    )
                    AccessStatCard(
                        icon = Icons.Default.Contacts,
                        title = "Contacts",
                        count = access.contactsAppsCount,
                        modifier = Modifier.weight(1f),
                        isSensitive = false
                    )
                }
            } else {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Auditing application permissions...", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // 3. PERFORMANCE CHAT (THIRD SECTION)
            // ==========================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "3. Performance AI Assistant",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Powered by local Qwen 2.5 Coder (Ollama)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // AI connection status chip
                val isConnected = ollamaStatus?.first == true
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isConnected) HealthHealthyBg else HealthReviewBg,
                    modifier = Modifier.clickable { showEndpointDialog = true }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (isConnected) HealthHealthy else HealthReview)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = if (isConnected) "Qwen Ready" else "Configure AI",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isConnected) HealthHealthy else HealthReview,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Quick Prompt Suggestion Chips
                    Text(
                        text = "Suggested Questions:",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickPromptChip(text = "📊 Analyze device RAM") {
                            sendChatMessage("How is my phone's RAM and memory headroom right now?")
                        }
                        QuickPromptChip(text = "🛡️ Audit app permissions") {
                            sendChatMessage("Summarize the sensitive permission access across installed applications.")
                        }
                        QuickPromptChip(text = "💾 Storage optimization tips") {
                            sendChatMessage("Based on my internal storage headroom, what actions should I take?")
                        }
                        QuickPromptChip(text = "⚡ Battery & stability risks") {
                            sendChatMessage("What factors should I check for battery and background stability?")
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(12.dp))

                    // Chat messages list
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        chatMessages.forEach { msg ->
                            val isUser = msg.sender == "user"
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(
                                        topStart = 14.dp,
                                        topEnd = 14.dp,
                                        bottomStart = if (isUser) 14.dp else 2.dp,
                                        bottomEnd = if (isUser) 2.dp else 14.dp
                                    ),
                                    color = if (isUser) PrimaryBlue else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.widthIn(max = 280.dp)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = if (isUser) "You" else "Qwen AI",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isUser) DeepNavy else AccentCyan
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = msg.text,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isUser) Color.White else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }

                        if (isAiReplying) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Qwen is processing real device metrics...", style = MaterialTheme.typography.labelSmall, color = AccentCyan)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Chat Input Field
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = chatInput,
                            onValueChange = { chatInput = it },
                            placeholder = { Text("Ask about phone performance...", fontSize = 13.sp) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            singleLine = true,
                            enabled = !isAiReplying
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { sendChatMessage(chatInput) },
                            enabled = chatInput.isNotBlank() && !isAiReplying
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (chatInput.isNotBlank() && !isAiReplying) PrimaryBlue else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Send,
                                        contentDescription = "Send",
                                        tint = if (chatInput.isNotBlank() && !isAiReplying) DeepNavy else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==========================================
            // 4. CHOOSE APP FOR TESTING (FOURTH SECTION)
            // ==========================================
            Column {
                Text(
                    text = "4. Choose App for Testing & Analysis",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Select an app to inspect its risk, impact, storage, and parameters",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // App Search Input
            OutlinedTextField(
                value = appSearchQuery,
                onValueChange = { appSearchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search installed app to test...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(14.dp),
                trailingIcon = {
                    if (appSearchQuery.isNotEmpty()) {
                        IconButton(onClick = { appSearchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            val filteredApps = remember(snapshots, appSearchQuery) {
                if (appSearchQuery.isBlank()) {
                    snapshots.take(15) // Show top candidates for immediate selection
                } else {
                    snapshots.filter {
                        it.label.contains(appSearchQuery, ignoreCase = true) ||
                                it.packageName.contains(appSearchQuery, ignoreCase = true)
                    }
                }
            }

            if (filteredApps.isEmpty()) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Apps, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No installed apps matching '$appSearchQuery'", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            } else {
                filteredApps.forEach { app ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onNavigateToAppTest(app.packageName) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Android,
                                        contentDescription = null,
                                        tint = AccentCyan,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = app.label,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = app.packageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Button(
                                onClick = { onNavigateToAppTest(app.packageName) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = DeepNavy, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Test App", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DeepNavy)
                            }
                        }
                    }
                }

                if (snapshots.size > filteredApps.size && appSearchQuery.isBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = onNavigateToAllApps,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("View all ${snapshots.size} installed apps", color = PrimaryBlue)
                    }
                }
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }

    // Ollama Endpoint Configuration Dialog
    if (showEndpointDialog) {
        AlertDialog(
            onDismissRequest = { showEndpointDialog = false },
            title = { Text("Local Ollama Configuration") },
            text = {
                Column {
                    Text(
                        text = "Specify the URL for your local Ollama instance running Qwen (e.g. qwen2.5-coder:3b):",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = endpointInput,
                        onValueChange = { endpointInput = it },
                        label = { Text("Ollama Base URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Default for Android Emulator: http://10.0.2.2:11434\nFor physical phones: Use your PC's local Wi-Fi IP (e.g. http://192.168.1.X:11434)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        OllamaClient.baseUrl = endpointInput.trim()
                        showEndpointDialog = false
                        refreshTelemetry()
                    }
                ) {
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
private fun AccessStatCard(
    icon: ImageVector,
    title: String,
    count: Int,
    modifier: Modifier = Modifier,
    isSensitive: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isSensitive) HealthAttentionBg else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(32.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSensitive) HealthAttention else PrimaryBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Text(
                    text = "$count apps",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickPromptChip(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
