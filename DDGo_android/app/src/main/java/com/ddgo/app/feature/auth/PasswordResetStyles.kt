package com.ddgo.app.feature.auth

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.ddgo.app.core.ui.theme.PretendardFamily

internal object PasswordResetTextStyles {
    val HeroBadge = TextStyle(
        fontFamily = PretendardFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )

    val HeroTitle = TextStyle(
        fontFamily = PretendardFamily,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )

    val HeroDescription = TextStyle(
        fontFamily = PretendardFamily,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        color = Color.White.copy(alpha = 0.82f)
    )

    val StageEyebrow = TextStyle(
        fontFamily = PretendardFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF2383E2)
    )

    val StageTitle = TextStyle(
        fontFamily = PretendardFamily,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF1E232C)
    )

    val StageSubtitle = TextStyle(
        fontFamily = PretendardFamily,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        color = Color(0xFF607080)
    )

    val StatusTitle = TextStyle(
        fontFamily = PretendardFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF1E232C)
    )

    val StatusBody = TextStyle(
        fontFamily = PretendardFamily,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = Color(0xFF4D5D6C)
    )

    val SecondaryAction = TextStyle(
        fontFamily = PretendardFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF2383E2)
    )

    val TertiaryAction = TextStyle(
        fontFamily = PretendardFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF607080)
    )

    val GuideTitle = TextStyle(
        fontFamily = PretendardFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF1E232C)
    )

    val GuideChip = TextStyle(
        fontFamily = PretendardFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFF4D5D6C)
    )

    val InputText = TextStyle(
        fontFamily = PretendardFamily,
        fontSize = 15.sp
    )

    val PrimaryButton = TextStyle(
        fontFamily = PretendardFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = Color.White
    )

    val ProgressStepNumber = TextStyle(
        fontFamily = PretendardFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White
    )

    val ProgressStatus = TextStyle(
        fontFamily = PretendardFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold
    )

    val ProgressLabel = TextStyle(
        fontFamily = PretendardFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF1E232C)
    )
}
