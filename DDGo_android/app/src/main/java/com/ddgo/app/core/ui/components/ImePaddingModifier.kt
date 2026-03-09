package com.ddgo.app.core.ui.components

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 키보드가 올라왔을 때 하단 패딩을 조정해주는 재사용 가능한 Modifier.
 * 키보드의 높이 변화에 맞추어 버튼 위치를 동적으로 조절합니다.
 *
 * @param keyboardOpenPadding 키보드가 열렸을 때 적용할 하단 여백 (기본값: 16.dp)
 * @param keyboardClosePadding 키보드가 닫혔을 때 적용할 하단 여백 (기본값: 40.dp)
 */
@OptIn(ExperimentalLayoutApi::class)
fun Modifier.keyboardAwareBottomPadding(
    keyboardOpenPadding: Dp = 16.dp,
    keyboardClosePadding: Dp = 40.dp
): Modifier = composed {
    val isImeVisible = WindowInsets.isImeVisible
    val bottomPadding = if (isImeVisible) keyboardOpenPadding else keyboardClosePadding

    this
        .padding(bottom = bottomPadding)
        .imePadding()
}
