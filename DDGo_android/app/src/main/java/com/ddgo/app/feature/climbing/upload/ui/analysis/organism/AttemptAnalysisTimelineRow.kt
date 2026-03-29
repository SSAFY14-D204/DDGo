package com.ddgo.app.feature.climbing.upload.ui.analysis.organism

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddgo.app.R
import com.ddgo.app.domain.model.AnalysisPoint
import com.ddgo.app.domain.model.AnalysisPointKind
import com.ddgo.app.feature.climbing.upload.AnalysisCardColor
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisPanelColor
import com.ddgo.app.feature.climbing.upload.AnalysisPrimary
import com.ddgo.app.feature.climbing.upload.AnalysisText
import com.ddgo.app.feature.climbing.upload.toVideoTimeString
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.analysisAccentBrushFor
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.analysisSurfaceBrushFor

@Composable
internal fun AttemptAnalysisTimelineRow(
    points: List<AnalysisPoint>,
    selectedTimeMs: Long?,
    onPointSelected: (AnalysisPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    if (points.isEmpty()) {
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(AnalysisPanelColor)
            .padding(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "핵심 장면",
            modifier = Modifier.padding(horizontal = 16.dp),
            color = AnalysisText,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )

        if (points.size <= 3) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                points.forEach { point ->
                    AttemptTimelineCard(
                        point = point,
                        isSelected = selectedTimeMs == point.timeMs,
                        onClick = { onPointSelected(point) },
                        useFixedWidth = false,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(points) { _, point ->
                    AttemptTimelineCard(
                        point = point,
                        isSelected = selectedTimeMs == point.timeMs,
                        onClick = { onPointSelected(point) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AttemptTimelineCard(
    point: AnalysisPoint,
    isSelected: Boolean,
    onClick: () -> Unit,
    useFixedWidth: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isBoundaryPoint = point.kind == AnalysisPointKind.PERSON_OBSERVATION_START ||
        point.kind == AnalysisPointKind.CLIMB_END
    val accentColor = if (point.kind == AnalysisPointKind.CLIMB_END) {
        Color(0xFFFFB357)
    } else {
        AnalysisPrimary
    }
    val accentBrush = analysisAccentBrushFor(accentColor)

    Column(
        modifier = modifier
            .then(if (useFixedWidth) Modifier.width(148.dp) else Modifier)
            .clip(RoundedCornerShape(18.dp))
            .background(AnalysisCardColor)
            .then(
                if (isSelected && accentBrush != null) {
                    Modifier.border(
                        width = 1.5.dp,
                        brush = accentBrush,
                        shape = RoundedCornerShape(18.dp)
                    )
                } else {
                    Modifier.border(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) {
                            accentColor.copy(alpha = 0.72f)
                        } else {
                            Color.White.copy(alpha = 0.05f)
                        },
                        shape = RoundedCornerShape(18.dp)
                    )
                }
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        brush = analysisSurfaceBrushFor(accentColor)
                            ?: androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = if (isSelected) 0.26f else 0.18f),
                                    accentColor.copy(alpha = if (isSelected) 0.16f else 0.10f)
                                )
                            )
                    )
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    text = point.timeMs.toVideoTimeString(),
                    color = AnalysisText,
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            if (isBoundaryPoint) {
                Image(
                    painter = painterResource(
                        id = if (point.kind == AnalysisPointKind.CLIMB_END) {
                            R.drawable.end_flag
                        } else {
                            R.drawable.start_flag
                        }
                    ),
                    contentDescription = if (point.kind == AnalysisPointKind.CLIMB_END) {
                        "핵심 장면 종료"
                    } else {
                        "핵심 장면 시작"
                    },
                    modifier = Modifier.size(width = 20.dp, height = 24.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .then(
                            if (isSelected && accentBrush != null) {
                                Modifier.background(brush = accentBrush)
                            } else {
                                Modifier.background(if (isSelected) accentColor else Color.Transparent)
                            }
                        )
                        .border(
                            width = if (isSelected) 0.dp else 1.dp,
                            color = if (isSelected) Color.Transparent else AnalysisMuted.copy(alpha = 0.35f),
                            shape = RoundedCornerShape(999.dp)
                        )
                )
            }
        }

        Text(
            text = point.description,
            color = AnalysisText,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            lineHeight = 20.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
