package com.ddgo.app.feature.climbing

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.core.ui.components.SvgAssetImage

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
    Surface(
        modifier = Modifier.size(width = 195.dp, height = 175.dp),
        color = Color.White,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 21.dp, end = 21.dp, top = 16.dp, bottom = 15.dp),
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
            .padding(vertical = 2.dp)
    ) {
        SvgAssetImage(
            assetPath = iconAsset,
            contentDescription = label,
            modifier = Modifier.size(52.dp)
        )
        Spacer(modifier = Modifier.width(20.dp))
        Text(
            text = label,
            style = TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                color = Color(0xFF505050)
            )
        )
    }
}
