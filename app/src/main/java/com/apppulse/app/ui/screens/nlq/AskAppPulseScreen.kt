package com.apppulse.app.ui.screens.nlq

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import com.apppulse.app.domain.nlq.NaturalLanguageQueryEngine
import com.apppulse.app.domain.nlq.NlqQueryResult
import com.apppulse.app.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskAppPulseScreen(
    repository: AppPulseRepository,
    onBack: () -> Unit,
    onNavigateToDetail: (String) -> Unit
) {
    val snapshots by repository.snapshotsFlow.collectAsState(initial = emptyList())
    val scoreResults by repository.scoreResultsFlow.collectAsState(initial = emptyList())
    val decisions by repository.decisionsFlow.collectAsState(initial = emptyList())

    val scoreMap = remember(scoreResults) { scoreResults.associateBy { it.packageName } }
    val decisionMap = remember(decisions) { decisions.associateBy { it.packageName } }

    var userQuery by remember { mutableStateOf("What should I fix first?") }
    var currentResult by remember { mutableStateOf<NlqQueryResult?>(null) }

    // Execute query whenever userQuery or datasets change
    LaunchedEffect(userQuery, snapshots, scoreResults, decisions) {
        if (snapshots.isNotEmpty()) {
            val result = NaturalLanguageQueryEngine.execute(
                query = userQuery,
                apps = snapshots,
                scores = scoreMap,
                storages = emptyMap(),
                usages = emptyMap(),
                permissions = emptyMap(),
                indicators = emptyMap(),
                decisions = decisionMap
            )
            currentResult = result
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ask AppPulse", fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))

            // Query Input
            OutlinedTextField(
                value = userQuery,
                onValueChange = { userQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Ask anything about your apps...") },
                leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = AccentCyan) },
                trailingIcon = {
                    if (userQuery.isNotEmpty()) {
                        IconButton(onClick = { userQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Preset Suggested Queries
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NaturalLanguageQueryEngine.PRESET_QUERIES.forEach { preset ->
                    SuggestionChip(
                        onClick = { userQuery = preset },
                        label = { Text(preset) },
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Result explanation banner
            currentResult?.let { res ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = res.explanation,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(14.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Matched list
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(res.matchedApps, key = { it.packageName }) { app ->
                        val score = scoreMap[app.packageName]
                        Card(
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onNavigateToDetail(app.packageName) }
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
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Android, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(22.dp))
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(text = app.label, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                                        Text(text = app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }

                                Text(
                                    text = "Score: ${score?.healthScore?.toInt() ?: 100}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlue
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
