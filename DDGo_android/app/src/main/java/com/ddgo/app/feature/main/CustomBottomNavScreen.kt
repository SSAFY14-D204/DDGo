package com.ddgo.app.feature.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object MainTab {
    const val CALENDAR = 0
    const val COMMUNITY = 1
    const val CLIMBING = 2
    const val ANALYSIS = 3
    const val PROFILE = 4
}

object MainChromeDefaults {
    val NavBarHeight = 110.dp
    val ContentBottomPadding = 120.dp
    val OverlayFabBottomPadding = 132.dp
    val MenuOverlayBottomPadding = 110.dp
    val NavBarContentTopPadding = 36.dp
    val NavBarItemHorizontalPadding = 8.dp
    val NavBarItemVerticalPadding = 4.dp
}

class BumpShape(
    private val bumpRadius: Float = 120f,
    private val bumpHeight: Float = 50f
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            val width = size.width
            val height = size.height
            val centerX = width / 2f
            moveTo(0f, bumpHeight)
            lineTo(centerX - bumpRadius * 1.2f, bumpHeight)
            cubicTo(centerX - bumpRadius * 0.6f, bumpHeight, centerX - bumpRadius * 0.8f, 0f, centerX, 0f)
            cubicTo(centerX + bumpRadius * 0.8f, 0f, centerX + bumpRadius * 0.6f, bumpHeight, centerX + bumpRadius * 1.2f, bumpHeight)
            lineTo(width, bumpHeight)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * 1. 하단 바 본체 (배경 + 4개 탭)
 */
@Composable
fun CustomBottomNavBarBase(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToDebug: () -> Unit = {}
) {
    val activeColor = Color(0xFF42A5F5)
    val inactiveColor = Color(0xFF788490)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, shape = BumpShape())
            .background(Color.White, shape = BumpShape())
            .navigationBarsPadding()
            .height(MainChromeDefaults.NavBarHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = MainChromeDefaults.NavBarContentTopPadding),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                icon = Icons.Rounded.DateRange,
                label = "캘린더",
                isSelected = selectedIndex == MainTab.CALENDAR,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                onClick = { onTabSelected(MainTab.CALENDAR) }
            )
            BottomNavItem(
                icon = Icons.Rounded.Chat,
                label = "커뮤니티",
                isSelected = selectedIndex == MainTab.COMMUNITY,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                onClick = { onTabSelected(MainTab.COMMUNITY) }
            )
            Spacer(modifier = Modifier.width(80.dp)) // 중앙 FAB 자리
            BottomNavItem(
                icon = Icons.Rounded.List,
                label = "분석",
                isSelected = selectedIndex == MainTab.ANALYSIS,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                onClick = { onTabSelected(MainTab.ANALYSIS) }
            )
            BottomNavItem(
                icon = Icons.Rounded.Person,
                label = "프로필",
                isSelected = selectedIndex == MainTab.PROFILE,
                activeColor = activeColor,
                inactiveColor = inactiveColor,
                onClick = { onTabSelected(MainTab.PROFILE) },
                onLongClick = onNavigateToDebug
            )
        }
    }
}

/**
 * 2. 중앙 클라이밍 FAB 버튼 (별도 계층)
 */
@Composable
fun ClimbingFloatingButton(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (isSelected) Color(0xFF1E88E5) else Color(0xFF42A5F5))
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.PlayArrow,
                contentDescription = "클라이밍",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "클라이밍",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) Color(0xFF42A5F5) else Color(0xFF788490)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val color = if (isSelected) activeColor else inactiveColor
    Column(
        modifier = Modifier
            .let { m -> if (onLongClick != null) m.combinedClickable(onClick = onClick, onLongClick = onLongClick) else m.clickable(onClick = onClick) }
            .padding(
                horizontal = MainChromeDefaults.NavBarItemHorizontalPadding,
                vertical = MainChromeDefaults.NavBarItemVerticalPadding
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(imageVector = icon, contentDescription = label, tint = color, modifier = Modifier.size(28.dp))
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 12.sp, color = color, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}
