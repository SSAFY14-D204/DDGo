package com.ddgo.app.feature.climbing.record.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.core.ui.tokens.DdgoColorTokens
import com.ddgo.app.core.ui.tokens.DdgoHoldColorPalette
import com.ddgo.app.domain.model.GymGrade
import com.ddgo.app.feature.climbing.upload.ChallengeCreationUiState
import com.ddgo.app.feature.climbing.upload.RealtimeHoldColorOption
import com.ddgo.app.feature.climbing.upload.resolveHoldColorKey

internal val RecordAccent = Color(0xFF42A5F5)
internal val RecordAccentStrong = Color(0xFF1E88E5)
internal val RecordAccentSoft = Color(0xFF111318)
internal val RecordBackgroundTop = Color(0xFF030405)
internal val RecordBackgroundBottom = Color(0xFF08090B)
internal val RecordSurface = Color(0xFF0F1115)
internal val RecordSurfaceMuted = Color(0xFF14171C)
internal val RecordBorder = Color(0xFF252A34)
internal val RecordTextPrimary = Color(0xFFF7F8FA)
internal val RecordTextSecondary = Color(0xFFBAC2CF)
internal val RecordTextHint = Color(0xFF7D8694)
internal val RecordScrim = Color(0xFF020305)
internal val RecordBackdrop = Color(0xFF090B10)
internal val RecordOnAccent = Color.White
internal val RecordError = Color(0xFFFF8A8A)

private data class DifficultyReferenceSlot(
    val color: Color,
    val isSelected: Boolean
)

@Composable
internal fun RealtimeDifficultyPaletteCard(
    gymName: String,
    grades: List<GymGrade>,
    selectedGradeId: Int?,
    challengeCreationUiState: ChallengeCreationUiState,
    onSelectDifficulty: (GymGrade) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = gymName.ifBlank { "난이도 선택" },
            color = RecordTextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "업로드 화면과 같은 팔레트에서 난이도를 고르면, 바로 홀드 색 선택으로 넘어갑니다.",
            color = RecordTextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(18.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            DifficultyReferenceBar(selectedGrade = grades.firstOrNull { it.gymGradeId == selectedGradeId })
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (grades.isEmpty()) {
                    Text(
                        text = "이 암장의 난이도 정보가 아직 없어요.",
                        color = RecordTextHint,
                        fontSize = 13.sp,
                        lineHeight = 19.sp
                    )
                } else {
                    grades.forEach { grade ->
                        DifficultyPaletteChip(
                            grade = grade,
                            selected = grade.gymGradeId == selectedGradeId,
                            onClick = { onSelectDifficulty(grade) }
                        )
                    }
                }
            }
        }

        when (challengeCreationUiState) {
            is ChallengeCreationUiState.Error -> {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = challengeCreationUiState.message,
                    color = RecordError,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }

            is ChallengeCreationUiState.Loading -> {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = RecordAccent,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "난이도 정보를 정리하고 있어요...",
                        color = RecordTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            else -> Unit
        }
    }
}

@Composable
internal fun RealtimeHoldColorSetupCard(
    grade: GymGrade?,
    holdColorOptions: List<RealtimeHoldColorOption>,
    selectedHoldColorKey: String?,
    challengeCreationUiState: ChallengeCreationUiState,
    onSelectHoldColor: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "홀드 색 선택",
            color = RecordTextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = grade?.let {
                "${resolveHoldColorDisplayName(it.colorName, it.colorHex)} 난이도를 선택했어요. 홀드 색을 고르면 준비가 완료됩니다."
            } ?: "홀드 색을 고르면 바로 촬영 준비가 완료됩니다.",
            color = RecordTextSecondary,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(18.dp))

        grade?.let {
            SelectedGradeSummaryCard(grade = it)
            Spacer(modifier = Modifier.height(18.dp))
        }

        HoldColorPaletteGrid(
            options = holdColorOptions,
            selectedHoldColorKey = selectedHoldColorKey,
            onSelectHoldColor = onSelectHoldColor
        )

        when (challengeCreationUiState) {
            is ChallengeCreationUiState.Error -> {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = challengeCreationUiState.message,
                    color = RecordError,
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }

            is ChallengeCreationUiState.Loading -> {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = RecordAccent,
                        strokeWidth = 2.dp
                    )
                    Text(
                        text = "실시간 도전을 준비하고 있어요...",
                        color = RecordTextSecondary,
                        fontSize = 13.sp
                    )
                }
            }

            else -> Unit
        }
    }
}

@Composable
internal fun HoldColorPaletteGrid(
    options: List<RealtimeHoldColorOption>,
    selectedHoldColorKey: String?,
    onSelectHoldColor: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        options.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { option ->
                    HoldColorTile(
                        option = option,
                        selected = option.key == selectedHoldColorKey,
                        onClick = { onSelectHoldColor(option.key) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(4 - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DifficultyReferenceBar(
    selectedGrade: GymGrade?,
    modifier: Modifier = Modifier
) {
    val slots = remember(selectedGrade) {
        difficultyReferenceSlots(selectedGrade)
    }

    Row(
        modifier = modifier.height(336.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(
            modifier = Modifier.height(336.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "어려움",
                color = RecordTextHint,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "쉬움",
                color = RecordTextHint,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Column(
            modifier = Modifier
                .width(34.dp)
                .height(336.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            slots.forEach { slot ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (slot.isSelected) {
                                Brush.verticalGradient(
                                    listOf(slot.color.copy(alpha = 0.95f), slot.color.copy(alpha = 0.65f))
                                )
                            } else {
                                Brush.verticalGradient(
                                    listOf(slot.color.copy(alpha = 0.24f), slot.color.copy(alpha = 0.08f))
                                )
                            }
                        )
                        .border(
                            width = if (slot.isSelected) 1.5.dp else 1.dp,
                            color = if (slot.isSelected) slot.color.copy(alpha = 0.95f) else RecordBorder,
                            shape = RoundedCornerShape(10.dp)
                        )
                )
            }
        }
    }
}

@Composable
private fun DifficultyPaletteChip(
    grade: GymGrade,
    selected: Boolean,
    onClick: () -> Unit
) {
    val accentColor = resolveGymGradeAccentColor(grade)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(if (selected) RecordAccentSoft else RecordSurfaceMuted)
            .border(
                width = 1.dp,
                color = if (selected) accentColor.copy(alpha = 0.8f) else RecordBorder,
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(accentColor)
                .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = resolveHoldColorDisplayName(grade.colorName, grade.colorHex),
                color = RecordTextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = grade.gradeLabel?.takeIf { it.isNotBlank() } ?: "V${grade.sortOrder}",
                color = RecordTextSecondary,
                fontSize = 12.sp
            )
        }

        Text(
            text = if (selected) "선택됨" else "선택",
            color = if (selected) accentColor else RecordTextHint,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SelectedGradeSummaryCard(
    grade: GymGrade,
    modifier: Modifier = Modifier
) {
    val accentColor = resolveGymGradeAccentColor(grade)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(RecordSurfaceMuted)
            .border(1.dp, RecordBorder, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.linearGradient(
                        listOf(accentColor.copy(alpha = 0.92f), accentColor.copy(alpha = 0.55f))
                    )
                )
        )

        Column {
            Text(
                text = "선택한 난이도",
                color = RecordAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = resolveHoldColorDisplayName(grade.colorName, grade.colorHex),
                color = RecordTextPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = grade.gradeLabel?.takeIf { it.isNotBlank() } ?: "V${grade.sortOrder}",
                color = RecordTextSecondary,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun HoldColorTile(
    option: RealtimeHoldColorOption,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fillColor = Color(option.colorInt)
    val borderColor = if (selected) {
        option.borderColorInt?.let(::Color) ?: fillColor.copy(alpha = 0.85f)
    } else {
        RecordBorder
    }
    val labelColor = if (option.key == "white") Color(0xFF16181C) else Color.White

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) RecordAccentSoft else RecordSurfaceMuted)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(fillColor)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) Color.White.copy(alpha = 0.9f) else borderColor,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(labelColor.copy(alpha = 0.9f))
                )
            }
        }

        Text(
            text = resolveRealtimeHoldColorTileDisplayName(option),
            color = RecordTextPrimary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

private fun difficultyReferenceSlots(selectedGrade: GymGrade?): List<DifficultyReferenceSlot> {
    val selectedIndex = selectedGrade?.sortOrder?.coerceIn(0, 11) ?: -1
    val baseColors = listOf(
        Color(0xFFFF4D4D),
        Color(0xFFFF7A00),
        Color(0xFFFFC400),
        Color(0xFF9FD33B),
        Color(0xFF4DD27D),
        Color(0xFF20C8E3),
        Color(0xFF2F8FFF),
        Color(0xFF4450FF),
        Color(0xFF8B67FF),
        Color(0xFFC35FFF),
        Color(0xFFFF5E9B),
        Color(0xFF7B4A2F)
    )
    return baseColors.mapIndexed { index, color ->
        DifficultyReferenceSlot(color = color, isSelected = index == selectedIndex)
    }
}

internal fun resolveGymGradeAccentColor(grade: GymGrade): Color {
    return DdgoHoldColorPalette.colorForKey(
        resolveHoldColorKey(
            colorName = grade.colorName,
            colorHex = grade.colorHex
        )
    ) ?: DdgoColorTokens.BrandBlue
}

internal fun resolveHoldColorDisplayName(
    colorName: String,
    colorHex: String?
): String {
    val resolved = com.ddgo.app.feature.climbing.upload.resolveHoldColorDisplayName(
        colorName = colorName,
        colorHex = colorHex
    )
    return resolved.ifBlank { "색상" }
}

private fun resolveRealtimeHoldColorTileDisplayName(option: RealtimeHoldColorOption): String {
    return realtimeHoldColorDisplayNameByKey(option.key) ?: resolveHoldColorDisplayName(option.key, null)
}

private fun realtimeHoldColorDisplayNameByKey(colorKey: String): String? {
    return DdgoHoldColorPalette.displayNameForKey(colorKey)
}

private fun colorOverrideByName(colorName: String): Color? {
    return DdgoHoldColorPalette.colorForKey(
        resolveHoldColorKey(colorName = colorName, colorHex = null)
    )
}

private fun fallbackColorByName(colorName: String): Color {
    return colorOverrideByName(colorName) ?: DdgoColorTokens.BrandBlue
}
