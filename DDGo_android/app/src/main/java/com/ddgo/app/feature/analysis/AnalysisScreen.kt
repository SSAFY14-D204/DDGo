package com.ddgo.app.feature.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.core.ui.theme.DDGoTheme
import com.ddgo.app.feature.analysis.components.AnalysisAttemptDetailScreen
import com.ddgo.app.feature.analysis.components.AnalysisChallengeDetailScreen
import com.ddgo.app.feature.analysis.components.AnalysisChallengeListSection
import com.ddgo.app.feature.analysis.components.AnalysisGlow
import com.ddgo.app.feature.analysis.components.AnalysisGrowthSection
import com.ddgo.app.feature.analysis.components.AnalysisTopBar
import com.ddgo.app.feature.analysis.model.AnalysisUiState
import com.ddgo.app.feature.analysis.style.AnalysisPalette

/**
 * 메인 탭의 분석 화면 진입점입니다.
 *
 * 역할:
 * - 대시보드, 챌린지 상세, 시도 상세 중 현재 상태에 맞는 화면을 렌더링합니다.
 * - 업로드 직후 결과 화면과는 분리된, 메인 탭 전용 분석 경험만 담당합니다.
 */
@Composable
fun AnalysisScreen(
    externalChallengeId: Long? = null,
    onExternalChallengeHandled: () -> Unit = {},
    viewModel: AnalysisViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val attemptDetail = uiState.attemptDetail
    val challengeDetail = uiState.challengeDetail

    LaunchedEffect(externalChallengeId, uiState.challenges.size) {
        val challengeId = externalChallengeId ?: return@LaunchedEffect
        if (viewModel.openChallengeDetailIfAvailable(challengeId)) {
            onExternalChallengeHandled()
        }
    }

    when {
        attemptDetail != null -> {
            AnalysisAttemptDetailScreen(
                detail = attemptDetail,
                onBack = viewModel::closeAttemptDetail
            )
        }

        challengeDetail != null -> {
            AnalysisChallengeDetailScreen(
                detail = challengeDetail,
                onBack = viewModel::closeChallengeDetail,
                onAttemptSelected = viewModel::openAttemptDetail
            )
        }

        else -> {
            AnalysisDashboardContent(
                uiState = uiState,
                onChallengeSelected = viewModel::openChallengeDetail
            )
        }
    }
}

/**
 * 메인 탭에서 처음 보이는 분석 대시보드 본문입니다.
 *
 * 역할:
 * - 전체 성장과 챌린지 목록만 보여주고, 더 깊은 정보는 별도 상세 화면으로 넘깁니다.
 * - 첫 화면에서 과도한 정보 밀도를 줄이고 진입 맥락을 분명하게 합니다.
 */
@Composable
internal fun AnalysisDashboardContent(
    uiState: AnalysisUiState,
    onChallengeSelected: (Long) -> Unit
) {
    val backgroundBrush = Brush.verticalGradient(
        colors = listOf(
            AnalysisPalette.BackgroundTop,
            AnalysisPalette.BackgroundBottom,
            AnalysisPalette.BackgroundTop
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundBrush)
    ) {
        AnalysisGlow(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 56.dp, y = (-28).dp),
            colors = listOf(
                AnalysisPalette.Accent.copy(alpha = 0.18f),
                AnalysisPalette.Accent.copy(alpha = 0f)
            )
        )

        AnalysisGlow(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = (-92).dp, y = 148.dp),
            colors = listOf(
                AnalysisPalette.AccentStrong.copy(alpha = 0.12f),
                AnalysisPalette.AccentStrong.copy(alpha = 0f)
            )
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                AnalysisTopBar(
                    title = uiState.title
                )
            }

            item {
                AnalysisGrowthSection(summary = uiState.growthSummary)
            }

            item {
                AnalysisChallengeListSection(
                    challenges = uiState.challenges,
                    onChallengeSelected = onChallengeSelected
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AnalysisScreenPreview() {
    DDGoTheme(darkTheme = false) {
        AnalysisDashboardContent(
            uiState = AnalysisPreviewData.defaultUiState(),
            onChallengeSelected = {}
        )
    }
}
