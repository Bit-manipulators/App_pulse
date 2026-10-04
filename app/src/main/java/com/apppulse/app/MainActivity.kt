package com.apppulse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.apppulse.app.data.repository.AppPulseRepository
import com.apppulse.app.ui.navigation.AppNavigation
import com.apppulse.app.ui.navigation.Destinations
import com.apppulse.app.ui.theme.AppPulseTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: AppPulseRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        repository = AppPulseRepository(applicationContext)

        val hasAccess = repository.hasUsageAccess()
        val startDestination = if (hasAccess) Destinations.DASHBOARD else Destinations.ONBOARDING

        if (hasAccess) {
            lifecycleScope.launch {
                repository.performScan()
            }
        }

        setContent {
            AppPulseTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    AppNavigation(
                        navController = navController,
                        repository = repository,
                        startDestination = startDestination
                    )
                }
            }
        }
    }
}
