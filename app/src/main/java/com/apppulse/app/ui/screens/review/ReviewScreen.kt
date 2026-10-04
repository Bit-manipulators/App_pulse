package com.apppulse.app.ui.screens.review

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apppulse.app.data.local.entities.AppSnapshotEntity
import com.apppulse.app.data.repository.AppPulseRepository
import com.apppulse.app.ui.components.AppIconImage
import com.apppulse.app.ui.components.ImpactBadge
import com.apppulse.app.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    repository: AppPulseRepository,
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snapshots by repository.snapshotsFlow.collectAsState(initial = emptyList())
    val scoreResults by repository.scoreResultsFlow.collectAsState(initial = emptyList())
    val decisions by repository.decisionsFlow.collectAsState(initial = emptyList())

    val scoreMap = remember(scoreResults) { scoreResults.associateBy { it.packageName } }
    val decisionMap = remember(decisions) { decisions.associateBy { it.packageName } }

    // Flagged attention apps not ignored or kept
    val flaggedApps = remember(snapshots, scoreMap, decisionMap) {
        snapshots.filter { app ->
            val dec = decisionMap[app.packageName]
            if (dec?.decision == "IGNORE" || dec?.decision == "KEEP") return@filter false
            val sc = scoreMap[app.packageName]
            (sc?.impactBand == "High" || (sc?.healthScore ?: 100.0) < 60.0)
        }
    }

    var currentIndex by remember { mutableStateOf(0) }

    // Clamped index
    val currentApp: AppSnapshotEntity? = if (flaggedApps.isNotEmpty()) {
        flaggedApps[currentIndex.coerceIn(0, flaggedApps.lastIndex)]
    } else {
        null
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Flagged Apps", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            if (currentApp == null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = HealthHealthyBg,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = HealthHealthy, modifier = Modifier.size(36.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "All Flagged Apps Reviewed!",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No additional apps currently require your immediate attention.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onBack) {
                        Text("Return to Dashboard")
                    }
                }
            } else {
                val score = scoreMap[currentApp.packageName]

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        // Progress indicator
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "App ${currentIndex + 1} of ${flaggedApps.size}",
                                style = MaterialTheme.typography.labelLarge,
                                color = PrimaryBlue
                            )
                            score?.let {
                                ImpactBadge(band = it.impactBand, dominantDriver = it.dominantDriver)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Review App Card
                        Card(
                            shape = RoundedCornerShape(24.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(24.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AppIconImage(
                                        packageName = currentApp.packageName,
                                        modifier = Modifier.size(54.dp),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(
                                            text = currentApp.label,
                                            style = MaterialTheme.typography.titleLarge,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = currentApp.packageName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                                HorizontalDivider()
                                Spacer(modifier = Modifier.height(20.dp))

                                Text(
                                    text = "Why it needs attention:",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Elevated impact detected driven by ${score?.dominantDriver ?: "usage"}. Health score is ${score?.healthScore?.toInt() ?: 0}/100.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(24.dp))

                                // Quick Actions
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                data = Uri.parse("package:${currentApp.packageName}")
                                            }
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("App Info", fontSize = 13.sp)
                                    }

                                    Button(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_DELETE).apply {
                                                data = Uri.parse("package:${currentApp.packageName}")
                                            }
                                            context.startActivity(intent)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = HealthAttention),
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Uninstall", color = DeepNavy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                TextButton(
                                    onClick = { onNavigateToDetail(currentApp.packageName) },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("View Complete Health Diagnostics")
                                }
                            }
                        }
                    }

                    // Keep vs Ignore Action Bottom Row (persists per FR-19)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                val label = currentApp.label
                                coroutineScope.launch {
                                    repository.setUserDecision(currentApp.packageName, "IGNORE")
                                    android.widget.Toast.makeText(context, "Ignored alerts for $label", android.widget.Toast.LENGTH_SHORT).show()
                                    if (currentIndex >= flaggedApps.size - 1) {
                                        currentIndex = (flaggedApps.size - 2).coerceAtLeast(0)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ignore")
                        }

                        Button(
                            onClick = {
                                val label = currentApp.label
                                coroutineScope.launch {
                                    repository.setUserDecision(currentApp.packageName, "KEEP")
                                    android.widget.Toast.makeText(context, "$label marked as kept & trusted", android.widget.Toast.LENGTH_SHORT).show()
                                    if (currentIndex >= flaggedApps.size - 1) {
                                        currentIndex = (flaggedApps.size - 2).coerceAtLeast(0)
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = DeepNavy)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Keep App", color = DeepNavy, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
