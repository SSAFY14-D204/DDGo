package com.ddgo.app.feature.auth

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ddgo.app.core.ui.theme.PretendardFamily

@Composable
internal fun AuthInlineErrorMessage(message: String) {
    Text(
        text = message,
        style = TextStyle(
            color = Color(0xFFD92D20),
            fontSize = 13.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = PretendardFamily
        )
    )
}
