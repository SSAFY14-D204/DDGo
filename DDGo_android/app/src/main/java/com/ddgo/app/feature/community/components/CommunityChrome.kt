package com.ddgo.app.feature.community.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ddgo.app.feature.community.CommunityPalette

internal val CommunityPagePadding = 20.dp
internal val CommunitySectionSpacing = 18.dp
internal val CommunityCardRadius = 28.dp
internal val CommunityChipRadius = 999.dp
internal val CommunityComposeFieldRadius = 16.dp
internal val CommunityComposeAttachmentRadius = 14.dp
internal val CommunityComposeHeaderPadding = 18.dp
internal val CommunityComposeSectionSpacing = 20.dp
internal val CommunityDetailAvatarSize = 42.dp
internal val CommunityDetailHeaderHeight = 72.dp
internal val CommunityDetailMediaRadius = 18.dp
internal val CommunityDetailComposerHeight = 74.dp
internal val CommunityCardBorder = BorderStroke(1.dp, CommunityPalette.Border)
internal val CommunitySubtleBorder = BorderStroke(1.dp, CommunityPalette.Border.copy(alpha = 0.72f))

internal enum class CommunityChipTone {
    Neutral,
    Accent,
    Success,
    Warning,
    Danger
}

@Composable
internal fun CommunityPageShell(
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CommunityPalette.Surface)
    ) {
        content()
    }
}

@Composable
internal fun CommunityTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailingContent: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "뒤로 가기",
                        tint = CommunityPalette.TextPrimary
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                subtitle?.let {
                    Text(
                        text = it,
                        color = CommunityPalette.AccentStrong,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = title,
                    color = CommunityPalette.TextPrimary,
                    style = if (subtitle == null) {
                        MaterialTheme.typography.headlineLarge
                    } else {
                        MaterialTheme.typography.headlineMedium
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = trailingContent
        )
    }
}

@Composable
internal fun CommunityComposeTopBar(
    title: String = "커뮤니티 글쓰기",
    onClose: () -> Unit,
    onDone: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxWidth()
    ) {
        CommunityCloseIconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.CenterStart)
        )

        Text(
            text = title,
            modifier = Modifier.align(Alignment.Center),
            color = CommunityPalette.TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        if (onDone != null) {
            CommunityHeaderActionTextButton(
                text = "완료",
                onClick = onDone,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
internal fun CommunityCloseIconButton(
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.size(40.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "닫기",
            tint = CommunityPalette.TextPrimary.copy(alpha = if (enabled) 1f else 0.4f)
        )
    }
}

@Composable
internal fun CommunityHeaderActionTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentColor: Color = CommunityPalette.AccentStrong
) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(CommunityChipRadius))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        color = contentColor,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center
    )
}

@Composable
internal fun CommunityDetailTopBarChrome(
    title: String,
    onBack: () -> Unit,
    onMore: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "뒤로 가기",
                tint = CommunityPalette.TextPrimary
            )
        }

        Text(
            text = title,
            color = CommunityPalette.TextPrimary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        if (onMore != null) {
            IconButton(onClick = onMore) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "더보기",
                    tint = CommunityPalette.TextPrimary
                )
            }
        } else {
            Spacer(modifier = Modifier.size(40.dp))
        }
    }
}

@Composable
internal fun CommunityDetailAuthorRowChrome(
    authorName: String,
    metaText: String,
    modifier: Modifier = Modifier,
    avatarContent: @Composable BoxScope.() -> Unit = {
        CommunityAccentChip(
            text = authorName.firstOrNull()?.uppercaseChar()?.toString() ?: "?",
            containerColor = CommunityPalette.AccentSoft,
            contentColor = CommunityPalette.AccentStrong,
            borderColor = CommunityPalette.AccentStrong.copy(alpha = 0.18f)
        )
    },
    trailingContent: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = CommunityPalette.SurfaceMuted,
                border = BorderStroke(1.dp, CommunityPalette.Border),
                modifier = Modifier.size(CommunityDetailAvatarSize)
            ) {
                Box(contentAlignment = Alignment.Center, content = avatarContent)
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = authorName,
                    color = CommunityPalette.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = metaText,
                    color = CommunityPalette.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = trailingContent
        )
    }
}

@Composable
internal fun CommunityDetailTagChip(
    text: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(CommunityChipRadius),
        color = if (highlighted) CommunityPalette.AccentStrong else CommunityPalette.AccentSoft,
        border = BorderStroke(
            1.dp,
            if (highlighted) CommunityPalette.AccentStrong.copy(alpha = 0.18f) else CommunityPalette.AccentStrong.copy(alpha = 0.12f)
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = if (highlighted) CommunityPalette.OnAccent else CommunityPalette.AccentStrong,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun CommunityDetailSectionDivider(
    modifier: Modifier = Modifier
) {
    HorizontalDivider(
        modifier = modifier,
        color = CommunityPalette.Border.copy(alpha = 0.78f)
    )
}

@Composable
internal fun CommunityDetailMediaSurface(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CommunityDetailMediaRadius),
        color = CommunityPalette.SurfaceMuted,
        border = BorderStroke(1.dp, CommunityPalette.Border.copy(alpha = 0.72f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 220.dp)
                .clip(RoundedCornerShape(CommunityDetailMediaRadius)),
            content = content
        )
    }
}

@Composable
internal fun CommunityDetailComposerSurface(
    modifier: Modifier = Modifier,
    placeholder: String = "댓글을 입력하세요",
    trailingContent: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CommunityDetailComposerHeight),
        color = CommunityPalette.SurfaceMuted,
        border = BorderStroke(1.dp, CommunityPalette.Border),
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = placeholder,
                modifier = Modifier.weight(1f),
                color = CommunityPalette.TextHint,
                style = MaterialTheme.typography.bodyLarge
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = trailingContent
            )
        }
    }
}

@Composable
internal fun CommunityComposeSectionDivider(
    modifier: Modifier = Modifier
) {
    HorizontalDivider(
        modifier = modifier,
        color = CommunityPalette.Border.copy(alpha = 0.72f)
    )
}

@Composable
internal fun CommunityComposeFieldSurface(
    modifier: Modifier = Modifier,
    background: Color = CommunityPalette.SurfaceMuted.copy(alpha = 0.58f),
    border: BorderStroke = BorderStroke(1.dp, CommunityPalette.Border.copy(alpha = 0.66f)),
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CommunityComposeFieldRadius),
        color = background,
        border = border
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
internal fun CommunityComposeChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = true,
    onClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    containerColor: Color = if (selected) CommunityPalette.AccentStrong else CommunityPalette.SurfaceMuted,
    contentColor: Color = if (selected) CommunityPalette.OnAccent else CommunityPalette.TextSecondary,
    borderColor: Color = if (selected) CommunityPalette.AccentStrong.copy(alpha = 0.18f) else CommunityPalette.Border
) {
    Surface(
        modifier = if (onClick != null) {
            modifier.clickable(onClick = onClick)
        } else {
            modifier
        },
        color = containerColor,
        shape = RoundedCornerShape(CommunityChipRadius),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(18.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "항목 제거",
                        tint = contentColor
                    )
                }
            }
        }
    }
}

@Composable
internal fun CommunityComposeAttachmentTile(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    Surface(
        modifier = if (onClick != null) {
            modifier.clickable(onClick = onClick)
        } else {
            modifier
        },
        shape = RoundedCornerShape(CommunityComposeAttachmentRadius),
        color = CommunityPalette.SurfaceMuted.copy(alpha = 0.58f),
        border = BorderStroke(1.dp, CommunityPalette.Border.copy(alpha = 0.62f))
    ) {
        Box(
            modifier = Modifier.clip(RoundedCornerShape(CommunityComposeAttachmentRadius)),
            content = content
        )
    }
}

@Composable
internal fun CommunityComposeSearchFieldChrome(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    placeholder: String = "암장 검색",
    trailingContent: @Composable (() -> Unit)? = null
) {
    CommunityComposeFieldSurface(
        modifier = if (onClick != null) {
            modifier.clickable(enabled = enabled, onClick = onClick)
        } else {
            modifier
        },
        background = CommunityPalette.SurfaceMuted.copy(alpha = 0.58f),
        contentPadding = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = placeholder,
                modifier = Modifier.weight(1f),
                color = CommunityPalette.TextHint,
                style = MaterialTheme.typography.bodyLarge
            )
            trailingContent?.invoke()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CommunityHeroCard(
    title: String,
    description: String,
    chips: List<String>,
    modifier: Modifier = Modifier,
    kicker: String? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CommunityCardRadius),
        color = Color.Transparent,
        shadowElevation = 12.dp
    ) {
        Box(
            modifier = Modifier
                .background(CommunityPalette.HeroGradient)
                .padding(22.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                kicker?.let {
                    Text(
                        text = it,
                        color = CommunityPalette.OnAccent.copy(alpha = 0.84f),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    text = title,
                    color = CommunityPalette.OnAccent,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    color = CommunityPalette.OnAccent.copy(alpha = 0.84f),
                    style = MaterialTheme.typography.bodyMedium
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    chips.forEach { chip ->
                        CommunityAccentChip(
                            text = chip,
                            selected = true,
                            borderColor = CommunityPalette.OnAccent.copy(alpha = 0.22f),
                            containerColor = CommunityPalette.OnAccent.copy(alpha = 0.16f),
                            contentColor = CommunityPalette.OnAccent
                        )
                    }
                }
                footer?.invoke(this)
            }
        }
    }
}

@Composable
internal fun CommunityHeroMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = CommunityPalette.OnAccent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, CommunityPalette.OnAccent.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                color = CommunityPalette.OnAccent.copy(alpha = 0.78f),
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = value,
                color = CommunityPalette.OnAccent,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
internal fun CommunityAccentChip(
    text: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    containerColor: Color = if (selected) CommunityPalette.AccentSoft else CommunityPalette.Surface,
    contentColor: Color = if (selected) CommunityPalette.AccentStrong else CommunityPalette.TextSecondary,
    borderColor: Color = if (selected) CommunityPalette.AccentStrong else CommunityPalette.Border
) {
    Surface(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        color = containerColor,
        shape = RoundedCornerShape(CommunityChipRadius),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
internal fun CommunityStatChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: CommunityChipTone = CommunityChipTone.Neutral
) {
    val (containerColor, contentColor, borderColor) = chipToneColors(tone)
    CommunityAccentChip(
        text = text,
        modifier = modifier,
        selected = true,
        containerColor = containerColor,
        contentColor = contentColor,
        borderColor = borderColor
    )
}

@Composable
internal fun CommunitySectionCard(
    modifier: Modifier = Modifier,
    background: Color = CommunityPalette.Surface,
    border: BorderStroke = CommunityCardBorder,
    contentPadding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(CommunityCardRadius),
        color = background,
        border = border,
        shadowElevation = 6.dp
    ) {
        Column(Modifier.padding(contentPadding), content = content)
    }
}

@Composable
internal fun CommunitySectionHeader(
    title: String,
    subtitle: String? = null,
    trailingContent: @Composable RowScope.() -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                color = CommunityPalette.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            subtitle?.let {
                Text(
                    text = it,
                    color = CommunityPalette.TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = trailingContent
        )
    }
}

@Composable
internal fun CommunityInfoRow(
    label: String,
    value: String,
    showDivider: Boolean = true
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = CommunityPalette.TextSecondary,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = value,
                color = CommunityPalette.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        if (showDivider) {
            HorizontalDivider(color = CommunityPalette.Border.copy(alpha = 0.8f))
        }
    }
}

@Composable
internal fun CommunityMessageCard(
    message: String,
    background: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = background),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, CommunityPalette.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            color = contentColor
        )
    }
}

@Composable
internal fun communityOutlinedTextFieldColors(): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = CommunityPalette.Surface,
        unfocusedContainerColor = CommunityPalette.Surface,
        disabledContainerColor = CommunityPalette.Surface,
        focusedBorderColor = CommunityPalette.AccentStrong,
        unfocusedBorderColor = CommunityPalette.Border,
        focusedTextColor = CommunityPalette.TextPrimary,
        unfocusedTextColor = CommunityPalette.TextPrimary,
        cursorColor = CommunityPalette.AccentStrong,
        focusedPlaceholderColor = CommunityPalette.TextHint,
        unfocusedPlaceholderColor = CommunityPalette.TextHint,
        focusedLeadingIconColor = CommunityPalette.AccentStrong,
        unfocusedLeadingIconColor = CommunityPalette.TextHint,
        focusedTrailingIconColor = CommunityPalette.AccentStrong,
        unfocusedTrailingIconColor = CommunityPalette.TextHint
    )
}

@Composable
internal fun communityComposeTextFieldColors(): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = CommunityPalette.SurfaceMuted.copy(alpha = 0.58f),
        unfocusedContainerColor = CommunityPalette.SurfaceMuted.copy(alpha = 0.58f),
        disabledContainerColor = CommunityPalette.SurfaceMuted.copy(alpha = 0.58f),
        focusedBorderColor = CommunityPalette.AccentStrong,
        unfocusedBorderColor = CommunityPalette.Border.copy(alpha = 0.62f),
        focusedTextColor = CommunityPalette.TextPrimary,
        unfocusedTextColor = CommunityPalette.TextPrimary,
        cursorColor = CommunityPalette.AccentStrong,
        focusedPlaceholderColor = CommunityPalette.TextHint,
        unfocusedPlaceholderColor = CommunityPalette.TextHint,
        focusedLeadingIconColor = CommunityPalette.TextHint,
        unfocusedLeadingIconColor = CommunityPalette.TextHint,
        focusedTrailingIconColor = CommunityPalette.TextHint,
        unfocusedTrailingIconColor = CommunityPalette.TextHint
    )
}

private fun chipToneColors(tone: CommunityChipTone): Triple<Color, Color, Color> {
    return when (tone) {
        CommunityChipTone.Neutral -> Triple(
            CommunityPalette.SurfaceMuted,
            CommunityPalette.TextSecondary,
            CommunityPalette.Border
        )

        CommunityChipTone.Accent -> Triple(
            CommunityPalette.AccentSoft,
            CommunityPalette.AccentStrong,
            CommunityPalette.AccentStrong.copy(alpha = 0.5f)
        )

        CommunityChipTone.Success -> Triple(
            CommunityPalette.SuccessSoft,
            CommunityPalette.Success,
            CommunityPalette.Success.copy(alpha = 0.24f)
        )

        CommunityChipTone.Warning -> Triple(
            CommunityPalette.WarningSoft,
            CommunityPalette.Warning,
            CommunityPalette.Warning.copy(alpha = 0.24f)
        )

        CommunityChipTone.Danger -> Triple(
            CommunityPalette.DangerSoft,
            CommunityPalette.Danger,
            CommunityPalette.Danger.copy(alpha = 0.24f)
        )
    }
}
