package com.ddgo.app.feature.community.components

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayCircle
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
import com.ddgo.app.core.ui.tokens.DdgoColorTokens
import com.ddgo.app.domain.model.CommunityPostSummary
import com.ddgo.app.feature.community.CommunityFeedTab
import com.ddgo.app.feature.community.CommunityPalette
import com.ddgo.app.feature.community.CommunityUiState
import com.ddgo.app.feature.main.MainChromeDefaults
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
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
    val contentBottomPadding = MainChromeDefaults.ContentBottomPadding

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CommunityPalette.Surface)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(
                top = 20.dp,
                bottom = contentBottomPadding + 88.dp
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
        item {
            CommunityFeedHeader(
                keyword = uiState.searchKeyword,
                onKeywordChanged = onKeywordChanged,
                onSearchSubmit = onSearchSubmit,
                onNotificationClick = onNotificationClick
            )
        }

        item {
            CommunityGymFilterSection(
                selectedTab = uiState.selectedFeedTab,
                availableGyms = uiState.availableGyms,
                selectedGymId = uiState.selectedGymId,
                onSelectTab = onSelectTab,
                onSelectGym = onSelectGym
            )
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
                    message = "\uB4F1\uB85D\uB41C \uAC8C\uC2DC\uAE00\uC774 \uC5C6\uC2B5\uB2C8\uB2E4.",
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

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 20.dp,
                    bottom = MainChromeDefaults.OverlayFabBottomPadding
                )
                .navigationBarsPadding()
        ) {
            CommunityWriteButton(onClick = onOpenCompose)
        }
    }
}

@Composable
private fun CommunityFeedHeader(
    keyword: String,
    onKeywordChanged: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onNotificationClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 2.dp)
        ) {
            Text(
                text = "\uCEE4\uBBA4\uB2C8\uD2F0",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = CommunityPalette.TextPrimary,
                modifier = Modifier.align(Alignment.TopStart)
            )
            Row(
                modifier = Modifier.align(Alignment.TopEnd),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNotificationClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.NotificationsNone,
                        contentDescription = "\uC54C\uB9BC",
                        tint = CommunityPalette.TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        CommunitySearchSection(
            keyword = keyword,
            onKeywordChanged = onKeywordChanged,
            onSearchSubmit = onSearchSubmit
        )
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
            .padding(horizontal = 22.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = keyword,
            onValueChange = onKeywordChanged,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("\uC81C\uBAA9 \uB610\uB294 \uB0B4\uC6A9\uC73C\uB85C \uAC80\uC0C9") },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
            colors = communityOutlinedTextFieldColors(),
            trailingIcon = {
                Text(
                    text = "\uAC80\uC0C9",
                    color = CommunityPalette.AccentStrong,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clickable(onClick = onSearchSubmit)
                )
            }
        )
    }
}

@Composable
private fun CommunityGymFilterSection(
    selectedTab: CommunityFeedTab,
    availableGyms: List<Pair<Long, String>>,
    selectedGymId: Long?,
    onSelectTab: (CommunityFeedTab) -> Unit,
    onSelectGym: (Long?, String?) -> Unit
) {
    val collapsedTagCount = 6
    var isExpanded by rememberSaveable(availableGyms.size, selectedGymId) { mutableStateOf(false) }
    val collapsedGyms = remember(availableGyms, selectedGymId) {
        val selectedGym = availableGyms.firstOrNull { it.first == selectedGymId }
        buildList {
            if (selectedGym != null) {
                add(selectedGym)
            }
            availableGyms
                .asSequence()
                .filterNot { it.first == selectedGymId }
                .take(if (selectedGym != null) collapsedTagCount - 1 else collapsedTagCount)
                .forEach { add(it) }
        }
    }
    val visibleGyms = if (isExpanded || availableGyms.size <= collapsedTagCount) {
        availableGyms
    } else {
        collapsedGyms
    }
    val hiddenGymCount = (availableGyms.size - visibleGyms.size).coerceAtLeast(0)

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CommunityAccentChip(
            text = "\uC804\uCCB4",
            selected = selectedTab != CommunityFeedTab.Popular && selectedGymId == null,
            onClick = {
                onSelectTab(CommunityFeedTab.Latest)
                onSelectGym(null, null)
            }
        )
        CommunityAccentChip(
            text = "\uC778\uAE30",
            selected = selectedTab == CommunityFeedTab.Popular,
            onClick = {
                onSelectTab(CommunityFeedTab.Popular)
                onSelectGym(null, null)
            }
        )
        visibleGyms.forEach { (gymId, gymName) ->
            CommunityAccentChip(
                text = gymName,
                selected = selectedGymId == gymId,
                onClick = { onSelectGym(gymId, gymName) }
            )
        }
        if (availableGyms.size > collapsedTagCount) {
            CommunityAccentChip(
                text = if (isExpanded) {
                    "\uC811\uAE30"
                } else {
                    "\uB354\uBCF4\uAE30 +$hiddenGymCount"
                },
                selected = false,
                onClick = { isExpanded = !isExpanded },
                containerColor = CommunityPalette.Surface,
                contentColor = CommunityPalette.TextSecondary,
                borderColor = CommunityPalette.Border
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
        shape = RoundedCornerShape(21.dp),
        color = DdgoColorTokens.BrandBlue,
        border = null,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "\uAE00\uC4F0\uAE30",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
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
    val metaColor = CommunityPalette.TextSecondary
    val createdAtDisplay = formatCommunityFeedTimestamp(post.createdAt)
    val authorDisplay = post.authorNickname.ifBlank { "\uC775\uBA85" }

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
                text = post.contentPreview.ifBlank { "\uB0B4\uC6A9\uC774 \uC5C6\uC2B5\uB2C8\uB2E4." },
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
                    tint = metaColor
                )
                CommunityFeedMetricChip(
                    icon = Icons.AutoMirrored.Filled.Comment,
                    value = post.commentCount.toString(),
                    tint = metaColor
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
            tint = tint,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
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
        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
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

    val metaColor = CommunityPalette.TextSecondary
    val createdAtDisplay = formatCommunityFeedTimestamp(post.createdAt)
    val authorDisplay = post.authorNickname.ifBlank { "\uC775\uBA85" }

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
            text = post.contentPreview.ifBlank { "\uB0B4\uC6A9\uC774 \uC5C6\uC2B5\uB2C8\uB2E4." },
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
                text = "${post.createdAt} 議고쉶 ${post.viewCount}",
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
    val metaColor = CommunityPalette.TextSecondary
    val createdAtDisplay = formatCommunityFeedTimestamp(post.createdAt)
    val hasVideo = post.videoCount > 0
    val thumbnailUrl = post.thumbnailUrl?.takeIf { it.isNotBlank() }
    val authorName = post.authorNickname.ifBlank { "\uC775\uBA85" }
    val tagLabel = post.gymName?.takeIf { it.isNotBlank() }
    val authorMetaLine = "$authorName \u00B7 $createdAtDisplay \u00B7 \uC870\uD68C ${post.viewCount}"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CommunityPalette.Surface)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (tagLabel != null) {
                    CommunityPostTagBadge(text = tagLabel)
                }
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 16.sp),
                    fontWeight = FontWeight.Bold,
                    color = CommunityPalette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = post.contentPreview.ifBlank { "\uB0B4\uC6A9\uC774 \uC5C6\uC2B5\uB2C8\uB2E4." },
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp),
                    color = CommunityPalette.TextPrimary.copy(alpha = 0.74f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (hasVideo) {
                CommunityFeedThumbnail(
                    thumbnailUrl = thumbnailUrl,
                    contentDescription = post.title,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = authorMetaLine,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = metaColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier.padding(start = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CommunityFeedMetricChip(
                    icon = if (post.isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    value = post.likeCount.toString(),
                    tint = metaColor
                )
                CommunityFeedMetricChip(
                    icon = Icons.AutoMirrored.Filled.Comment,
                    value = post.commentCount.toString(),
                    tint = metaColor
                )
            }
        }
    }
}

@Composable
private fun CommunityPostTagBadge(
    text: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = DdgoColorTokens.BrandBlue.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, DdgoColorTokens.BrandBlue.copy(alpha = 0.18f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            color = DdgoColorTokens.BrandBlue,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CommunityFeedThumbnail(
    thumbnailUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var imageState by remember(thumbnailUrl) {
        mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty)
    }
    val request = remember(thumbnailUrl) {
        ImageRequest.Builder(context)
            .data(thumbnailUrl)
            .crossfade(true)
            .build()
    }

    Box(
        modifier = modifier
            .width(104.dp)
            .height(78.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CommunityPalette.SurfaceMuted),
        contentAlignment = Alignment.Center
    ) {
        if (thumbnailUrl != null) {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onState = { state -> imageState = state }
            )
        }

        when (imageState) {
            is AsyncImagePainter.State.Error -> {
                Icon(
                    imageVector = Icons.Default.PlayCircle,
                    contentDescription = null,
                    tint = CommunityPalette.TextSecondary.copy(alpha = 0.84f),
                    modifier = Modifier.size(24.dp)
                )
            }

            is AsyncImagePainter.State.Success -> Unit
            else -> {
                if (thumbnailUrl != null) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = CommunityPalette.AccentStrong,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.24f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
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
