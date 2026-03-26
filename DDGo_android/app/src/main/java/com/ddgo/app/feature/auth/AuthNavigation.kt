package com.ddgo.app.feature.auth

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import androidx.navigation.navArgument
import com.ddgo.app.navigation.ScreenRoutes

/**
 * 인증(로그인, 회원가입) 관련 내비게이션 그래프.
 * 
 * 💡 아키텍처 가이드 준수: NavGraph에서 ViewModel을 직접 넘기지 않고,
 * 이 내비게이션 그래프가 살아있는 동안 공유될 수 있도록 
 * NavBackStackEntry를 부모(ScreenRoutes.Auth.route)로 지정해 hiltViewModel을 가져옵니다.
 */
fun NavGraphBuilder.authGraph(
    navController: NavController,
    onLoginSuccess: (AuthSuccessDestination) -> Unit
) {
    navigation(startDestination = ScreenRoutes.Auth.WELCOME, route = ScreenRoutes.Auth.route) {

        composable(ScreenRoutes.Auth.WELCOME) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(ScreenRoutes.Auth.route)
            }
            val viewModel: AuthViewModel = hiltViewModel(parentEntry)

            AuthLandingScreen(
                viewModel = viewModel,
                onLoginClick = {
                    viewModel.prepareLoginFlow()
                    navController.navigate(ScreenRoutes.Auth.LOGIN_EMAIL)
                },
                onRegisterClick = {
                    viewModel.prepareRegisterFlow()
                    navController.navigate(ScreenRoutes.Auth.REGISTER_EMAIL)
                }
            )
        }

        composable(ScreenRoutes.Auth.LOGIN_EMAIL) { backStackEntry ->
            // ✅ 이 그래프(auth_graph) 전체에서 공유되는 ViewModel 인스턴스를 가져옵니다.
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(ScreenRoutes.Auth.route)
            }
            val viewModel: AuthViewModel = hiltViewModel(parentEntry)

            LoginEmailScreen(
                viewModel = viewModel,
                onNext = { navController.navigate(ScreenRoutes.Auth.LOGIN_PASSWORD) },
                onLoginComplete = onLoginSuccess,
                onRegisterClick = {
                    viewModel.prepareRegisterFlow()
                    navController.navigate(ScreenRoutes.Auth.REGISTER_EMAIL)
                }
            )
        }

        composable(ScreenRoutes.Auth.LOGIN_PASSWORD) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(ScreenRoutes.Auth.route)
            }
            val viewModel: AuthViewModel = hiltViewModel(parentEntry)

            LoginPasswordScreen(
                viewModel = viewModel,
                onLoginComplete = onLoginSuccess,
                onBack = { navController.popBackStack() },
                onForgotPassword = {
                    viewModel.preparePasswordResetFlow()
                    navController.navigate(ScreenRoutes.Auth.PASSWORD_RESET)
                }
            )
        }

        composable(
            route = ScreenRoutes.Auth.PASSWORD_RESET_WITH_ARG,
            arguments = listOf(
                navArgument(ScreenRoutes.Auth.ARG_PASSWORD_RESET_LINK) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(ScreenRoutes.Auth.route)
            }
            val viewModel: AuthViewModel = hiltViewModel(parentEntry)
            val passwordResetLink =
                backStackEntry.arguments?.getString(ScreenRoutes.Auth.ARG_PASSWORD_RESET_LINK)

            LaunchedEffect(passwordResetLink) {
                if (passwordResetLink != null) {
                    viewModel.preparePasswordResetFlow(passwordResetLink)
                }
            }

            PasswordResetScreen(
                viewModel = viewModel,
                onResetCompleted = {
                    viewModel.consumePasswordResetCompletion()
                    val popped = navController.popBackStack(ScreenRoutes.Auth.LOGIN_PASSWORD, false)
                    if (!popped) {
                        navController.navigate(ScreenRoutes.Auth.LOGIN_EMAIL) {
                            launchSingleTop = true
                        }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ScreenRoutes.Auth.REGISTER_EMAIL) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(ScreenRoutes.Auth.route)
            }
            val viewModel: AuthViewModel = hiltViewModel(parentEntry)

            RegisterEmailScreen(
                viewModel = viewModel,
                onNext = { navController.navigate(ScreenRoutes.Auth.REGISTER_PASSWORD) },
                onBack = { navController.popBackStack() }
            )
        }

        composable(ScreenRoutes.Auth.REGISTER_PASSWORD) { backStackEntry ->
            val parentEntry = remember(backStackEntry) {
                navController.getBackStackEntry(ScreenRoutes.Auth.route)
            }
            val viewModel: AuthViewModel = hiltViewModel(parentEntry)

            RegisterPasswordScreen(
                viewModel = viewModel,
                onRegComplete = onLoginSuccess,
                onBack = { navController.popBackStack() }
            )
        }

    }
}
