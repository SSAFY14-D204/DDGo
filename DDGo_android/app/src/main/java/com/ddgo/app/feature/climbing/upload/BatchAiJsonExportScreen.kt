package com.ddgo.app.feature.climbing.upload

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.launch

private val EXPORT_BG = Color(0xFF101010)
private val EXPORT_CARD = Color(0xFF1C1C1C)
private val EXPORT_TEXT = Color(0xFFF4F4F4)
private val EXPORT_SUBTEXT = Color(0xFFBDBDBD)
private val EXPORT_ERROR = Color(0xFFFF8A80)

@Composable
fun BatchAiJsonExportScreen(
    viewModel: UploadViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val attemptIndex = viewModel.currentAttemptIndex
    val playbackUri = viewModel.playbackAttemptUris.getOrNull(attemptIndex)
    val coroutineScope = rememberCoroutineScope()
    var refreshVersion by remember { mutableIntStateOf(0) }
    var isSaving by remember { mutableStateOf(false) }
    var pendingVariant by remember { mutableStateOf(BatchAiJsonExportVariant.SAMPLED) }
    var statusMessage by remember(playbackUri, attemptIndex) { mutableStateOf<String?>(null) }
    var statusIsError by remember(playbackUri, attemptIndex) { mutableStateOf(false) }
    val exportSummary by produceState<BatchAiJsonExportSummary?>(
        initialValue = null,
        key1 = attemptIndex,
        key2 = playbackUri,
        key3 = refreshVersion
    ) {
        value = viewModel.loadCurrentAttemptBatchAiJsonExportSummary()
    }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = CreateDocument("application/json")
    ) { targetUri ->
        if (targetUri == null) {
            return@rememberLauncherForActivityResult
        }

        coroutineScope.launch {
            isSaving = true
            statusMessage = null
            statusIsError = false
            val result = viewModel.exportCurrentAttemptBatchAiJson(
                targetUri = targetUri,
                variant = pendingVariant
            )
            statusMessage = result.fold(
                onSuccess = { summary ->
                    "Saved '${summary.suggestedFileName(pendingVariant)}'."
                },
                onFailure = { throwable ->
                    statusIsError = true
                    throwable.message ?: "Failed to save JSON."
                }
            )
            isSaving = false
            refreshVersion += 1
        }
    }

    LaunchedEffect(attemptIndex, playbackUri) {
        statusMessage = null
        statusIsError = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EXPORT_BG)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onNavigateBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2A2A2A),
                    contentColor = EXPORT_TEXT
                )
            ) {
                Text("Back")
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Batch AI JSON Export",
                    color = EXPORT_TEXT,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Export the current attempt as the same batch AI request JSON body.",
                    color = EXPORT_SUBTEXT,
                    fontSize = 12.sp
                )
            }
        }

        val summary = exportSummary
        if (summary == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
            return@Column
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = EXPORT_CARD
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Current Attempt",
                    color = EXPORT_TEXT,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                ExportInfoRow(label = "Attempt", value = "#${summary.attemptNumber}")
                ExportInfoRow(label = "AI Mode", value = summary.analysisMode.name)
                ExportInfoRow(label = "Hold Count", value = summary.holdCount.toString())
                ExportInfoRow(label = "Pose Frames", value = summary.poseFrameCount.toString())
                ExportInfoRow(
                    label = "Sampled Frames",
                    value = summary.sampledFrameCount?.toString() ?: "-"
                )
                ExportInfoRow(
                    label = "Sampled Step",
                    value = summary.sampledFrameStep?.toString() ?: "-"
                )
                ExportInfoRow(
                    label = "Raw Frames",
                    value = summary.rawFrameCount?.toString() ?: "-"
                )
                ExportInfoRow(
                    label = "Raw Step",
                    value = summary.rawFrameStep?.toString() ?: "-"
                )
                ExportInfoRow(
                    label = "Frame Size",
                    value = if (summary.frameWidthPx != null && summary.frameHeightPx != null) {
                        "${summary.frameWidthPx} x ${summary.frameHeightPx}"
                    } else {
                        "-"
                    }
                )
                ExportInfoRow(
                    label = "Sampled File",
                    value = summary.sampledSuggestedFileName
                )
                ExportInfoRow(
                    label = "Raw File",
                    value = summary.rawSuggestedFileName
                )
                summary.playbackUri?.let { uri ->
                    ExportInfoRow(
                        label = "Video",
                        value = uri.substringAfterLast('/').substringAfterLast('\\')
                    )
                }
            }
        }

        if (!summary.canExport) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = EXPORT_CARD
            ) {
                Text(
                    text = summary.unavailableReason ?: "Export is unavailable.",
                    color = EXPORT_ERROR,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 13.sp
                )
            }
        }

        statusMessage?.let { message ->
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = EXPORT_CARD
            ) {
                Text(
                    text = message,
                    color = if (statusIsError) EXPORT_ERROR else EXPORT_TEXT,
                    modifier = Modifier.padding(16.dp),
                    fontSize = 13.sp
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    pendingVariant = BatchAiJsonExportVariant.SAMPLED
                    createDocumentLauncher.launch(summary.sampledSuggestedFileName)
                },
                enabled = summary.canExport && !isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1565C0),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF2A2A2A),
                    disabledContentColor = EXPORT_SUBTEXT
                )
            ) {
                Text(if (isSaving && pendingVariant == BatchAiJsonExportVariant.SAMPLED) "Saving..." else "Save Sampled JSON")
            }

            OutlinedButton(
                onClick = {
                    pendingVariant = BatchAiJsonExportVariant.RAW
                    createDocumentLauncher.launch(summary.rawSuggestedFileName)
                },
                enabled = summary.canExport && !isSaving,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = EXPORT_TEXT
                )
            ) {
                Text(if (isSaving && pendingVariant == BatchAiJsonExportVariant.RAW) "Saving..." else "Save Raw JSON")
            }

            OutlinedButton(
                onClick = { refreshVersion += 1 },
                enabled = !isSaving,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = EXPORT_TEXT
                )
            ) {
                Text("Refresh")
            }
        }
    }
}

@Composable
private fun ExportInfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = EXPORT_SUBTEXT,
            fontSize = 12.sp,
            modifier = Modifier.width(88.dp)
        )
        Text(
            text = value,
            color = EXPORT_TEXT,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
    }
}
