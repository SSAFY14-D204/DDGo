package com.ddgo.app.feature.main

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

// 탭 인덱스 상수
object MainTab {
    const val CALENDAR = 0
    const val COMMUNITY = 1
    const val CLIMBING = 2
    const val ANALYSIS = 3
    const val PROFILE = 4
}

// 1. 상단이 볼록하게 튀어나온 커스텀 Shape 정의
class BumpShape(
    private val bumpRadius: Float = 100f,
    private val bumpHeight: Float = 60f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path().apply {
            val width = size.width
            val height = size.height
            val centerX = width / 2f

            moveTo(0f, bumpHeight)
            lineTo(centerX - bumpRadius * 1.2f, bumpHeight)
            cubicTo(
                centerX - bumpRadius * 0.6f, bumpHeight,
                centerX - bumpRadius * 0.8f, 0f,
                centerX, 0f
            )
            cubicTo(
                centerX + bumpRadius * 0.8f, 0f,
                centerX + bumpRadius * 0.6f, bumpHeight,
                centerX + bumpRadius * 1.2f, bumpHeight
            )
            lineTo(width, bumpHeight)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * 공통 하단 네비게이션 바.
 *
 * @param selectedIndex 현재 선택된 탭 인덱스 (MainTab 상수 사용)
 * @param onTabSelected 탭 선택 시 콜백
 */
@Composable
fun CustomBottomNavigationBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit
) {
    val activeColor = Color(0xFF42A5F5)
    val inactiveColor = Color(0xFF788490)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = BumpShape(bumpRadius = 120f, bumpHeight = 50f),
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .background(Color.White, shape = BumpShape(bumpRadius = 120f, bumpHeight = 50f))
            .height(110.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 50.dp),
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

            // 중앙 공간 (클라이밍 버튼이 위에서 겹침)
            Spacer(modifier = Modifier.width(70.dp))

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
                onClick = { onTabSelected(MainTab.PROFILE) }
            )
        }

        // 중앙 클라이밍 버튼 (볼록 위에 겹침)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(
                        if (selectedIndex == MainTab.CLIMBING) Color(0xFF1E88E5) else Color(0xFF42A5F5)
                    )
                    .clickable { onTabSelected(MainTab.CLIMBING) },
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
                color = if (selectedIndex == MainTab.CLIMBING) activeColor else inactiveColor
            )
        }
    }
}

// 개별 네비게이션 아이템 컴포저블
@Composable
fun BottomNavItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    onClick: () -> Unit
) {
    val color = if (isSelected) activeColor else inactiveColor

    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = color,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}