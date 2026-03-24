package com.ddgo.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PasswordResetHeroCard(
    stage: PasswordResetStage,
    hasIncomingToken: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF10243E),
                            Color(0xFF2383E2)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column {
                Surface(
                    color = Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(999.dp)
                ) {
                    Text(
                        text = PasswordResetCopy.heroBadge(stage),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = PasswordResetTextStyles.HeroBadge
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                DdgoKoreanWordmark(
                    fontSize = 40.sp,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = PasswordResetCopy.heroTitle(stage, hasIncomingToken),
                    style = PasswordResetTextStyles.HeroTitle
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = PasswordResetCopy.heroDescription(stage, hasIncomingToken),
                    style = PasswordResetTextStyles.HeroDescription
                )
            }
        }
    }
}

@Composable
internal fun PasswordResetProgressRow(stage: PasswordResetStage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PasswordResetProgressItem(
            modifier = Modifier.weight(1f),
            step = 1,
            label = PasswordResetCopy.ProgressRequestMail,
            status = if (stage == PasswordResetStage.RequestEmail) {
                PasswordResetStepStatus.Active
            } else {
                PasswordResetStepStatus.Completed
            }
        )

        PasswordResetProgressItem(
            modifier = Modifier.weight(1f),
            step = 2,
            label = PasswordResetCopy.ProgressResetPassword,
            status = if (stage == PasswordResetStage.UpdatePassword) {
                PasswordResetStepStatus.Active
            } else {
                PasswordResetStepStatus.Upcoming
            }
        )
    }
}

@Composable
internal fun PasswordResetStageCard(
    eyebrow: String,
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = eyebrow,
                style = PasswordResetTextStyles.StageEyebrow
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = title,
                style = PasswordResetTextStyles.StageTitle
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = subtitle,
                style = PasswordResetTextStyles.StageSubtitle
            )

            Spacer(modifier = Modifier.height(20.dp))

            content()
        }
    }
}

@Composable
internal fun PasswordResetMailStatusCard(
    requestedEmail: String?,
    hasIncomingToken: Boolean,
    canResend: Boolean,
    isLoading: Boolean,
    onResend: () -> Unit,
    onEditEmail: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFEAF5FF),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Text(
                text = PasswordResetCopy.statusTitle(requestedEmail),
                style = PasswordResetTextStyles.StatusTitle
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = PasswordResetCopy.statusBody(requestedEmail, hasIncomingToken),
                style = PasswordResetTextStyles.StatusBody
            )

            if (canResend) {
                Spacer(modifier = Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onResend,
                        enabled = !isLoading,
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text(
                            text = PasswordResetCopy.ResendResetMailAction,
                            style = PasswordResetTextStyles.SecondaryAction
                        )
                    }

                    TextButton(
                        onClick = onEditEmail,
                        enabled = !isLoading,
                        contentPadding = ButtonDefaults.TextButtonContentPadding
                    ) {
                        Text(
                            text = PasswordResetCopy.EditEmailAction,
                            style = PasswordResetTextStyles.TertiaryAction
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun PasswordResetTokenDetectedCard(onEditLink: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFF4F8FC),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = PasswordResetCopy.TokenDetectedTitle,
                style = PasswordResetTextStyles.StatusTitle
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = PasswordResetCopy.TokenDetectedDescription,
                style = PasswordResetTextStyles.StatusBody
            )

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = onEditLink,
                contentPadding = ButtonDefaults.TextButtonContentPadding
            ) {
                Text(
                    text = PasswordResetCopy.EditLinkAction,
                    style = PasswordResetTextStyles.SecondaryAction
                )
            }
        }
    }
}

@Composable
private fun PasswordResetProgressItem(
    modifier: Modifier = Modifier,
    step: Int,
    label: String,
    status: PasswordResetStepStatus
) {
    val containerColor = when (status) {
        PasswordResetStepStatus.Active -> Color(0xFFDEF0FF)
        PasswordResetStepStatus.Completed -> Color(0xFFE9F8EF)
        PasswordResetStepStatus.Upcoming -> Color.White.copy(alpha = 0.92f)
    }
    val accentColor = when (status) {
        PasswordResetStepStatus.Active -> Color(0xFF2383E2)
        PasswordResetStepStatus.Completed -> Color(0xFF18A957)
        PasswordResetStepStatus.Upcoming -> Color(0xFF8A97A6)
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(accentColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = step.toString(),
                    style = PasswordResetTextStyles.ProgressStepNumber
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column {
                Text(
                    text = when (status) {
                        PasswordResetStepStatus.Completed -> PasswordResetCopy.ProgressCompleted
                        PasswordResetStepStatus.Active -> PasswordResetCopy.ProgressActive
                        PasswordResetStepStatus.Upcoming -> PasswordResetCopy.ProgressUpcoming
                    },
                    style = PasswordResetTextStyles.ProgressStatus.copy(color = accentColor)
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = label,
                    style = PasswordResetTextStyles.ProgressLabel
                )
            }
        }
    }
}

internal enum class PasswordResetStepStatus {
    Active,
    Completed,
    Upcoming
}
