package com.apppulse.app.ui.screens.dashboard

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apppulse.app.data.local.entities.AppSnapshotEntity
import com.apppulse.app.data.local.entities.ScoreResultEntity
import com.apppulse.app.data.local.entities.UserDecisionEntity
import com.apppulse.app.data.repository.AppPulseRepository
import com.apppulse.app.domain.ranking.FixFirstRanker
import com.apppulse.app.domain.ranking.RankedIssue
import com.apppulse.app.ui.components.HealthScoreRing
import com.apppulse.app.ui.components.StorageProgressBar
import com.apppulse.app.ui.theme.*
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
    onNavigateToDetail: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val isScanning by repository.isScanning.collectAsState()
    val snapshots by repository.snapshotsFlow.collectAsState(initial = emptyList())
    val scoreResults by repository.scoreResultsFlow.collectAsState(initial = emptyList())
    val decisions by repository.decisionsFlow.collectAsState(initial = emptyList())
    val latestScan by repository.latestScanRunFlow.collectAsState(initial = null)

    val scoreMap = remember(scoreResults) { scoreResults.associateBy { it.packageName } }
    val decisionMap = remember(decisions) { decisions.associateBy { it.packageName } }

    // Attention apps list (ignoring kept or suppressed apps)
    val attentionApps = remember(snapshots, scoreMap, decisionMap) {
        snapshots.filter { app ->
            val dec = decisionMap[app.packageName]
            if (dec?.decision == "IGNORE") return@filter false
            val sc = scoreMap[app.packageName]
            (sc?.impactBand == "High" || (sc?.healthScore ?: 100.0) < 60.0)
        }
    }

    val meanHealth = remember(scoreResults) {
        if (scoreResults.isNotEmpty()) scoreResults.map { it.healthScore }.average() else 100.0
    }

    val totalBytes = latestScan?.totalDeviceBytes ?: (64L * 1024 * 1024 * 1024)
    val freeBytes = latestScan?.freeDeviceBytes ?: (20L * 1024 * 1024 * 1024)
    val usedBytes = totalBytes - freeBytes

    // Potentially reviewable storage estimate: cache + dormant apps footprint
    val reviewableBytes = remember(snapshots, scoreMap) {
        // Estimated cache & dormant sizes labeled as "up to" per PRD FR-05
        (attentionApps.size * 250L * 1024 * 1024).coerceAtMost(usedBytes)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryBlue,
                            modifier = Modifier.size(28.dp)
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
                    IconButton(onClick = onNavigateToNlq) {
                        Icon(Icons.Default.Search, contentDescription = "Ask AppPulse")
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
            Spacer(modifier = Modifier.height(10.dp))

            // Phone Health Ring Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val phoneHealthBand = when {
                        meanHealth >= 80.0 -> "Healthy"
                        meanHealth >= 60.0 -> "Fair"
                        else -> "Needs Attention"
                    }

                    HealthScoreRing(
                        score = meanHealth,
                        band = phoneHealthBand,
                        size = 170
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Phone Health Score",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${snapshots.size} installed launchable apps evaluated",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Storage Breakdown Card
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

            // Attention Alert Card (FR-13: "N apps need attention" + Review button)
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
                            Icon(Icons.Default.ArrowForward, contentDescription = null, tint = DeepNavy)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Quick Actions Section
            Text(
                text = "Quick Tools",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    icon = Icons.Default.Apps,
                    title = "All Apps",
                    subtitle = "${snapshots.size} inspected",
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToAllApps
                )
                QuickActionCard(
                    icon = Icons.Default.Search,
                    title = "Ask AI",
                    subtitle = "Filter & queries",
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToNlq
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    icon = Icons.Default.Shield,
                    title = "APK Scan",
                    subtitle = "Inspect file",
                    modifier = Modifier.weight(1f),
                    onClick = onNavigateToApkScan
                )
                QuickActionCard(
                    icon = Icons.Default.Refresh,
                    title = if (isScanning) "Scanning..." else "Rescan Now",
                    subtitle = "Update signals",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        coroutineScope.launch {
                            repository.performScan()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun QuickActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
