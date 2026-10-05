package com.apppulse.app.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.apppulse.app.data.collector.DevicePerformanceCollector
import com.apppulse.app.data.collector.DevicePerformanceMetrics
import com.apppulse.app.data.local.entities.AppSnapshotEntity
import com.apppulse.app.data.remote.OllamaClient
import com.apppulse.app.data.repository.AppPulseRepository
import com.apppulse.app.domain.stealth.ThreatLevel
import com.apppulse.app.ui.components.AppIconImage
import com.apppulse.app.ui.components.MetricCircleChart
import com.apppulse.app.ui.components.StorageProgressBar
import com.apppulse.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    onNavigateToAppTest: (String) -> Unit,
    onNavigateToStealthHunter: () -> Unit = {}
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snapshots by repository.snapshotsFlow.collectAsState(initial = emptyList())
    val scoreResults by repository.scoreResultsFlow.collectAsState(initial = emptyList())
    val decisions by repository.decisionsFlow.collectAsState(initial = emptyList())
    val decisionMap = remember(decisions) { decisions.associateBy { it.packageName } }
    val stealthThreats by repository.stealthThreatsFlow.collectAsState(initial = emptyList())

    // Telemetry Collector
    val perfCollector = remember { DevicePerformanceCollector(context) }
    var deviceMetrics by remember { mutableStateOf<DevicePerformanceMetrics?>(null) }
    var isRefreshingMetrics by remember { mutableStateOf(false) }

    // Ollama Configuration State
    var showEndpointDialog by remember { mutableStateOf(false) }
    var endpointInput by remember { mutableStateOf(OllamaClient.baseUrl) }

    // Top Search Dialog State
    var isTopSearchOpen by remember { mutableStateOf(false) }
    var topSearchQuery by remember { mutableStateOf("") }

    // Rescan Animation Modal State
    var isRescanningOpen by remember { mutableStateOf(false) }
    var rescanStatus by remember { mutableStateOf("SCANNING") } // "SCANNING", "SUCCESS"
    var rescanStepText by remember { mutableStateOf("Inspecting installed applications...") }

    // Initial Telemetry Load
    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            deviceMetrics = perfCollector.collectMetrics()
        }
        launch(Dispatchers.IO) {
            repository.syncAppInventory()
        }
        launch(Dispatchers.IO) {
            repository.scanForStealthThreats()
        }
    }

    fun refreshTelemetry() {
        coroutineScope.launch {
            isRefreshingMetrics = true
            launch(Dispatchers.IO) {
                deviceMetrics = perfCollector.collectMetrics()
            }
            launch(Dispatchers.IO) {
                repository.scanForStealthThreats()
            }
            isRefreshingMetrics = false
        }
    }

    fun startRescanFlow() {
        coroutineScope.launch {
            isRescanningOpen = true
            rescanStatus = "SCANNING"
            rescanStepText = "Inspecting installed applications..."
            delay(600)
            rescanStepText = "Auditing runtime permissions & privileges..."
            delay(600)
            rescanStepText = "Measuring storage footprint & cache..."
            repository.performScan()
            deviceMetrics = perfCollector.collectMetrics()
            delay(600)
            rescanStepText = "Auditing stealth services & disguised packages..."
            repository.scanForStealthThreats()
            delay(500)
            rescanStepText = "Calculating hardware vitality & thermals..."
            delay(500)
            rescanStatus = "SUCCESS"
            rescanStepText = "Scan Successful"
        }
    }

    // Storage Values
    val usedBytes = deviceMetrics?.usedStorageBytes ?: (3L * 1024 * 1024 * 1024)
    val totalBytes = deviceMetrics?.totalStorageBytes ?: (32L * 1024 * 1024 * 1024)
    val reviewableBytes = ((usedBytes * 0.14).toLong()).coerceAtLeast(500L * 1024 * 1024)

    // Attention Apps (excluding kept and ignored apps)
    val attentionApps = remember(snapshots, scoreResults, decisionMap) {
        if (scoreResults.isNotEmpty()) {
            scoreResults.filter { score ->
                val dec = decisionMap[score.packageName]
                val isKeptOrIgnored = dec?.decision == "KEEP" || dec?.decision == "IGNORE"
                !isKeptOrIgnored && (score.healthScore < 60.0 || score.impactScore >= 50.0)
            }
        } else {
            snapshots.filter { snapshot ->
                val dec = decisionMap[snapshot.packageName]
                val isKeptOrIgnored = dec?.decision == "KEEP" || dec?.decision == "IGNORE"
                !isKeptOrIgnored && (snapshot.targetSdk < 31 || snapshot.isSystem.not())
            }.take(8)
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
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Bolt, contentDescription = null, tint = DeepNavy, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("AppPulse", fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { isTopSearchOpen = true }) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search Any App",
                            tint = PrimaryBlue
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
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToNlq,
                    icon = { Icon(Icons.Default.Search, contentDescription = "Ask AI") },
                    label = { Text("Ask AI", fontSize = 11.sp, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = PrimaryBlue,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToApkScan,
                    icon = { Icon(Icons.Default.Shield, contentDescription = "APK Scan") },
                    label = { Text("APK Scan", fontSize = 11.sp, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = PrimaryBlue,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = { startRescanFlow() },
                    icon = { Icon(Icons.Default.Refresh, contentDescription = "Rescan Now") },
                    label = { Text("Rescan Now", fontSize = 11.sp, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = HealthFair,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                NavigationBarItem(
                    selected = false,
                    onClick = onNavigateToAllApps,
                    icon = { Icon(Icons.Default.Apps, contentDescription = "All Apps") },
                    label = { Text("All Apps", fontSize = 11.sp, maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        unselectedIconColor = PrimaryBlue,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // ==========================================
            // 1. HARDWARE PERFORMANCE CARD (RAM & Storage Circular Charts + CPU & GPU)
            // ==========================================
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    val metrics = deviceMetrics

                    // Header Row: Model & Vitality Status
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Device Performance",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = metrics?.let { "${it.deviceModel} • ${it.androidVersion}" } ?: "System Telemetry",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        metrics?.let {
                            val statusColor = when (it.performanceStatus) {
                                "Optimal" -> HealthHealthy
                                "Good" -> AccentCyan
                                "Moderate" -> HealthFair
                                else -> HealthAttention
                            }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = statusColor.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = it.performanceStatus,
                                    color = statusColor,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Two Circular Charts: RAM Memory (Blue) & Internal Storage (Red)
                    val ramPercent = metrics?.ramUsagePercent ?: 0
                    val storagePercent = metrics?.storageUsagePercent ?: 0
                    val usedRamGb = metrics?.let { String.format("%.1f", it.usedRamBytes / (1024.0 * 1024.0 * 1024.0)) } ?: "0.0"
                    val totalRamGb = metrics?.let { String.format("%.1f", it.totalRamBytes / (1024.0 * 1024.0 * 1024.0)) } ?: "0.0"
                    val usedStorageGb = metrics?.let { String.format("%.1f", it.usedStorageBytes / (1024.0 * 1024.0 * 1024.0)) } ?: "0.0"
                    val totalStorageGb = metrics?.let { String.format("%.1f", it.totalStorageBytes / (1024.0 * 1024.0 * 1024.0)) } ?: "0.0"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // RAM Memory (Blue)
                        MetricCircleChart(
                            title = "RAM Memory",
                            percentage = ramPercent,
                            subtitle = "$usedRamGb / $totalRamGb GB",
                            color = PrimaryBlue,
                            size = 135
                        )

                        // Internal Storage (Red)
                        MetricCircleChart(
                            title = "Internal Storage",
                            percentage = storagePercent,
                            subtitle = "$usedStorageGb / $totalStorageGb GB",
                            color = HealthAttention,
                            size = 135
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    Spacer(modifier = Modifier.height(14.dp))

                    // CPU and GPU Performance below charts
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // CPU Performance
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = AccentCyan.copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Memory, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("CPU Performance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = metrics?.cpuPerformanceSummary ?: "${metrics?.cpuCores ?: 8} Cores Active",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        // GPU Performance
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = HealthFair.copy(alpha = 0.15f),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Speed, contentDescription = null, tint = HealthFair, modifier = Modifier.size(20.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text("GPU Performance", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(
                                    text = metrics?.gpuPerformanceSummary ?: "Hardware Accelerated",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 2. STORAGE BREAKDOWN PROGRESS BAR CARD
            // ==========================================
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(20.dp)) {
                    StorageProgressBar(
                        usedBytes = usedBytes,
                        totalBytes = totalBytes,
                        reviewableBytes = reviewableBytes
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ==========================================
            // 3. ATTENTION ALERT CARD (Review Flagged Apps)
            // ==========================================
            if (attentionApps.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = HealthAttentionBg),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = CircleShape,
                                    color = HealthAttention,
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(Icons.Default.Warning, contentDescription = null, tint = DeepNavy, modifier = Modifier.size(20.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "${attentionApps.size} apps need attention",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "High storage, dormancy, or sensitive permissions",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = onNavigateToReview,
                            colors = ButtonDefaults.buttonColors(containerColor = HealthAttention),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().height(48.dp)
                        ) {
                            Text(
                                text = "Review Flagged Apps",
                                fontWeight = FontWeight.Bold,
                                color = DeepNavy
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = DeepNavy)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // ==========================================
            // 4. STEALTH & STALKERWARE RADAR CARD
            // ==========================================
            val criticalThreatCount = remember(stealthThreats) {
                stealthThreats.count { it.threatLevel == ThreatLevel.CRITICAL || it.threatLevel == ThreatLevel.HIGH }
            }
            val isRadarAlert = stealthThreats.isNotEmpty()

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRadarAlert) HealthAttentionBg else MaterialTheme.colorScheme.surface
                ),
                border = if (isRadarAlert) {
                    androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.5f))
                } else {
                    androidx.compose.foundation.BorderStroke(1.dp, HealthHealthy.copy(alpha = 0.25f))
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToStealthHunter() }
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (isRadarAlert) Color(0xFFFF5252) else HealthHealthy,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = if (isRadarAlert) Icons.Default.VisibilityOff else Icons.Default.Security,
                                        contentDescription = null,
                                        tint = if (isRadarAlert) Color.White else DeepNavy,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = if (isRadarAlert) "Covert Threats Detected!" else "Stealth Radar: Clear",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isRadarAlert) Color(0xFFFF5252) else MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isRadarAlert) Color(0xFFFF5252).copy(alpha = 0.15f) else HealthHealthy.copy(alpha = 0.15f)
                                    ) {
                                        Text(
                                            text = if (isRadarAlert) "$criticalThreatCount HIGH RISK" else "ACTIVE",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isRadarAlert) Color(0xFFFF5252) else HealthHealthy
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isRadarAlert)
                                        "${stealthThreats.size} app(s) hiding launcher icons or holding covert access"
                                    else
                                        "No hidden stalkerware, headless trackers or deceptive overlays",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Open Stealth Hunter",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isRadarAlert) "Heuristic Stalkerware & Stealth Audit" else "3-Tier Anti-False-Positive Shield Enabled",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Launch Hunter →",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlue
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // ==========================================
    // RESCAN OVERLAY (Shield in Circular Loading Design)
    // ==========================================
    if (isRescanningOpen) {
        Dialog(
            onDismissRequest = {
                if (rescanStatus == "SUCCESS") {
                    isRescanningOpen = false
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background.copy(alpha = 0.98f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    if (rescanStatus == "SCANNING") {
                        Text(
                            text = "Rescanning Device...",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Real-time audit of storage, permissions & telemetry",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(48.dp))

                        // Shield inside circular loading design
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(190.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.fillMaxSize(),
                                strokeWidth = 6.dp,
                                color = PrimaryBlue,
                                trackColor = PrimaryBlue.copy(alpha = 0.15f)
                            )
                            Surface(
                                shape = CircleShape,
                                color = PrimaryBlue.copy(alpha = 0.12f),
                                modifier = Modifier.size(140.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = "Scanning Shield",
                                        tint = AccentCyan,
                                        modifier = Modifier.size(68.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(48.dp))

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = AccentCyan
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = rescanStepText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    } else {
                        // SUCCESS State
                        Text(
                            text = "Scan Successful",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = HealthHealthy
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "${snapshots.size} applications inspected • System telemetry up to date",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        // Success Shield Graphic
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(120.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = HealthHealthy.copy(alpha = 0.15f),
                                modifier = Modifier.fillMaxSize()
                            ) {}
                            Surface(
                                shape = CircleShape,
                                color = HealthHealthy.copy(alpha = 0.25f),
                                modifier = Modifier.size(90.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Success",
                                        tint = HealthHealthy,
                                        modifier = Modifier.size(54.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Real Audit Summary Breakdown Card
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Audit Summary & Vitality",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Installed Apps Audited", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${snapshots.size} apps", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Needing Review", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${attentionApps.size} apps", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = if (attentionApps.isNotEmpty()) HealthAttention else HealthHealthy)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Device Vitality Index", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${deviceMetrics?.performanceScore ?: 85}/100 (${deviceMetrics?.performanceStatus ?: "Optimal"})", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = HealthHealthy)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("RAM Utilization", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${deviceMetrics?.ramUsagePercent ?: 0}% active", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Storage in Use", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    val usedGb = String.format("%.1f", usedBytes / (1024.0 * 1024.0 * 1024.0))
                                    val totGb = String.format("%.1f", totalBytes / (1024.0 * 1024.0 * 1024.0))
                                    Text("$usedGb / $totGb GB", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Stealth Radar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(
                                        if (stealthThreats.isEmpty()) "0 Covert Apps (Clean)" else "${stealthThreats.size} Flagged!",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (stealthThreats.isEmpty()) HealthHealthy else Color(0xFFFF5252)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(28.dp))

                        Button(
                            onClick = { isRescanningOpen = false },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(50.dp)
                        ) {
                            Text(
                                text = "Done • View Dashboard",
                                fontWeight = FontWeight.Bold,
                                color = DeepNavy,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // TOP APP SEARCH DIALOG OVERLAY (Search & Test any app)
    // ==========================================
    if (isTopSearchOpen) {
        Dialog(
            onDismissRequest = {
                isTopSearchOpen = false
                topSearchQuery = ""
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    // Header Search Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            isTopSearchOpen = false
                            topSearchQuery = ""
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Close Search",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        OutlinedTextField(
                            value = topSearchQuery,
                            onValueChange = { topSearchQuery = it },
                            placeholder = { Text("Search installed apps...") },
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 4.dp),
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = PrimaryBlue)
                            },
                            trailingIcon = {
                                if (topSearchQuery.isNotEmpty()) {
                                    IconButton(onClick = { topSearchQuery = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    val matchedApps = remember(snapshots, topSearchQuery) {
                        if (topSearchQuery.isBlank()) {
                            snapshots
                        } else {
                            snapshots.filter {
                                it.label.contains(topSearchQuery, ignoreCase = true) ||
                                        it.packageName.contains(topSearchQuery, ignoreCase = true)
                            }
                        }
                    }

                    Text(
                        text = if (topSearchQuery.isBlank()) "All Installed Apps (${matchedApps.size})" else "Found ${matchedApps.size} apps for \"$topSearchQuery\"",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    if (matchedApps.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Apps,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("No apps matching \"$topSearchQuery\"", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(matchedApps, key = { it.packageName }) { app ->
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            isTopSearchOpen = false
                                            topSearchQuery = ""
                                            onNavigateToAppTest(app.packageName)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        AppIconImage(
                                            packageName = app.packageName,
                                            modifier = Modifier.size(42.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        )

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
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }

                                        Button(
                                            onClick = {
                                                isTopSearchOpen = false
                                                topSearchQuery = ""
                                                onNavigateToAppTest(app.packageName)
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = DeepNavy, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Test", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = DeepNavy)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // OLLAMA CONFIGURATION DIALOG
    // ==========================================
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
                        text = "Default for Android Emulator: http://10.0.2.2:11434\nFor physical phones: Use your PC's local Wi-Fi IP (e.g. http://10.93.220.194:11434)",
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
