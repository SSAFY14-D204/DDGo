package com.ddgo.app.feature.climbing

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.R

private const val MENU_UPLOAD_ICON_ASSET = "file:///android_asset/figma/guide2_upload_icon.svg"
private const val MENU_RECORD_ICON_ASSET = "file:///android_asset/figma/guide2_record_icon.svg"

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
    Box(contentAlignment = Alignment.TopCenter) {
        Surface(
            modifier = Modifier.size(width = 228.dp, height = 182.dp),
            color = Color.White,
            shape = RoundedCornerShape(18.dp),
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 15.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ClimbingMenuItem(
                    iconAsset = MENU_UPLOAD_ICON_ASSET,
                    label = "영상 업로드",
                    onClick = {
                        onNavigateToUpload()
                        onDismiss()
                    }
                )

                Box(
                    modifier = Modifier
                        .height(1.dp)
                        .fillMaxWidth()
                        .background(Color(0xFFE6E8EC))
                )

                ClimbingMenuItem(
                    iconAsset = MENU_RECORD_ICON_ASSET,
                    label = "실시간 기록",
                    onClick = {
                        onNavigateToRecord()
                        onDismiss()
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .offset(y = 166.dp)
                .size(18.dp)
                .graphicsLayer { rotationZ = 45f }
                .background(Color.White, RoundedCornerShape(4.dp))
        )
    }
}

@Composable
private fun ClimbingMenuItem(
    iconAsset: String,
    label: String,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    ) {
        ClimbingMenuLeadingIcon(
            iconAsset = iconAsset,
            contentDescription = label
        )
        Spacer(modifier = Modifier.width(18.dp))
        Text(
            text = label,
            style = TextStyle(
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF505050)
            )
        )
    }
}

@Composable
private fun ClimbingMenuLeadingIcon(
    iconAsset: String,
    contentDescription: String
) {
    val backgroundColor = if (iconAsset == MENU_UPLOAD_ICON_ASSET) {
        Color(0xFFFFD0CF)
    } else {
        Color(0xFFD9FFDB)
    }
    val iconRes = if (iconAsset == MENU_UPLOAD_ICON_ASSET) {
        R.drawable.ic_record
    } else {
        R.drawable.ic_timer
    }
    val iconSize = if (iconAsset == MENU_UPLOAD_ICON_ASSET) 20.dp else 18.dp

    Surface(
        modifier = Modifier.size(54.dp),
        shape = RoundedCornerShape(27.dp),
        color = backgroundColor
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = contentDescription,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
