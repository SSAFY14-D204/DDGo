package com.ddgo.app.feature.community.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.domain.model.CommunityPostSummary
import com.ddgo.app.feature.community.CommunityFeedTab
import com.ddgo.app.feature.community.CommunityPalette
import com.ddgo.app.feature.community.CommunityUiState
import com.ddgo.app.feature.main.MainChromeDefaults
import androidx.compose.foundation.Image as FoundationImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CommunityFeedPage(
    uiState: CommunityUiState,
    onSelectTab: (CommunityFeedTab) -> Unit,
    onKeywordChanged: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onSelectGym: (Long?, String?) -> Unit,
    onOpenPost: (CommunityPostSummary) -> Unit,
    onOpenCompose: () -> Unit,
    onNotificationClick: () -> Unit,
    onLoadMore: () -> Unit
) {
    var isSearchVisible by rememberSaveable { mutableStateOf(false) }
    var isGymFilterVisible by rememberSaveable { mutableStateOf(false) }

    val contentBottomPadding = MainChromeDefaults.ContentBottomPadding

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(CommunityPalette.Surface)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = contentBottomPadding),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        item {
            CommunityFeedHeader(
                onToggleSearch = { isSearchVisible = !isSearchVisible },
                onToggleFilter = { isGymFilterVisible = !isGymFilterVisible },
                onNotificationClick = onNotificationClick
            )
        }

        item {
            CommunityFeedTabRow(
                selectedTab = uiState.selectedFeedTab,
                onSelectTab = onSelectTab
            )
        }

        item {
            CommunityWriteButtonRow(onClick = onOpenCompose)
        }

        if (isSearchVisible) {
            item {
                CommunitySearchSection(
                    keyword = uiState.searchKeyword,
                    onKeywordChanged = onKeywordChanged,
                    onSearchSubmit = onSearchSubmit
                )
            }
        }

        if (isGymFilterVisible) {
            item {
                CommunityGymFilterSection(
                    availableGyms = uiState.availableGyms,
                    selectedGymId = uiState.selectedGymId,
                    onSelectGym = onSelectGym
                )
            }
        }

        item {
            HorizontalDivider(color = CommunityPalette.Border)
        }

        when {
            uiState.isLoadingFeed -> item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = CommunityPalette.AccentStrong
                    )
                }
            }

            uiState.feedError != null -> item {
                CommunityMessageCard(
                    message = uiState.feedError,
                    background = CommunityPalette.DangerSoft,
                    contentColor = CommunityPalette.Danger,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                )
            }

            uiState.posts.isEmpty() -> item {
                CommunityMessageCard(
                    message = "등록된 게시글이 없습니다.",
                    background = CommunityPalette.Surface,
                    contentColor = CommunityPalette.TextSecondary,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                )
            }

            else -> itemsIndexed(
                items = uiState.posts,
                key = { _, post -> post.id }
            ) { index, post ->
                CommunityFeedListItem(
                    post = post,
                    modifier = Modifier.clickable { onOpenPost(post) }
                )
                if (index == uiState.posts.lastIndex &&
                    uiState.hasMoreFeed &&
                    !uiState.isLoadingFeed &&
                    !uiState.isLoadingMoreFeed
                ) {
                    LaunchedEffect(post.id, uiState.posts.size, uiState.hasMoreFeed) {
                        onLoadMore()
                    }
                }
                HorizontalDivider(
                    color = CommunityPalette.Border,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun CommunityFeedHeader(
    onToggleSearch: () -> Unit,
    onToggleFilter: () -> Unit,
    onNotificationClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 16.dp, top = 14.dp, bottom = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "커뮤니티",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = CommunityPalette.TextPrimary
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleSearch) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "검색",
                    tint = CommunityPalette.TextPrimary
                )
            }
            IconButton(onClick = onNotificationClick) {
                Icon(
                    imageVector = Icons.Default.NotificationsNone,
                    contentDescription = "알림",
                    tint = CommunityPalette.TextPrimary
                )
            }
            IconButton(onClick = onToggleFilter) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "필터",
                    tint = CommunityPalette.TextPrimary
                )
            }
        }
    }
}

@Composable
private fun CommunityFeedTabRow(
    selectedTab: CommunityFeedTab,
    onSelectTab: (CommunityFeedTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CommunityFeedTabChip(
            label = "추천",
            selected = selectedTab == CommunityFeedTab.Recommended,
            showDropdown = true,
            onClick = { onSelectTab(CommunityFeedTab.Recommended) }
        )
        CommunityFeedTabChip(
            label = "인기",
            selected = selectedTab == CommunityFeedTab.Popular,
            onClick = { onSelectTab(CommunityFeedTab.Popular) }
        )
        CommunityFeedTabChip(
            label = "최신",
            selected = selectedTab == CommunityFeedTab.Latest,
            onClick = { onSelectTab(CommunityFeedTab.Latest) }
        )
    }
}

@Composable
private fun CommunityFeedTabChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    showDropdown: Boolean = false
) {
    val containerColor = if (selected) CommunityPalette.Accent else Color(0xFFF1F3F5)
    val contentColor = if (selected) CommunityPalette.OnAccent else CommunityPalette.TextPrimary

    Surface(
        modifier = Modifier
            .height(46.dp)
            .wrapContentWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(23.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (showDropdown) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = contentColor
                )
            }
        }
    }
}

@Composable
private fun CommunitySearchSection(
    keyword: String,
    onKeywordChanged: (String) -> Unit,
    onSearchSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = keyword,
            onValueChange = onKeywordChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("제목 또는 내용으로 검색") },
            singleLine = true,
            colors = communityOutlinedTextFieldColors(),
            trailingIcon = {
                Text(
                    text = "검색",
                    color = CommunityPalette.AccentStrong,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.clickable(onClick = onSearchSubmit)
                )
            }
        )
    }
}

@Composable
private fun CommunityGymFilterSection(
    availableGyms: List<Pair<Long, String>>,
    selectedGymId: Long?,
    onSelectGym: (Long?, String?) -> Unit
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CommunityAccentChip(
            text = "전체",
            selected = selectedGymId == null,
            onClick = { onSelectGym(null, null) }
        )
        availableGyms.forEach { (gymId, gymName) ->
            CommunityAccentChip(
                text = gymName,
                selected = selectedGymId == gymId,
                onClick = { onSelectGym(gymId, gymName) }
            )
        }
    }
}

@Composable
private fun CommunityWriteButtonRow(
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.End
    ) {
        CommunityWriteButton(onClick = onClick)
    }
}

@Composable
private fun CommunityWriteButton(
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(23.dp),
        color = CommunityPalette.Surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, CommunityPalette.Accent.copy(alpha = 0.32f)),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = CommunityPalette.AccentStrong
            )
            Text(
                text = "글쓰기",
                color = CommunityPalette.AccentStrong,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommunityFeedCardItem(
    post: CommunityPostSummary,
    modifier: Modifier = Modifier
) {
    val likeColor = CommunityPalette.Danger
    val commentColor = Color(0xFF58AFC2)
    val metaColor = CommunityPalette.TextSecondary
    val createdAtDisplay = formatCommunityFeedTimestamp(post.createdAt)
    val authorDisplay = post.authorNickname.ifBlank { "익명" }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(18.dp),
        color = CommunityPalette.Surface,
        border = BorderStroke(1.dp, CommunityPalette.Border.copy(alpha = 0.7f)),
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = post.title,
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
                fontWeight = FontWeight.Bold,
                color = CommunityPalette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = post.contentPreview.ifBlank { "내용이 없습니다." },
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
                color = CommunityPalette.TextPrimary.copy(alpha = 0.74f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CommunityFeedMetricChip(
                    icon = if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    value = post.likeCount.toString(),
                    tint = likeColor
                )
                CommunityFeedMetricChip(
                    icon = Icons.AutoMirrored.Filled.Comment,
                    value = post.commentCount.toString(),
                    tint = commentColor
                )
                CommunityFeedMetaText(
                    text = createdAtDisplay,
                    color = metaColor
                )
                CommunityFeedMetaText(
                    text = authorDisplay,
                    color = metaColor
                )
            }
        }
    }
}

@Composable
private fun CommunityFeedMetricChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    tint: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            color = tint,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun CommunityFeedMetaText(
    text: String,
    color: Color
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
        color = color
    )
}

@Composable
private fun CommunityFeedListItemCard(
    post: CommunityPostSummary,
    modifier: Modifier = Modifier
) {
    CommunityFeedCardItem(
        post = post,
        modifier = modifier
    )
    return
}

    /*

    val likeColor = CommunityPalette.Danger
    val commentColor = Color(0xFF58AFC2)
    val metaColor = CommunityPalette.TextSecondary
    val createdAtDisplay = formatCommunityFeedTimestamp(post.createdAt)
    val authorDisplay = post.authorNickname.ifBlank { "익명" }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        shape = RoundedCornerShape(18.dp),
        color = CommunityPalette.Surface,
        border = BorderStroke(1.dp, CommunityPalette.Border.copy(alpha = 0.7f)),
        shadowElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
        Text(
            text = post.title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
            fontWeight = FontWeight.Bold,
            color = CommunityPalette.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = post.contentPreview.ifBlank { "내용이 없습니다." },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
            color = CommunityPalette.TextPrimary.copy(alpha = 0.74f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${post.createdAt} 조회 ${post.viewCount}",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                color = metaColor
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CommunityMetric(
                    icon = {
                        Icon(
                            imageVector = if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = CommunityPalette.TextPrimary.copy(alpha = 0.82f)
                        )
                    },
                    value = post.likeCount.toString()
                )
                CommunityMetric(
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Comment,
                            contentDescription = null,
                            tint = CommunityPalette.TextPrimary.copy(alpha = 0.82f)
                        )
                    },
                    value = post.commentCount.toString()
                )
            }
        }
    }
}

    */

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommunityFeedListItem(
    post: CommunityPostSummary,
    modifier: Modifier = Modifier
) {
    val likeColor = CommunityPalette.Danger
    val commentColor = Color(0xFF58AFC2)
    val metaColor = CommunityPalette.TextSecondary
    val createdAtDisplay = formatCommunityFeedTimestamp(post.createdAt)
    val thumbnailUrl = post.thumbnailUrl?.takeIf { it.isNotBlank() }
    val authorDisplay = post.authorNickname.ifBlank { "익명" }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(CommunityPalette.Surface)
            .padding(horizontal = 22.dp, vertical = 18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = if (thumbnailUrl != null) 110.dp else 0.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
            text = post.title,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 18.sp),
            fontWeight = FontWeight.Bold,
            color = CommunityPalette.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = post.contentPreview.ifBlank { "내용이 없습니다." },
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
            color = CommunityPalette.TextPrimary.copy(alpha = 0.74f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            CommunityFeedMetricChip(
                icon = if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                value = post.likeCount.toString(),
                tint = likeColor
            )
            CommunityFeedMetricChip(
                icon = Icons.AutoMirrored.Filled.Comment,
                value = post.commentCount.toString(),
                tint = commentColor
            )
            CommunityFeedMetaText(
                text = createdAtDisplay,
                color = metaColor
            )
            CommunityFeedMetaText(
                text = authorDisplay,
                color = metaColor
            )
        }
        }
        if (thumbnailUrl != null) {
            CommunityFeedThumbnail(
                thumbnailUrl = thumbnailUrl,
                contentDescription = post.title,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun CommunityFeedThumbnail(
    thumbnailUrl: String,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val request = remember(thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(thumbnailUrl)
            .crossfade(true)
            .build()
    }
    val painter = rememberAsyncImagePainter(model = request)

    Box(
        modifier = modifier
            .size(width = 96.dp, height = 72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CommunityPalette.SurfaceMuted),
        contentAlignment = Alignment.Center
    ) {
        when (painter.state) {
            is AsyncImagePainter.State.Success -> {
                FoundationImage(
                    painter = painter,
                    contentDescription = contentDescription,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            is AsyncImagePainter.State.Error -> {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = CommunityPalette.TextSecondary.copy(alpha = 0.84f),
                    modifier = Modifier.size(28.dp)
                )
            }

            else -> {
                androidx.compose.material3.CircularProgressIndicator(
                    color = CommunityPalette.AccentStrong,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun CommunityMetric(
    icon: @Composable () -> Unit,
    value: String
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 15.sp),
            color = CommunityPalette.TextPrimary.copy(alpha = 0.82f)
        )
    }
}
