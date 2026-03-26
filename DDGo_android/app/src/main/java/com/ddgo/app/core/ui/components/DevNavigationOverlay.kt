package com.ddgo.app.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.core.dev.DevOptions
import com.ddgo.app.domain.model.AiAnalysisMode
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import com.ddgo.app.navigation.ScreenRoutes
import com.ddgo.app.core.ui.tokens.DdgoColorTokens
import kotlin.math.roundToInt

/**
 * 개발용 네비게이션 오버레이.
 *
 * 화면 우측 하단에 드래그 가능한 "DEV" 버튼이 떠 있으며,
 * 탭하면 모든 화면으로 바로 이동할 수 있는 패널이 열립니다.
 * 서버 통신 없이 화면 전환만 수행합니다.
 */
@Composable
fun DevNavigationOverlay(navController: NavController) {
    var expanded by remember { mutableStateOf(false) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize().zIndex(Float.MAX_VALUE)) {
        // 패널 열렸을 때 배경 dim
        if (expanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { expanded = false }
            )
        }

        // 패널
        AnimatedVisibility(
            visible = expanded,
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            DevPanel(
                navController = navController,
                onDismiss = { expanded = false }
            )
        }

        // 플로팅 DEV 버튼 (드래그 가능)
        if (!expanded) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .padding(end = 16.dp, bottom = 100.dp)
                    .size(48.dp)
                    .background(DdgoColorTokens.BrandBlue, CircleShape)
                    .clickable { expanded = true }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "DEV",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

// ── 패널 본체 ──────────────────────────────────────────────

private data class DevRoute(
    val label: String,
    val route: String,
    val clearBackStack: Boolean = false
)

private val devRoutes = listOf(
    // ── Auth ──
    DevRoute("Welcome", ScreenRoutes.Auth.WELCOME),
    DevRoute("Login Email", ScreenRoutes.Auth.LOGIN_EMAIL),
    DevRoute("Login Password", ScreenRoutes.Auth.LOGIN_PASSWORD),
    DevRoute("Register Email", ScreenRoutes.Auth.REGISTER_EMAIL),
    DevRoute("Register Password", ScreenRoutes.Auth.REGISTER_PASSWORD),

    // ── Onboarding ──
    DevRoute(
        "Onboarding",
        ScreenRoutes.Onboarding.createRoute(
            nextRoute = ScreenRoutes.MainGraph.route,
            showEntryGuide = true
        )
    ),
    DevRoute(
        "Onboarding Recovery",
        ScreenRoutes.Onboarding.createRoute(
            nextRoute = ScreenRoutes.MainGraph.route,
            showEntryGuide = false
        )
    ),

    // ── Main ──
    DevRoute("Main (탭 UI)", ScreenRoutes.Main.route, clearBackStack = true),

    // ── Upload Flow ──
    DevRoute("1. 영상 선택", ScreenRoutes.Climbing.Upload.ATTEMPT_UPLOAD),
    DevRoute("2. 챌린지 생성", ScreenRoutes.Climbing.Upload.CHALLENGE_CREATE),
    DevRoute("2-1. 색상 선택", ScreenRoutes.Climbing.Upload.CHALLENGE_COLOR),
    DevRoute("dev.이미지 선택", ScreenRoutes.Climbing.Upload.DEV_IMAGE_PICKER),
    DevRoute("3. 홀드 탐지", ScreenRoutes.Climbing.Upload.CHALLENGE_HOLD),
    DevRoute("3-1. 추가 업로드", ScreenRoutes.Climbing.Upload.ADDITIONAL_UPLOAD),
    DevRoute("4. 홀드 선택", ScreenRoutes.Climbing.Upload.HOLD_SELECT),
    DevRoute("3-2. 분석 로딩", ScreenRoutes.Climbing.Upload.ANALYSIS_LOADING),
    DevRoute("5. 시도 결과", ScreenRoutes.Climbing.Upload.ATTEMPT_RESULT),
    DevRoute("홀드탐지디버깅", ScreenRoutes.Climbing.Upload.HOLD_CONTACT_DEBUG),
    DevRoute("6. 최종 분석", ScreenRoutes.Climbing.Upload.FINAL_ANALYSIS),

    // ── Record ──
    DevRoute("실시간 기록", ScreenRoutes.Climbing.Record.RECORD_MAIN),
)

private val sectionLabels = mapOf(
    0 to "Auth",
    5 to "Onboarding",
    7 to "Main",
    8 to "Upload Flow",
    19 to "Record",
)

@Composable
private fun DevPanel(
    navController: NavController,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .width(260.dp)
            .padding(vertical = 40.dp),
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        color = Color(0xFF1E1E1E),
        shadowElevation = 16.dp
    ) {
        Column {
            // 헤더
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DdgoColorTokens.BrandBlue)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DEV Navigation",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "✕",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onDismiss() }
                )
            }

            AiAnalysisModeSection()

            // 라우트 목록
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(devRoutes.indices.toList()) { index ->
                    val route = devRoutes[index]

                    // 섹션 헤더
                    sectionLabels[index]?.let { section ->
                        if (index > 0) Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = section,
                        color = DdgoColorTokens.BrandBlue,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
                        )
                    }

                    Button(
                        onClick = {
                            navigateDev(navController, route)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().height(38.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2D2D2D)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Text(
                            text = route.label,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AiAnalysisModeSection() {
    val selectedMode = DevOptions.aiAnalysisMode

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = "AI Analysis",
                        color = DdgoColorTokens.BrandBlue,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AiModeButton(
                label = "FAST",
                selected = selectedMode == AiAnalysisMode.FAST,
                onClick = { DevOptions.aiAnalysisMode = AiAnalysisMode.FAST },
                modifier = Modifier.weight(1f)
            )
            AiModeButton(
                label = "PHYSICS",
                selected = selectedMode == AiAnalysisMode.PHYSICS,
                onClick = { DevOptions.aiAnalysisMode = AiAnalysisMode.PHYSICS },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AiModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(38.dp),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
                        containerColor = if (selected) DdgoColorTokens.BrandBlue else Color(0xFF2D2D2D),
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 12.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun navigateDev(navController: NavController, route: DevRoute) {
    if (route.clearBackStack) {
        navController.navigate(route.route) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
        }
    } else {
        // upload flow 등 nested graph 안의 route는 해당 graph가 백스택에 없으면
        // 직접 진입이 불가하므로, 안전하게 try-catch로 감싼다.
        try {
            navController.navigate(route.route) {
                launchSingleTop = true
            }
        } catch (e: Exception) {
            // nested graph 밖에서 접근 시 → main_graph 진입 후 재시도
            navController.navigate(ScreenRoutes.MainGraph.route) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
            try {
                navController.navigate(route.route) {
                    launchSingleTop = true
                }
            } catch (_: Exception) {
                // 최종 실패 시 무시
            }
        }
    }
}
