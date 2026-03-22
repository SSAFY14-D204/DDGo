package com.ddgo.app.feature.climbing.upload

import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

@Composable
internal fun UploadBackgroundUploadSnackbarHost(
    viewModel: UploadViewModel,
    modifier: Modifier = Modifier
) {
    val notice by viewModel.backgroundUploadNotice.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(notice?.id) {
        val currentNotice = notice ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = currentNotice.message,
            actionLabel = currentNotice.actionLabel
        )
        viewModel.consumeBackgroundUploadNotice(currentNotice.id)
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.retryBackgroundAttemptUpload()
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = modifier
    )
}
