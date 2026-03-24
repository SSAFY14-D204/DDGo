package com.ddgo.app.feature.auth

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ddgo.app.core.ui.theme.PretendardFamily

@Composable
internal fun AuthInlineFeedbackMessage(feedback: AuthFieldFeedback) {
    val textColor = when (feedback.tone) {
        AuthFieldFeedbackTone.Neutral -> Color(0xFF64788D)
        AuthFieldFeedbackTone.Success -> Color(0xFF1E8E5A)
        AuthFieldFeedbackTone.Error -> Color(0xFFD92D20)
    }

    Text(
        text = feedback.message,
        style = TextStyle(
            color = textColor,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = PretendardFamily
        )
    )
}
