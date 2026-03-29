package com.ddgo.app.feature.auth

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.ddgo.app.core.ui.theme.PretendardFamily

private val DdgoWordmarkBlack = Color(0xFF111319)

@Composable
internal fun DdgoKoreanWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 64.sp,
    color: Color = DdgoWordmarkBlack
) {
    Text(
        text = "디디고",
        modifier = modifier,
        style = TextStyle(
            fontFamily = PretendardFamily,
            fontWeight = FontWeight.Black,
            fontSize = fontSize,
            letterSpacing = (-1.5).sp,
            color = color
        )
    )
}
