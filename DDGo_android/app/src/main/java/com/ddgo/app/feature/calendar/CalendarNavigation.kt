package com.ddgo.app.feature.calendar

import android.net.Uri
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ddgo.app.navigation.ScreenRoutes
import java.time.LocalDate

internal const val CALENDAR_DETAIL_RESULT_CHALLENGE_ID = "calendarDetailResultChallengeId"

fun NavGraphBuilder.calendarDetailRoute(
    navController: NavController,
    onEntrySelected: (Long) -> Unit
) {
    composable(
        route = ScreenRoutes.CalendarDetail.ROUTE_WITH_ARG,
        arguments = listOf(
            navArgument(ScreenRoutes.CalendarDetail.ARG_SELECTED_DATE) {
                type = NavType.StringType
            }
        )
    ) { backStackEntry ->
        val requestedDate = backStackEntry.arguments
            ?.getString(ScreenRoutes.CalendarDetail.ARG_SELECTED_DATE)
            ?.let(LocalDate::parse)
            ?: LocalDate.now()
        val viewModel = rememberSharedCalendarViewModel(navController, backStackEntry)

        CalendarDetailScreen(
            requestedDate = requestedDate,
            onNavigateBack = { navController.popBackStack() },
            onEntrySelected = onEntrySelected,
            viewModel = viewModel
        )
    }
}

fun NavController.navigateToCalendarDetail(date: LocalDate) {
    navigate(
        buildString {
            append(ScreenRoutes.CalendarDetail.route)
            append('?')
            append(ScreenRoutes.CalendarDetail.ARG_SELECTED_DATE)
            append('=')
            append(Uri.encode(date.toString()))
        }
    )
}
