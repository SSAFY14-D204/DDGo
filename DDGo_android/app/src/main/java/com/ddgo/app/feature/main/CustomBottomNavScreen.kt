package com.ddgo.app.feature.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Chat
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.R

object MainTab {
    const val CALENDAR = 0
    const val COMMUNITY = 1
    const val CLIMBING = 2
    const val ANALYSIS = 3
    const val PROFILE = 4
}

object MainChromeDefaults {
    val NavBarHeight = 108.dp
    val ContentBottomPadding = 118.dp
    val FloatingButtonBottomPadding = 36.dp
    val MenuOverlayBottomPadding = 138.dp
}

private class BumpShape(
    private val bumpRadius: Float = 120f,
    private val bumpHeight: Float = 52f
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            val width = size.width
            val height = size.height
            val centerX = width / 2f

            moveTo(0f, bumpHeight)
            lineTo(centerX - bumpRadius * 1.18f, bumpHeight)
            cubicTo(
                centerX - bumpRadius * 0.82f,
                bumpHeight,
                centerX - bumpRadius * 0.78f,
                0f,
                centerX,
                0f
            )
            cubicTo(
                centerX + bumpRadius * 0.78f,
                0f,
                centerX + bumpRadius * 0.82f,
                bumpHeight,
                centerX + bumpRadius * 1.18f,
                bumpHeight
            )
            lineTo(width, bumpHeight)
            lineTo(width, height)
            lineTo(0f, height)
            close()
        }
        return Outline.Generic(path)
    }
}

@Composable
fun CustomBottomNavBarBase(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateToDebug: () -> Unit = {}
) {
    val activeColor = Color(0xFF4396FB)
    val inactiveColor = Color(0xFF595C63)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .shadow(10.dp, shape = BumpShape())
            .background(Color.White, shape = BumpShape())
            .height(MainChromeDefaults.NavBarHeight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 52.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomNavItem(
                icon = Icons.Rounded.CalendarMonth,
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
            Spacer(modifier = Modifier.width(86.dp))
            BottomNavItem(
                icon = Icons.Rounded.BarChart,
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

@Composable
fun ClimbingFloatingButton(
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(94.dp)
                .shadow(18.dp, CircleShape)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.92f)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1995FF))
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "클라이밍 메뉴 닫기",
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    Image(
                        painter = painterResource(id = R.drawable.ic_ddgo_mark_fill),
                        contentDescription = "클라이밍 열기",
                        modifier = Modifier.size(38.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "클라이밍",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) Color(0xFF4396FB) else Color(0xFF595C63)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BottomNavItem(
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
            .let { currentModifier ->
                if (onLongClick != null) {
                    currentModifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                } else {
                    currentModifier.clickable(onClick = onClick)
                }
            }
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(26.dp)
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
