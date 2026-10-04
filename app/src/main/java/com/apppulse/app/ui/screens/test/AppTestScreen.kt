package com.apppulse.app.ui.screens.test

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apppulse.app.data.local.entities.AppSnapshotEntity
import com.apppulse.app.data.remote.OllamaClient
import com.apppulse.app.domain.analyzer.AnalysisParameter
import com.apppulse.app.domain.analyzer.SingleAppAnalysisReport
import com.apppulse.app.domain.analyzer.SingleAppAnalyzer
import com.apppulse.app.ui.components.HealthScoreRing
import com.apppulse.app.ui.components.ImpactBadge
import com.apppulse.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTestScreen(
    app: AppSnapshotEntity,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val analyzer = remember { SingleAppAnalyzer(context) }

    var selectedParams by remember {
        mutableStateOf(
            setOf(
                AnalysisParameter.SECURITY_RISK,
                AnalysisParameter.STORAGE_FOOTPRINT,
                AnalysisParameter.USAGE_DORMANCY,
                AnalysisParameter.STABILITY_BATTERY
            )
        )
    }

    var isAnalyzing by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf<SingleAppAnalysisReport?>(null) }
    var ollamaStatus by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var showEndpointDialog by remember { mutableStateOf(false) }
    var endpointInput by remember { mutableStateOf(OllamaClient.baseUrl) }

    LaunchedEffect(Unit) {
        ollamaStatus = OllamaClient.checkConnection()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Diagnostics", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Ollama status chip
                    val isConnected = ollamaStatus?.first == true
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isConnected) HealthHealthyBg else HealthReviewBg,
                        modifier = Modifier
                            .padding(end = 8.dp)
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
                                    .background(if (isConnected) HealthHealthy else HealthReview)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isConnected) "Qwen Ready" else "Configure AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isConnected) HealthHealthy else HealthReview,
                                fontWeight = FontWeight.Bold
                            )
                        }
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
            Spacer(modifier = Modifier.height(10.dp))

            // Selected App Header Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Android, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(34.dp))
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = app.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(text = app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "Target SDK ${app.targetSdk} • v${app.versionName}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Parameter Selection Section
            Text(
                text = "Choose Parameters to Analyze",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Select which aspects of the application you want to inspect:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            AnalysisParameter.values().forEach { param ->
                val isSelected = selectedParams.contains(param)
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            selectedParams = if (isSelected) {
                                if (selectedParams.size > 1) selectedParams - param else selectedParams
                            } else {
                                selectedParams + param
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { checked ->
                                selectedParams = if (checked) {
                                    selectedParams + param
                                } else {
                                    if (selectedParams.size > 1) selectedParams - param else selectedParams
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = param.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = param.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Start Analysis Button
            Button(
                onClick = {
                    isAnalyzing = true
                    coroutineScope.launch {
                        report = analyzer.analyzeApp(app, selectedParams)
                        isAnalyzing = false
                    }
                },
                enabled = !isAnalyzing && selectedParams.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
            ) {
                if (isAnalyzing) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Qwen AI is analyzing metrics...", fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Run AI Performance & Risk Analysis", fontWeight = FontWeight.Bold, color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Analysis Report Presentation
            AnimatedVisibility(visible = report != null) {
                report?.let { rep ->
                    Column {
                        Text(
                            text = "Analysis Results",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Score Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Health Score", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "${rep.healthScore.toInt()}/100",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (rep.healthScore >= 70) HealthHealthy else HealthReview
                                    )
                                }
                            }

                            Card(
                                shape = RoundedCornerShape(18.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Resource Impact", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "${rep.impactScore.toInt()}/100",
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (rep.impactScore >= 65) HealthAttention else HealthReview
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    ImpactBadge(band = rep.impactBand, dominantDriver = rep.dominantDriver)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Measured Metrics Card
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Real Device Measurements", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(10.dp))

                                rep.storageStats?.let { st ->
                                    val totalMb = st.totalBytes / (1024 * 1024)
                                    val cacheMb = st.cacheBytes / (1024 * 1024)
                                    val dataMb = st.dataBytes / (1024 * 1024)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Storage Footprint", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${totalMb} MB (Data: ${dataMb} MB, Cache: ${cacheMb} MB)", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }

                                rep.usageStats?.let { us ->
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Days Since Last Use", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(if (us.daysSinceLastUse == 0) "Used Today" else "${us.daysSinceLastUse} days ago", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                }

                                val granted = rep.sensitivePermissions.filter { it.granted }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Sensitive Permissions", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${granted.size} granted", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                }
                                if (granted.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        granted.map { it.categoryName }.distinct().joinToString(" • "),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AccentCyan
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // AI Analysis Card (from Qwen / Ollama)
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(18.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Qwen AI Performance Analysis", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (rep.isAiFromOllama) HealthHealthyBg else MaterialTheme.colorScheme.surface
                                ) {
                                    Text(
                                        text = if (rep.isAiFromOllama) "Verified Ollama (qwen2.5-coder:3b)" else "On-Device Offline Engine",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (rep.isAiFromOllama) HealthHealthy else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Text(
                                    text = rep.aiGeneratedAnalysis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    lineHeight = 22.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Quick Action Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", app.packageName, null)
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Text("App Info")
                            }

                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_DELETE).apply {
                                        data = Uri.fromParts("package", app.packageName, null)
                                    }
                                    context.startActivity(intent)
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = HealthAttention)
                            ) {
                                Text("Uninstall", color = Color.White)
                            }
                        }

                        Spacer(modifier = Modifier.height(30.dp))
                    }
                }
            }
        }
    }

    // Ollama Configuration Dialog
    if (showEndpointDialog) {
        AlertDialog(
            onDismissRequest = { showEndpointDialog = false },
            title = { Text("Ollama Qwen AI Connection") },
            text = {
                Column {
                    Text(
                        text = "Enter your Ollama host URL. On Emulator use http://10.0.2.2:11434. On physical device via ADB use http://localhost:11434, or your PC Wi-Fi IP.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = endpointInput,
                        onValueChange = { endpointInput = it },
                        label = { Text("Ollama URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    OllamaClient.baseUrl = endpointInput
                    coroutineScope.launch {
                        ollamaStatus = OllamaClient.checkConnection()
                        showEndpointDialog = false
                    }
                }) {
                    Text("Test & Save")
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
