package com.ddgo.app.feature.debug

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

@Composable
fun DebugMainScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "👤 디버그 페이지",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

fun NavGraphBuilder.debugGraph(navController: NavController) {
    composable("debug_main") {
        DebugMainScreen()
    }
}