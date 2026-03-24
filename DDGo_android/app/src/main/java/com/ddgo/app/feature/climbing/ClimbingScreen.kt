package com.ddgo.app.feature.climbing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.R
import com.ddgo.app.core.ui.tokens.DdgoColorTokens

class SpeechBubbleShape(
    private val cornerRadius: Dp = 20.dp,
    private val tipSize: Dp = 15.dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val tipSizePx = with(density) { tipSize.toPx() }
        val cornerRadiusPx = with(density) { cornerRadius.toPx() }
        val path = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height - tipSizePx,
                    cornerRadius = CornerRadius(cornerRadiusPx)
                )
            )
            moveTo(size.width / 2f - tipSizePx, size.height - tipSizePx)
            lineTo(size.width / 2f, size.height)
            lineTo(size.width / 2f + tipSizePx, size.height - tipSizePx)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun ClimbingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "클라이밍 배경",
            fontSize = 20.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun ClimbingMenuOverlay(
    onNavigateToUpload: () -> Unit,
    onNavigateToRecord: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .shadow(elevation = 12.dp, shape = SpeechBubbleShape())
            .wrapContentSize(),
        color = Color.White.copy(alpha = 0.95f),
        shape = SpeechBubbleShape()
    ) {
        Column(
            modifier = Modifier
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 35.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            ClimbingMenuItem(
                iconResId = R.drawable.ic_record,
                label = "영상 업로드",
                onClick = {
                    onNavigateToUpload()
                    onDismiss()
                }
            )
            HorizontalDivider(
                modifier = Modifier.width(140.dp),
                thickness = 1.dp,
                color = Color.LightGray.copy(alpha = 0.5f)
            )
            ClimbingMenuItem(
                iconResId = R.drawable.ic_timer,
                label = "실시간 기록",
                onClick = {
                    onNavigateToRecord()
                    onDismiss()
                }
            )
        }
    }
}

@Composable
fun ClimbingMenuItem(
    iconResId: Int,
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = label,
            tint = Color.Unspecified,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = DdgoColorTokens.TextPrimary
        )
    }
}
