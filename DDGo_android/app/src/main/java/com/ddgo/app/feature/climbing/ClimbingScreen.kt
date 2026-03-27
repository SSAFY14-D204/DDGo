package com.ddgo.app.feature.climbing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ddgo.app.core.ui.components.SvgAssetImage

private const val MENU_CARD_ASSET = "file:///android_asset/figma/guide2_menu_card.svg"
private const val MENU_UPLOAD_ICON_ASSET = "file:///android_asset/figma/guide2_upload_icon.svg"
private const val MENU_RECORD_ICON_ASSET = "file:///android_asset/figma/guide2_record_icon.svg"
private const val MENU_DIVIDER_ASSET = "file:///android_asset/figma/guide2_divider.svg"

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
    Box(
        modifier = Modifier.size(width = 227.dp, height = 205.dp)
    ) {
        SvgAssetImage(
            assetPath = MENU_CARD_ASSET,
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 22.dp, end = 22.dp, top = 18.dp, bottom = 31.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ClimbingMenuItem(
                iconAsset = MENU_UPLOAD_ICON_ASSET,
                label = "영상 업로드",
                onClick = {
                    onNavigateToUpload()
                    onDismiss()
                }
            )

            SvgAssetImage(
                assetPath = MENU_DIVIDER_ASSET,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 2.dp, end = 2.dp)
                    .fillMaxWidth()
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
