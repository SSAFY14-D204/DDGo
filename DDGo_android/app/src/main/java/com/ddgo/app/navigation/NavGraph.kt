package com.ddgo.app.navigation

import android.net.Uri
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ddgo.app.BuildConfig
import com.ddgo.app.core.datastore.AuthSessionEvent
import com.ddgo.app.core.ui.components.DevNavigationOverlay
import com.ddgo.app.feature.auth.authGraph
import com.ddgo.app.feature.auth.AuthSuccessDestination
import com.ddgo.app.feature.debug.debugGraph
import com.ddgo.app.feature.main.mainGraph
import com.ddgo.app.feature.onboarding.OnboardingScreen
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
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route.orEmpty()
    val shouldShowDevOverlay = !currentRoute.startsWith(ScreenRoutes.Onboarding.route)

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
                    onNavigateToWelcome = {
                        navController.navigate(ScreenRoutes.Auth.WELCOME) {
                            popUpTo(ScreenRoutes.Splash.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToLoginEmail = {
                        navController.navigate(ScreenRoutes.Auth.LOGIN_EMAIL) {
                            popUpTo(ScreenRoutes.Splash.route) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                    onNavigateToMain = {
                        navController.navigate(ScreenRoutes.MainGraph.route) {
                            popUpTo(ScreenRoutes.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToOnboarding = { nextRoute, showEntryGuide ->
                        navController.navigate(
                            ScreenRoutes.Onboarding.createRoute(
                                nextRoute = nextRoute,
                                showEntryGuide = showEntryGuide
                            )
                        ) {
                            popUpTo(ScreenRoutes.Splash.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(
                route = ScreenRoutes.Onboarding.ROUTE_WITH_ARG,
                arguments = listOf(
                    navArgument(ScreenRoutes.Onboarding.ARG_NEXT_ROUTE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = ScreenRoutes.Auth.route
                    },
                    navArgument(ScreenRoutes.Onboarding.ARG_SHOW_ENTRY_GUIDE) {
                        type = NavType.BoolType
                        defaultValue = false
                    }
                )
            ) { backStackEntry ->
                val nextRoute = backStackEntry.arguments
                    ?.getString(ScreenRoutes.Onboarding.ARG_NEXT_ROUTE)
                    .orEmpty()
                    .ifBlank { ScreenRoutes.Auth.route }
                val showEntryGuide = backStackEntry.arguments
                    ?.getBoolean(ScreenRoutes.Onboarding.ARG_SHOW_ENTRY_GUIDE)
                    ?: false

                OnboardingScreen(
                    showEntryGuide = showEntryGuide,
                    onExit = { navController.popBackStack() },
                    onFinish = {
                        navController.navigate(nextRoute) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            authGraph(
                navController = navController,
                onLoginSuccess = { destination ->
                    val targetRoute = when (destination) {
                        AuthSuccessDestination.Main -> ScreenRoutes.MainGraph.route
                        is AuthSuccessDestination.Onboarding -> ScreenRoutes.Onboarding.createRoute(
                            nextRoute = ScreenRoutes.MainGraph.route,
                            showEntryGuide = destination.showEntryGuide
                        )
                    }

                    navController.navigate(targetRoute) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )

            mainGraph(
                navController = navController,
                onNavigateToAuth = {
                    navController.navigate(ScreenRoutes.Auth.LOGIN_EMAIL) {
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

        if (shouldShowDevOverlay) {
            DevNavigationOverlay(navController = navController)
        }

        if (showSessionExpiredDialog) {
            SessionExpiredDialog(
                onConfirm = {
                    showSessionExpiredDialog = false
                    navController.navigate(ScreenRoutes.Auth.LOGIN_EMAIL) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
