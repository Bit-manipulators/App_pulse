package com.apppulse.app.ui.screens.allapps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.apppulse.app.data.local.entities.AppSnapshotEntity
import com.apppulse.app.data.repository.AppPulseRepository
import com.apppulse.app.ui.components.ImpactBadge
import com.apppulse.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllAppsScreen(
    repository: AppPulseRepository,
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val snapshots by repository.snapshotsFlow.collectAsState(initial = emptyList())
    val scoreResults by repository.scoreResultsFlow.collectAsState(initial = emptyList())
    val scoreMap = remember(scoreResults) { scoreResults.associateBy { it.packageName } }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }

    val filterOptions = listOf(
        "All",
        "High Impact",
        "Large Storage",
        "Unused (30d+)",
        "Permission Concerns",
        "Recent Problems"
    )

    val filteredApps = remember(snapshots, scoreMap, searchQuery, selectedFilter) {
        snapshots.filter { app ->
            // Search query match
            val matchesSearch = app.label.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)
            if (!matchesSearch) return@filter false

            val score = scoreMap[app.packageName]

            when (selectedFilter) {
                "High Impact" -> score?.impactBand == "High"
                "Large Storage" -> score?.subScoresJson?.contains("storage") == true && (score.subScoresJson.contains("50.0") || score.subScoresJson.contains("30.0") || score.subScoresJson.contains("10.0"))
                "Unused (30d+)" -> {
                    val days = ((System.currentTimeMillis() - app.updateTime) / (1000L * 60 * 60 * 24)).coerceAtLeast(0)
                    days >= 30
                }
                "Permission Concerns" -> (score?.healthScore ?: 100.0) < 70.0
                "Recent Problems" -> score?.dominantDriver == "Stability"
                else -> true
            }
        }.sortedByDescending { scoreMap[it.packageName]?.impactScore ?: 0.0 }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("All Installed Apps (${filteredApps.size})", fontWeight = FontWeight.Bold) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Search input field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                placeholder = { Text("Search by name or package...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            // Filter Chips Horizontal Scroll
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                filterOptions.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { selectedFilter = filter },
                        label = { Text(filter) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // App list
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredApps, key = { it.packageName }) { app ->
                    val score = scoreMap[app.packageName]
                    AppRowItem(
                        app = app,
                        healthScore = score?.healthScore ?: 100.0,
                        healthBand = score?.healthBand ?: "Healthy",
                        impactBand = score?.impactBand ?: "Low",
                        dominantDriver = score?.dominantDriver ?: "Storage",
                        onClick = { onNavigateToDetail(app.packageName) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppRowItem(
    app: AppSnapshotEntity,
    healthScore: Double,
    healthBand: String,
    impactBand: String,
    dominantDriver: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Android, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = app.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "API ${app.targetSdk} • v${app.versionName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                ImpactBadge(band = impactBand, dominantDriver = dominantDriver)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Health: ${healthScore.toInt()}/100",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
