package com.ddgo.app.feature.auth

import androidx.compose.runtime.remember
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.navigation
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
    onLoginSuccess: () -> Unit
) {
    navigation(startDestination = ScreenRoutes.Auth.WELCOME, route = ScreenRoutes.Auth.route) {

        composable(ScreenRoutes.Auth.WELCOME) {
            AuthLandingScreen(
                onLoginClick = { navController.navigate(ScreenRoutes.Auth.LOGIN_EMAIL) },
                onRegisterClick = { navController.navigate(ScreenRoutes.Auth.REGISTER_EMAIL) }
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
                onRegisterClick = { navController.navigate(ScreenRoutes.Auth.REGISTER_EMAIL) }
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
