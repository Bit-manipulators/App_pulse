package com.apppulse.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.apppulse.app.data.local.entities.AppSnapshotEntity
import com.apppulse.app.data.repository.AppPulseRepository
import com.apppulse.app.ui.screens.allapps.AllAppsScreen
import com.apppulse.app.ui.screens.apkscan.ApkScanScreen
import com.apppulse.app.ui.screens.dashboard.DashboardScreen
import com.apppulse.app.ui.screens.detail.AppDetailScreen
import com.apppulse.app.ui.screens.nlq.AskAppPulseScreen
import com.apppulse.app.ui.screens.onboarding.OnboardingScreen
import com.apppulse.app.ui.screens.review.ReviewScreen
import com.apppulse.app.ui.screens.settings.SettingsScreen
import com.apppulse.app.ui.screens.test.AppTestScreen

object Destinations {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD = "dashboard"
    const val REVIEW = "review"
    const val ALL_APPS = "all_apps"
    const val APP_DETAIL = "detail/{packageName}"
    const val APP_TEST = "app_test/{packageName}"
    const val NLQ = "nlq"
    const val APK_SCAN = "apk_scan"
    const val SETTINGS = "settings"

    fun appDetail(packageName: String) = "detail/$packageName"
    fun appTest(packageName: String) = "app_test/$packageName"
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    repository: AppPulseRepository,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Destinations.ONBOARDING) {
            OnboardingScreen(
                repository = repository,
                onCompleted = {
                    navController.navigate(Destinations.DASHBOARD) {
                        popUpTo(Destinations.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(Destinations.DASHBOARD) {
            DashboardScreen(
                repository = repository,
                onNavigateToReview = { navController.navigate(Destinations.REVIEW) },
                onNavigateToAllApps = { navController.navigate(Destinations.ALL_APPS) },
                onNavigateToNlq = { navController.navigate(Destinations.NLQ) },
                onNavigateToApkScan = { navController.navigate(Destinations.APK_SCAN) },
                onNavigateToSettings = { navController.navigate(Destinations.SETTINGS) },
                onNavigateToDetail = { pkg -> navController.navigate(Destinations.appDetail(pkg)) },
                onNavigateToAppTest = { pkg -> navController.navigate(Destinations.appTest(pkg)) }
            )
        }

        composable(Destinations.REVIEW) {
            ReviewScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onNavigateToDetail = { pkg -> navController.navigate(Destinations.appDetail(pkg)) }
            )
        }

        composable(Destinations.ALL_APPS) {
            AllAppsScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onNavigateToDetail = { pkg -> navController.navigate(Destinations.appDetail(pkg)) }
            )
        }

        composable(
            route = Destinations.APP_DETAIL,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType })
        ) { backStackEntry ->
            val pkg = backStackEntry.arguments?.getString("packageName") ?: ""
            AppDetailScreen(
                packageName = pkg,
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Destinations.APP_TEST,
            arguments = listOf(navArgument("packageName") { type = NavType.StringType })
        ) { backStackEntry ->
            val pkg = backStackEntry.arguments?.getString("packageName") ?: ""
            val snapshots by repository.snapshotsFlow.collectAsState(initial = emptyList())
            val app = snapshots.firstOrNull { it.packageName == pkg }
                ?: AppSnapshotEntity(
                    packageName = pkg,
                    label = pkg.substringAfterLast("."),
                    versionName = "1.0",
                    versionCode = 1L,
                    targetSdk = 35,
                    installTime = 0L,
                    updateTime = 0L,
                    category = 0,
                    isSystem = false
                )
            AppTestScreen(
                app = app,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Destinations.NLQ) {
            AskAppPulseScreen(
                repository = repository,
                onBack = { navController.popBackStack() },
                onNavigateToDetail = { pkg -> navController.navigate(Destinations.appDetail(pkg)) }
            )
        }

        composable(Destinations.APK_SCAN) {
            ApkScanScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable(Destinations.SETTINGS) {
            SettingsScreen(
                repository = repository,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
