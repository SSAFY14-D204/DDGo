package com.ddgo.app.feature.debug

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ddgo.app.navigation.ScreenRoutes

fun NavGraphBuilder.debugGraph(navController: NavController) {
    composable(ScreenRoutes.Debug.MAIN) {
        ReleaseDebugStub()
    }
    composable(ScreenRoutes.Debug.UPLOAD_PHYSICS_OVERLAY) {
        ReleaseDebugStub()
    }
}

@androidx.compose.runtime.Composable
private fun ReleaseDebugStub() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Debug tools are unavailable in this build.",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
