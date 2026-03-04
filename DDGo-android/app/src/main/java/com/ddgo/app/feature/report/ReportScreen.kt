package com.ddgo.app.feature.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ddgo.app.core.ui.components.DdgoFullScreenLoading

/**
 * 클라이밍 분석 리포트 화면.
 *
 * ── Compose 학습 포인트 ───────────────────────────────────────────
 * 1. LazyColumn: RecyclerView 대체. 대용량 목록을 효율적으로 렌더링
 * 2. Card: Material3 카드 컴포넌트. elevation으로 그림자 효과
 * 3. when(sealed class): 상태별 UI 분기 패턴
 * ────────────────────────────────────────────────────────────────
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    viewModel: ReportViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("나의 클라이밍 기록") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (uiState) {
                is ReportUiState.Loading -> DdgoFullScreenLoading(Modifier.fillMaxSize())
                is ReportUiState.Empty   -> EmptyReportContent()
                is ReportUiState.Error   -> ErrorContent((uiState as ReportUiState.Error).message)
            }
        }
    }
}

@Composable
private fun EmptyReportContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🧗", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(16.dp))
            Text(
                text = "아직 분석된 기록이 없어요\n첫 번째 클라이밍을 분석해 보세요!",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(message, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** 리포트 목록 아이템 카드 (목록 구현 시 활용) */
@Composable
fun ReportCard(
    grade: String,
    success: Boolean,
    failTimeMs: Long?,
    holdCount: Int,
    date: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 성공/실패 아이콘
            Icon(
                imageVector = if (success) Icons.Default.Done else Icons.Default.AccessTime,
                contentDescription = null,
                tint = if (success) MaterialTheme.colorScheme.tertiary
                       else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(32.dp)
            )

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "난이도: $grade",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (success) "완등 성공 🎉" else {
                        failTimeMs?.let { "실패 시점: ${it / 1000}초" } ?: "분석 중..."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = "홀드 수: $holdCount  |  $date",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}
