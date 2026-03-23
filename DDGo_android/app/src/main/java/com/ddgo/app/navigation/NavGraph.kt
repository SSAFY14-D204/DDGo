package com.ddgo.app.navigation

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ddgo.app.BuildConfig
import com.ddgo.app.core.datastore.AuthSessionEvent
import com.ddgo.app.core.ui.components.DevNavigationOverlay
import com.ddgo.app.feature.auth.authGraph
import com.ddgo.app.feature.debug.debugGraph
import com.ddgo.app.feature.main.mainGraph
import com.ddgo.app.feature.splash.SplashScreen
import kotlinx.coroutines.flow.collectLatest

@Composable
fun NavGraph(
    passwordResetDeepLink: String? = null,
    onPasswordResetDeepLinkConsumed: () -> Unit = {}
) {
    val navController: NavHostController = rememberNavController()
    val appSessionViewModel: AppSessionViewModel = hiltViewModel()
    var showSessionExpiredDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(appSessionViewModel) {
        appSessionViewModel.authSessionEvent.collectLatest { event ->
            when (event) {
                AuthSessionEvent.SessionExpired -> {
                    showSessionExpiredDialog = true
                }
            }
        }
    }

    if (BuildConfig.DEBUG) {
        DisposableEffect(navController) {
            val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                Log.d("NavRoute", "currentRoute=${destination.route}")
            }
            navController.addOnDestinationChangedListener(listener)

            onDispose {
                navController.removeOnDestinationChangedListener(listener)
            }
        }
    }

    LaunchedEffect(passwordResetDeepLink) {
        val deepLink = passwordResetDeepLink ?: return@LaunchedEffect
        val encodedLink = Uri.encode(deepLink)

        navController.navigate(
            "${ScreenRoutes.Auth.PASSWORD_RESET}?${ScreenRoutes.Auth.ARG_PASSWORD_RESET_LINK}=$encodedLink"
        ) {
            launchSingleTop = true
        }

        onPasswordResetDeepLinkConsumed()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = ScreenRoutes.Splash.route
        ) {
            composable(ScreenRoutes.Splash.route) {
                SplashScreen(
                    onNavigateToAuth = {
                        navController.navigate(ScreenRoutes.Auth.route) {
                            popUpTo(ScreenRoutes.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToMain = {
                        navController.navigate(ScreenRoutes.MainGraph.route) {
                            popUpTo(ScreenRoutes.Splash.route) { inclusive = true }
                        }
                    }
                )
            }

            authGraph(
                navController = navController,
                onLoginSuccess = {
                    navController.navigate(ScreenRoutes.MainGraph.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )

            mainGraph(
                navController = navController,
                onNavigateToAuth = {
                    navController.navigate(ScreenRoutes.Auth.route) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNavigateToDebug = {
                    navController.navigate("debug_main")
                }
            )

            debugGraph(navController = navController)
        }

        DevNavigationOverlay(navController = navController)

        if (showSessionExpiredDialog) {
            AlertDialog(
                onDismissRequest = {},
                title = {
                    Text(text = "\uC138\uC158 \uB9CC\uB8CC")
                },
                text = {
                    Text(text = "\uC138\uC158\uC774 \uB9CC\uB8CC\uB418\uC5C8\uC5B4\uC694. \uB2E4\uC2DC \uB85C\uADF8\uC778\uD574 \uC8FC\uC138\uC694.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showSessionExpiredDialog = false
                            navController.navigate(ScreenRoutes.Auth.route) {
                                popUpTo(0) { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    ) {
                        Text(text = "\uD655\uC778")
                    }
                }
            )
        }
    }
}
