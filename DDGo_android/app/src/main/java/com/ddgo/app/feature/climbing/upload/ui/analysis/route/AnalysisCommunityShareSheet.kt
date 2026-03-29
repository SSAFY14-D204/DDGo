package com.ddgo.app.feature.climbing.upload.ui.analysis.route

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.feature.climbing.upload.AnalysisBgColor
import com.ddgo.app.feature.climbing.upload.AnalysisCardColor
import com.ddgo.app.feature.climbing.upload.AnalysisGradientButton
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisPrimary
import com.ddgo.app.feature.climbing.upload.AnalysisText
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.HeaderChip
import com.ddgo.app.navigation.PendingCommunityComposeRequest
import com.ddgo.app.navigation.PendingCommunityComposeVideo

internal data class AnalysisCommunityShareOption(
    val attemptNo: Int,
    val videoUri: String,
    val subtitle: String? = null
)

private const val MaxCommunityShareSelectionCount = 3

internal fun buildPendingCommunityComposeRequest(
    gymId: Long?,
    gymName: String,
    options: List<AnalysisCommunityShareOption>
): PendingCommunityComposeRequest {
    return PendingCommunityComposeRequest(
        requestId = System.currentTimeMillis(),
        gymId = gymId,
        gymName = gymName.takeIf { it.isNotBlank() },
        videos = options
            .sortedBy { option -> option.attemptNo }
            .map { option ->
                PendingCommunityComposeVideo(
                    attemptNo = option.attemptNo,
                    videoUri = option.videoUri
                )
            }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AnalysisCommunityShareSheet(
    options: List<AnalysisCommunityShareOption>,
    initialSelectedAttemptNos: Set<Int>,
    onDismissRequest: () -> Unit,
    onConfirm: (List<AnalysisCommunityShareOption>) -> Unit
) {
    val context = LocalContext.current
    var selectedAttemptNos by rememberSaveable(initialSelectedAttemptNos) {
        mutableStateOf(initialSelectedAttemptNos.toList().sorted())
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        containerColor = AnalysisBgColor,
        contentColor = AnalysisText,
        scrimColor = Color.Black.copy(alpha = 0.62f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
        ) {
            Text(
                text = "커뮤니티에 올릴 시도 영상을 선택하세요",
                color = AnalysisText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "최대 3개까지 선택할 수 있어요.",
                color = AnalysisMuted,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.height(20.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = options,
                    key = { option -> option.attemptNo }
                ) { option ->
                    val isSelected = selectedAttemptNos.contains(option.attemptNo)
                    AnalysisCommunityShareOptionCard(
                        option = option,
                        isSelected = isSelected,
                        onClick = {
                            selectedAttemptNos = when {
                                isSelected -> {
                                    selectedAttemptNos.filterNot { attemptNo -> attemptNo == option.attemptNo }
                                }

                                selectedAttemptNos.size >= MaxCommunityShareSelectionCount -> {
                                    Toast.makeText(
                                        context,
                                        "영상은 최대 3개까지 선택할 수 있어요.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    selectedAttemptNos
                                }

                                else -> {
                                    (selectedAttemptNos + option.attemptNo).sorted()
                                }
                            }
                        }
                    )
                }
            }

            HorizontalDivider(color = AnalysisMuted.copy(alpha = 0.18f))
            Spacer(modifier = Modifier.height(18.dp))
            AnalysisGradientButton(
                text = "선택 완료",
                onClick = {
                    val selectedOptions = options.filter { option ->
                        selectedAttemptNos.contains(option.attemptNo)
                    }
                    onConfirm(selectedOptions)
                },
                enabled = selectedAttemptNos.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AnalysisCommunityShareOptionCard(
    option: AnalysisCommunityShareOption,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = if (isSelected) AnalysisPrimary else AnalysisMuted.copy(alpha = 0.22f)

    Surface(
        color = AnalysisCardColor,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accentColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "${option.attemptNo}차 시도",
                        color = AnalysisText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    option.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                        Text(
                            text = subtitle,
                            color = AnalysisMuted,
                            fontSize = 13.sp
                        )
                    }
                }

                HeaderChip(
                    text = if (isSelected) "선택됨" else "선택",
                    background = if (isSelected) AnalysisPrimary.copy(alpha = 0.22f) else Color(0xFF23252B),
                    contentColor = if (isSelected) AnalysisPrimary else AnalysisText,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(
                            color = if (isSelected) AnalysisPrimary else AnalysisMuted,
                            shape = CircleShape
                        )
                )
                Text(
                    text = "분석 결과 영상 첨부",
                    color = AnalysisText.copy(alpha = 0.9f),
                    fontSize = 13.sp
                )
            }
        }
    }
}
