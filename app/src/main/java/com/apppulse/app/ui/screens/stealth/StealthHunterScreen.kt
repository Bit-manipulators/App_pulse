package com.apppulse.app.ui.screens.stealth

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.apppulse.app.data.remote.OllamaClient
import com.apppulse.app.data.repository.AppPulseRepository
import com.apppulse.app.domain.stealth.HeuristicTag
import com.apppulse.app.domain.stealth.StealthAppThreat
import com.apppulse.app.domain.stealth.ThreatLevel
import com.apppulse.app.ui.components.AppIconImage
import com.apppulse.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun StealthHunterScreen(
    repository: AppPulseRepository,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val threats by repository.stealthThreatsFlow.collectAsState(initial = emptyList())
    val snapshots by repository.snapshotsFlow.collectAsState(initial = emptyList())

    var isScanning by remember { mutableStateOf(false) }

    // Run stealth scan on first entrance
    LaunchedEffect(Unit) {
        if (threats.isEmpty()) {
            isScanning = true
            repository.scanForStealthThreats()
            isScanning = false
        }
    }

    fun rescanStealth() {
        coroutineScope.launch {
            isScanning = true
            repository.scanForStealthThreats()
            isScanning = false
            Toast.makeText(context, "Stealth Radar Scan Complete", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = AccentCyan.copy(alpha = 0.15f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.VisibilityOff,
                                    contentDescription = null,
                                    tint = AccentCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Stealth & Stalkerware Hunter", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { rescanStealth() }, enabled = !isScanning) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = PrimaryBlue)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Rescan Stealth", tint = PrimaryBlue)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 12.dp)
        ) {
            // ==========================================
            // 1. RADAR POSTURE STATUS HEADER
            // ==========================================
            item {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (threats.isEmpty()) HealthHealthyBg else HealthAttentionBg
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (threats.isEmpty()) HealthHealthy else HealthAttention,
                            modifier = Modifier.size(54.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (threats.isEmpty()) Icons.Default.VerifiedUser else Icons.Default.GppMaybe,
                                    contentDescription = null,
                                    tint = DeepNavy,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (threats.isEmpty()) "Radar Status: CLEAN" else "Stealth Threats Detected",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (threats.isEmpty()) {
                                    "No hidden, headless, or camouflaged stalkerware detected across ${snapshots.size} installed apps."
                                } else {
                                    "${threats.size} non-system app(s) lack launcher icons or hold suspicious accessibility/admin controls."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ==========================================
            // 2. HEURISTIC AUDIT BREAKDOWN BANNER
            // ==========================================
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Heuristic Protection Checks",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Headless Launcher Absences", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${threats.count { it.hasNoLauncherIcon }} found", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Deceptive System Camouflage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${threats.count { it.hasSystemCamouflage }} found", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Active Accessibility Hijacking", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${threats.count { it.isAccessibilityActive }} found", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            // ==========================================
            // 3. THREATS LIST OR CLEAN VERIFICATION
            // ==========================================
            if (threats.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Verified Safe Attributes:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = HealthHealthy
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            SafeAttributeRow("All third-party applications have visible launcher icons in the app drawer.")
                            SafeAttributeRow("No non-system apps are impersonating Android OS system update services.")
                            SafeAttributeRow("No unauthorized accessibility services are scraping screen content or keystrokes.")
                            SafeAttributeRow("Mainstream messengers (WhatsApp, Telegram) are fully verified and trusted.")
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = "Flagged Applications (${threats.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(threats, key = { it.packageName }) { threat ->
                    StealthThreatCard(
                        threat = threat,
                        onTrust = {
                            coroutineScope.launch {
                                repository.trustStealthApp(threat.packageName)
                                Toast.makeText(context, "${threat.label} whitelisted and marked as trusted", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SafeAttributeRow(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = HealthHealthy,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StealthThreatCard(
    threat: StealthAppThreat,
    onTrust: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isConsultingAi by remember { mutableStateOf(false) }
    var aiReport by remember { mutableStateOf<String?>(null) }

    fun consultQwenAi() {
        coroutineScope.launch {
            isConsultingAi = true
            val prompt = """
                Analyze this suspected stalkerware or stealth application on Android:
                - Application Name: ${threat.label}
                - Package: ${threat.packageName}
                - Version: ${threat.versionName}
                - Threat Score: ${threat.threatScore}/100 (${threat.threatLevel})
                - Heuristic Flags: ${threat.tags.joinToString { it.label }}
                - Specific Findings: ${threat.heuristicReasons.joinToString(" ")}
                
                Explain in 2-3 concise paragraphs:
                1. Why these specific indicators represent a privacy or surveillance risk.
                2. What actionable steps the user should take immediately (e.g. revoke accessibility, uninstall).
            """.trimIndent()

            val result = withContext(Dispatchers.IO) {
                OllamaClient.generateAnalysis(prompt).getOrNull()
            }
            aiReport = result ?: "On-device assessment: This application operates without a home screen presence or holds critical accessibility hooks, matching known stalkerware profiles. We recommend immediate uninstallation or privilege revocation."
            isConsultingAi = false
        }
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // App Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppIconImage(
                    packageName = threat.packageName,
                    modifier = Modifier.size(50.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = threat.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = threat.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                // Threat Score Badge
                val badgeColor = when (threat.threatLevel) {
                    ThreatLevel.CRITICAL -> HealthAttention
                    ThreatLevel.HIGH -> HealthAttention
                    ThreatLevel.MODERATE -> HealthFair
                    ThreatLevel.SAFE -> HealthHealthy
                }

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = badgeColor.copy(alpha = 0.15f)
                ) {
                    Text(
                        text = "${threat.threatScore}% Risk",
                        color = badgeColor,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Heuristic Warning Tag Chips (FlowRow)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                threat.tags.forEach { tag ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = tag.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = AccentCyan,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Plain-English Heuristic Reasons
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                threat.heuristicReasons.forEach { reason ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text("• ", color = HealthAttention, fontWeight = FontWeight.Bold)
                        Text(
                            text = reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Qwen AI Threat Report (if generated)
            AnimatedVisibility(visible = aiReport != null) {
                aiReport?.let { report ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentCyan, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Qwen AI Threat Assessment", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(text = report, style = MaterialTheme.typography.bodySmall, lineHeight = 19.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Revoke Privileges (if Accessibility is active)
                if (threat.isAccessibilityActive) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Revoke Access", fontSize = 12.sp, maxLines = 1)
                    }
                } else if (threat.isDeviceAdminActive) {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Revoke Admin", fontSize = 12.sp, maxLines = 1)
                    }
                } else {
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", threat.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("App Info", fontSize = 12.sp)
                    }
                }

                // Uninstall Button
                Button(
                    onClick = {
                        val intent = Intent(Intent.ACTION_DELETE).apply {
                            data = Uri.fromParts("package", threat.packageName, null)
                        }
                        context.startActivity(intent)
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = HealthAttention),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Uninstall", color = DeepNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                // Ask AI Button
                IconButton(
                    onClick = { consultQwenAi() },
                    modifier = Modifier.size(40.dp)
                ) {
                    if (isConsultingAi) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = AccentCyan)
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "Consult AI", tint = AccentCyan)
                    }
                }

                // Trust App (Whitelist)
                IconButton(
                    onClick = onTrust,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Trust App", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
