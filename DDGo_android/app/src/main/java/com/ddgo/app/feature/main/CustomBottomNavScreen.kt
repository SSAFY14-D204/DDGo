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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.R
import com.ddgo.app.core.ui.tokens.DdgoColorTokens

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

private data class MainNavItemSpec(
    val iconResId: Int,
    val label: String,
    val tab: Int
)

private val mainNavItems = listOf(
    MainNavItemSpec(R.drawable.ic_calendar, "캘린더", MainTab.CALENDAR),
    MainNavItemSpec(R.drawable.ic_community, "커뮤니티", MainTab.COMMUNITY),
    MainNavItemSpec(R.drawable.ic_records, "분석", MainTab.ANALYSIS),
    MainNavItemSpec(R.drawable.ic_my_page, "프로필", MainTab.PROFILE)
)

class BumpShape(
    private val bumpRadius: Float = 120f,
    private val bumpHeight: Float = 50f
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path().apply {
            val width = size.width
            val centerX = width / 2f
            moveTo(0f, bumpHeight)
            lineTo(centerX - bumpRadius * 1.2f, bumpHeight)
            cubicTo(centerX - bumpRadius * 0.6f, bumpHeight, centerX - bumpRadius * 0.8f, 0f, centerX, 0f)
            cubicTo(centerX + bumpRadius * 0.8f, 0f, centerX + bumpRadius * 0.6f, bumpHeight, centerX + bumpRadius * 1.2f, bumpHeight)
            lineTo(width, bumpHeight)
            lineTo(width, size.height)
            lineTo(0f, size.height)
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
    val activeColor = DdgoColorTokens.BrandBlue
    val inactiveColor = DdgoColorTokens.BrandGray

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
            mainNavItems.take(2).forEach { item ->
                BottomNavItem(
                    iconResId = item.iconResId,
                    label = item.label,
                    isSelected = selectedIndex == item.tab,
                    activeColor = activeColor,
                    inactiveColor = inactiveColor,
                    onClick = { onTabSelected(item.tab) }
                )
            }

            Spacer(modifier = Modifier.width(80.dp))

            mainNavItems.drop(2).forEach { item ->
                BottomNavItem(
                    iconResId = item.iconResId,
                    label = item.label,
                    isSelected = selectedIndex == item.tab,
                    activeColor = activeColor,
                    inactiveColor = inactiveColor,
                    onClick = { onTabSelected(item.tab) },
                    onLongClick = if (item.tab == MainTab.PROFILE) onNavigateToDebug else null
                )
            }
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
        modifier = modifier.padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(if (isSelected) DdgoColorTokens.BrandBlueStrong else DdgoColorTokens.BrandBlue)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_climbing),
                contentDescription = "클라이밍",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "클라이밍",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (isSelected) DdgoColorTokens.BrandBlue else DdgoColorTokens.BrandGray
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BottomNavItem(
    iconResId: Int,
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
            .let { base ->
                if (onLongClick != null) {
                    base.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    base.clickable(onClick = onClick)
                }
            }
            .padding(
                horizontal = MainChromeDefaults.NavBarItemHorizontalPadding,
                vertical = MainChromeDefaults.NavBarItemVerticalPadding
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = iconResId),
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(23.dp)
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
