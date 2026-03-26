package com.ddgo.app.feature.climbing.upload.ui.analysis.organism

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.ddgo.app.core.ui.tokens.DdgoColorTokens
import com.ddgo.app.feature.climbing.upload.AnalysisCardColor
import com.ddgo.app.feature.climbing.upload.AnalysisFailure
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisPanelColor
import com.ddgo.app.feature.climbing.upload.AnalysisPrimary
import com.ddgo.app.feature.climbing.upload.AnalysisText
import com.ddgo.app.feature.climbing.upload.FinalAnalysisBodyLoadDistribution
import com.ddgo.app.feature.climbing.upload.FinalAnalysisJointLoadSummary
import com.ddgo.app.feature.climbing.upload.FinalAnalysisUnknownMetricText
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.AnalysisAccentText
import com.ddgo.app.feature.climbing.upload.ui.analysis.molecule.analysisSurfaceBrushFor

@Composable
internal fun AttemptBodyLoadMapCard(
    distribution: FinalAnalysisBodyLoadDistribution?,
    loadFocusLabel: String?,
    topJointLoads: List<FinalAnalysisJointLoadSummary>,
    loadFocusValue: String,
    loadFocusCaption: String,
    loadFocusAccentColor: Color,
    modifier: Modifier = Modifier
) {
    val displayJointLoads = remember(topJointLoads) {
        topJointLoads.mapNotNull(::toDisplayJointLoad).take(3)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(AnalysisPanelColor)
            .padding(horizontal = 16.dp, vertical = 18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "신체 부위별 부하",
                color = AnalysisText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BodyLoadFigure(
                    title = "전면",
                    view = BodyLoadView.FRONT,
                    distribution = distribution,
                    topJointLoads = displayJointLoads,
                    modifier = Modifier.weight(1f)
                )
                BodyLoadFigure(
                    title = "후면",
                    view = BodyLoadView.BACK,
                    distribution = distribution,
                    topJointLoads = displayJointLoads,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BodyLoadLegendItem(
                        title = "왼팔",
                        value = distribution?.leftArm,
                        modifier = Modifier.weight(1f)
                    )
                    BodyLoadLegendItem(
                        title = "오른팔",
                        value = distribution?.rightArm,
                        modifier = Modifier.weight(1f)
                    )
                }

                BodyLoadLegendItem(
                    title = "몸통",
                    value = distribution?.torso,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BodyLoadLegendItem(
                        title = "왼다리",
                        value = distribution?.leftLeg,
                        modifier = Modifier.weight(1f)
                    )
                    BodyLoadLegendItem(
                        title = "오른다리",
                        value = distribution?.rightLeg,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            BodyLoadFocusInset(
                value = loadFocusValue,
                caption = loadFocusCaption,
                accentColor = loadFocusAccentColor
            )

            if (displayJointLoads.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "상위 관절 부하",
                        color = AnalysisText,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    displayJointLoads.forEach { joint ->
                        JointLoadRow(joint = joint)
                    }
                }
            }
        }
    }
}

@Composable
private fun BodyLoadFocusInset(
    value: String,
    caption: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    val surfaceBrush = analysisSurfaceBrushFor(accentColor)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(brush = surfaceBrush ?: androidx.compose.ui.graphics.Brush.horizontalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.18f),
                    accentColor.copy(alpha = 0.08f)
                )
            ))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 42.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(accentColor)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "부담 집중 부위",
                color = AnalysisMuted,
                fontSize = 12.sp
            )
            Text(
                text = value,
                color = AnalysisText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = caption,
                color = AnalysisText.copy(alpha = 0.86f),
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun BodyLoadFigure(
    title: String,
    view: BodyLoadView,
    distribution: FinalAnalysisBodyLoadDistribution?,
    topJointLoads: List<DisplayJointLoad>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .height(220.dp)
                .aspectRatio(315f / 734f)
        ) {
            BodyLoadSvgLayer(
                assetPath = view.baseAssetPath,
                modifier = Modifier.matchParentSize()
            )

            BodyLoadRegion.entries.forEach { region ->
                val value = distribution.valueFor(region)
                val tint = bodyLoadTint(value)
                if (tint.alpha > 0f) {
                    BodyLoadSvgLayer(
                        assetPath = view.assetPathFor(region),
                        tint = tint,
                        modifier = Modifier.matchParentSize()
                    )
                }
            }

            BodyLoadSvgLayer(
                assetPath = view.guidesAssetPath,
                modifier = Modifier.matchParentSize()
            )

            val markers = remember(topJointLoads, view) {
                topJointLoads
                    .map { joint ->
                        jointMarkerFor(
                            descriptor = joint.descriptor,
                            intensityPercent = joint.intensityPercent,
                            view = view
                        )
                    }
                    .sortedBy { it.displayNumber }
            }

            markers.forEach { marker ->
                JointMarkerDot(
                    marker = marker,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Text(
            text = title,
            color = AnalysisMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun BodyLoadSvgLayer(
    assetPath: String,
    modifier: Modifier = Modifier,
    tint: Color? = null
) {
    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data("file:///android_asset/$assetPath")
            .decoderFactory(SvgDecoder.Factory())
            .build()
    )

    Image(
        painter = painter,
        contentDescription = null,
        modifier = modifier,
        contentScale = ContentScale.Fit,
        colorFilter = tint?.let(ColorFilter::tint)
    )
}

@Composable
private fun BodyLoadLegendItem(
    title: String,
    value: Int?,
    modifier: Modifier = Modifier
) {
    val dotColor = bodyLoadTint(value)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AnalysisCardColor)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(dotColor.takeIf { it.alpha > 0f } ?: AnalysisMuted.copy(alpha = 0.4f))
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                color = AnalysisMuted,
                fontSize = 12.sp
            )
            if (value != null) {
                Text(
                    text = "$value%",
                    color = AnalysisText,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            } else {
                Text(
                    text = FinalAnalysisUnknownMetricText,
                    color = AnalysisText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun JointLoadRow(
    joint: DisplayJointLoad,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AnalysisCardColor)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(jointLoadTint(joint.intensityPercent))
        ) {
            Text(
                text = joint.displayNumber.toString(),
                color = Color.Black,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        Text(
            text = joint.label,
            color = AnalysisText,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )

        Text(
            text = "${joint.intensityPercent}%",
            color = AnalysisText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

private enum class BodyLoadView(
    val baseAssetPath: String,
    val guidesAssetPath: String
) {
    FRONT(
        baseAssetPath = "body_load/front_base.svg",
        guidesAssetPath = "body_load/front_guides.svg"
    ),
    BACK(
        baseAssetPath = "body_load/back_base.svg",
        guidesAssetPath = "body_load/back_guides.svg"
    );

    fun assetPathFor(region: BodyLoadRegion): String = when (this) {
        FRONT -> when (region) {
            BodyLoadRegion.LEFT_ARM -> "body_load/front_right_arm.svg"
            BodyLoadRegion.RIGHT_ARM -> "body_load/front_left_arm.svg"
            BodyLoadRegion.TORSO -> "body_load/front_torso.svg"
            BodyLoadRegion.LEFT_LEG -> "body_load/front_right_leg.svg"
            BodyLoadRegion.RIGHT_LEG -> "body_load/front_left_leg.svg"
        }

        BACK -> when (region) {
            BodyLoadRegion.LEFT_ARM -> "body_load/back_left_arm.svg"
            BodyLoadRegion.RIGHT_ARM -> "body_load/back_right_arm.svg"
            BodyLoadRegion.TORSO -> "body_load/back_torso.svg"
            BodyLoadRegion.LEFT_LEG -> "body_load/back_left_leg.svg"
            BodyLoadRegion.RIGHT_LEG -> "body_load/back_right_leg.svg"
        }
    }
}

private enum class BodyLoadRegion(val displayName: String) {
    LEFT_ARM("왼팔"),
    RIGHT_ARM("오른팔"),
    TORSO("몸통"),
    LEFT_LEG("왼다리"),
    RIGHT_LEG("오른다리")
}

private data class BodyLoadMapInsight(
    val summaryLine: String
)

private fun buildBodyLoadMapInsight(
    distribution: FinalAnalysisBodyLoadDistribution?,
    loadFocusLabel: String?
): BodyLoadMapInsight {
    val rankedRegions = distribution
        ?.toRankedRegions()
        .orEmpty()

    return BodyLoadMapInsight(
        summaryLine = buildBodyLoadSummaryLine(
            rankedRegions = rankedRegions,
            loadFocusLabel = loadFocusLabel
        )
    )

    val summaryLine = when {
        rankedRegions.isEmpty() && !loadFocusLabel.isNullOrBlank() ->
            "이번 시도에서는 $loadFocusLabel 쪽 부담이 두드러졌어요."

        rankedRegions.isEmpty() ->
            "이번 시도의 신체 부하 데이터가 아직 충분하지 않아요."

        rankedRegions.size == 1 ->
            "이번 시도에서는 ${rankedRegions.first().first.displayName}에 부담이 가장 크게 몰렸어요."

        rankedRegions.first().second - rankedRegions[1].second >= 12 ->
            "이번 시도에서는 ${rankedRegions.first().first.displayName}에 부담이 가장 크게 몰렸어요."

        else ->
            "이번 시도에서는 ${rankedRegions.first().first.displayName}과 ${rankedRegions[1].first.displayName}에 부담이 크게 몰렸어요."
    }

    return BodyLoadMapInsight(summaryLine = summaryLine)
}

private fun buildBodyLoadSummaryLine(
    rankedRegions: List<Pair<BodyLoadRegion, Int>>,
    loadFocusLabel: String?
): String {
    if (rankedRegions.isEmpty()) {
        return if (!loadFocusLabel.isNullOrBlank()) {
            "이번 시도에서는 $loadFocusLabel 쪽 부담이 상대적으로 컸어요."
        } else {
            "이번 시도에서는 특정 부위에 부담이 크게 치우치진 않았어요."
        }
    }

    val primary = rankedRegions.first()
    val secondary = rankedRegions.getOrNull(1)

    if (secondary == null) {
        return if (primary.second >= 72) {
            "이번 시도에서는 ${primary.first.displayName}에 부담이 가장 크게 몰렸어요."
        } else {
            "이번 시도에서는 ${primary.first.displayName}에 부담이 상대적으로 컸어요."
        }
    }

    val gap = primary.second - secondary.second

    return when {
        primary.second >= 72 && gap >= 16 ->
            "이번 시도에서는 ${primary.first.displayName}에 부담이 가장 크게 몰렸어요."

        primary.second >= 58 && gap >= 10 ->
            "이번 시도에서는 ${primary.first.displayName}에 부담이 더 크게 실렸어요."

        gap <= 6 ->
            "이번 시도에서는 ${primary.first.displayName}과 ${secondary.first.displayName}에 부담이 비슷하게 분산됐어요."

        else ->
            "이번 시도에서는 ${primary.first.displayName}과 ${secondary.first.displayName} 쪽 부담이 상대적으로 컸어요."
    }
}

private fun FinalAnalysisBodyLoadDistribution?.valueFor(region: BodyLoadRegion): Int? {
    return this?.let {
        when (region) {
            BodyLoadRegion.LEFT_ARM -> it.leftArm
            BodyLoadRegion.RIGHT_ARM -> it.rightArm
            BodyLoadRegion.TORSO -> it.torso
            BodyLoadRegion.LEFT_LEG -> it.leftLeg
            BodyLoadRegion.RIGHT_LEG -> it.rightLeg
        }
    }
}

private fun FinalAnalysisBodyLoadDistribution.toRankedRegions(): List<Pair<BodyLoadRegion, Int>> {
    return listOf(
        BodyLoadRegion.LEFT_ARM to leftArm,
        BodyLoadRegion.RIGHT_ARM to rightArm,
        BodyLoadRegion.TORSO to torso,
        BodyLoadRegion.LEFT_LEG to leftLeg,
        BodyLoadRegion.RIGHT_LEG to rightLeg
    )
        .filter { it.second > 0 }
        .sortedByDescending { it.second }
}

private fun bodyLoadTint(value: Int?): Color {
    if (value == null || value <= 0) return Color.Transparent
    return steppedLoadColor(value)
}

private enum class JointSide {
    LEFT,
    RIGHT
}

private enum class JointKind {
    SHOULDER,
    ELBOW,
    HIP,
    KNEE,
    ANKLE
}

private data class JointDescriptor(
    val side: JointSide,
    val kind: JointKind,
    val displayNumber: Int
)

private data class DisplayJointLoad(
    val label: String,
    val intensityPercent: Int,
    val descriptor: JointDescriptor
) {
    val displayNumber: Int = descriptor.displayNumber
}

private data class JointAnchor(
    val xFraction: Float,
    val yFraction: Float
)

private fun toDisplayJointLoad(
    joint: FinalAnalysisJointLoadSummary
): DisplayJointLoad? {
    val descriptor = jointDescriptorFor(joint.label) ?: return null
    return DisplayJointLoad(
        label = joint.label,
        intensityPercent = joint.intensityPercent,
        descriptor = descriptor
    )
}

private fun jointDescriptorFor(label: String): JointDescriptor? {
    val side = when {
        label.contains("왼쪽") -> JointSide.LEFT
        label.contains("오른쪽") -> JointSide.RIGHT
        else -> return null
    }

    val kind = when {
        label.contains("어깨") -> JointKind.SHOULDER
        label.contains("팔꿈치") -> JointKind.ELBOW
        label.contains("고관절") -> JointKind.HIP
        label.contains("무릎") -> JointKind.KNEE
        label.contains("발목") -> JointKind.ANKLE
        else -> return null
    }

    val displayNumber = when (kind) {
        JointKind.SHOULDER -> if (side == JointSide.RIGHT) 1 else 2
        JointKind.ELBOW -> if (side == JointSide.RIGHT) 3 else 4
        JointKind.HIP -> if (side == JointSide.RIGHT) 5 else 6
        JointKind.KNEE -> if (side == JointSide.RIGHT) 7 else 8
        JointKind.ANKLE -> if (side == JointSide.RIGHT) 9 else 10
    }

    return JointDescriptor(
        side = side,
        kind = kind,
        displayNumber = displayNumber
    )
}

private fun jointDisplayOrder(label: String): Int? {
    val isLeft = label.contains("왼")
    val isRight = label.contains("오른")

    return when {
        label.contains("어깨") -> if (isLeft) 1 else if (isRight) 2 else null
        label.contains("팔꿈치") -> if (isLeft) 3 else if (isRight) 4 else null
        label.contains("고관절") -> if (isLeft) 5 else if (isRight) 6 else null
        label.contains("무릎") -> if (isLeft) 7 else if (isRight) 8 else null
        label.contains("발목") -> if (isLeft) 9 else if (isRight) 10 else null
        else -> null
    }
}

private fun jointLoadTint(intensityPercent: Int): Color {
    return steppedLoadColor(intensityPercent)
}

private fun steppedLoadColor(percent: Int): Color {
    val step = (percent.coerceIn(0, 100) / 10)
    return when (step) {
        0 -> Color(0xFFFFF1F1)
        1 -> Color(0xFFFFE2E2)
        2 -> Color(0xFFFFD0D0)
        3 -> Color(0xFFFFB8B8)
        4 -> Color(0xFFFF9C9C)
        5 -> Color(0xFFFF8080)
        6 -> Color(0xFFFF6666)
        7 -> Color(0xFFFF4D4D)
        8 -> Color(0xFFF03A3A)
        9 -> Color(0xFFD72A2A)
        else -> Color(0xFFB71C1C)
    }
}

private data class JointMarker(
    val xFraction: Float,
    val yFraction: Float,
    val intensityPercent: Int,
    val displayNumber: Int
)

private fun jointMarkerFor(
    descriptor: JointDescriptor,
    intensityPercent: Int,
    view: BodyLoadView
): JointMarker {
    val anchor = when (view) {
        BodyLoadView.FRONT -> when (descriptor.kind) {
            JointKind.SHOULDER ->
                if (descriptor.side == JointSide.RIGHT) JointAnchor(0.24f, 0.19f) else JointAnchor(0.76f, 0.19f)
            JointKind.ELBOW ->
                if (descriptor.side == JointSide.RIGHT) JointAnchor(0.18f, 0.38f) else JointAnchor(0.82f, 0.38f)
            JointKind.HIP ->
                if (descriptor.side == JointSide.RIGHT) JointAnchor(0.39f, 0.53f) else JointAnchor(0.61f, 0.53f)
            JointKind.KNEE ->
                if (descriptor.side == JointSide.RIGHT) JointAnchor(0.39f, 0.75f) else JointAnchor(0.61f, 0.75f)
            JointKind.ANKLE ->
                if (descriptor.side == JointSide.RIGHT) JointAnchor(0.35f, 0.95f) else JointAnchor(0.65f, 0.95f)
        }

        BodyLoadView.BACK -> when (descriptor.kind) {
            JointKind.SHOULDER ->
                if (descriptor.side == JointSide.LEFT) JointAnchor(0.24f, 0.19f) else JointAnchor(0.76f, 0.19f)
            JointKind.ELBOW ->
                if (descriptor.side == JointSide.LEFT) JointAnchor(0.18f, 0.38f) else JointAnchor(0.82f, 0.38f)
            JointKind.HIP ->
                if (descriptor.side == JointSide.LEFT) JointAnchor(0.39f, 0.53f) else JointAnchor(0.61f, 0.53f)
            JointKind.KNEE ->
                if (descriptor.side == JointSide.LEFT) JointAnchor(0.39f, 0.75f) else JointAnchor(0.61f, 0.75f)
            JointKind.ANKLE ->
                if (descriptor.side == JointSide.LEFT) JointAnchor(0.35f, 0.95f) else JointAnchor(0.65f, 0.95f)
        }
    }

    return JointMarker(
        xFraction = anchor.xFraction,
        yFraction = anchor.yFraction,
        intensityPercent = intensityPercent,
        displayNumber = descriptor.displayNumber
    )
}

private fun jointMarkerFor(
    label: String,
    intensityPercent: Int,
    view: BodyLoadView,
    rank: Int
): JointMarker? {
    val isLeft = label.contains("왼쪽")
    val isRight = label.contains("오른쪽")

    return when {
        label.contains("어깨") -> when (view) {
            BodyLoadView.FRONT -> JointMarker(
                xFraction = if (isLeft) 0.29f else if (isRight) 0.71f else 0.50f,
                yFraction = 0.19f,
                intensityPercent = intensityPercent,
                displayNumber = rank
            )
            BodyLoadView.BACK -> JointMarker(
                xFraction = if (isLeft) 0.29f else if (isRight) 0.71f else 0.50f,
                yFraction = 0.19f,
                intensityPercent = intensityPercent,
                displayNumber = rank
            )
        }

        label.contains("팔꿈치") -> when (view) {
            BodyLoadView.FRONT -> JointMarker(
                xFraction = if (isLeft) 0.22f else if (isRight) 0.78f else 0.50f,
                yFraction = 0.35f,
                intensityPercent = intensityPercent,
                displayNumber = rank
            )
            BodyLoadView.BACK -> JointMarker(
                xFraction = if (isLeft) 0.22f else if (isRight) 0.78f else 0.50f,
                yFraction = 0.33f,
                intensityPercent = intensityPercent,
                displayNumber = rank
            )
        }

        label.contains("손목") -> when (view) {
            BodyLoadView.FRONT -> JointMarker(
                xFraction = if (isLeft) 0.84f else if (isRight) 0.16f else 0.50f,
                yFraction = 0.59f,
                intensityPercent = intensityPercent,
                displayNumber = rank
            )
            BodyLoadView.BACK -> JointMarker(
                xFraction = if (isLeft) 0.18f else if (isRight) 0.82f else 0.50f,
                yFraction = 0.59f,
                intensityPercent = intensityPercent,
                displayNumber = rank
            )
        }

        label.contains("고관절") -> when (view) {
            BodyLoadView.FRONT -> JointMarker(
                xFraction = if (isLeft) 0.47f else if (isRight) 0.53f else 0.50f,
                yFraction = 0.47f,
                intensityPercent = intensityPercent,
                displayNumber = rank
            )
            BodyLoadView.BACK -> JointMarker(
                xFraction = if (isLeft) 0.50f else if (isRight) 0.52f else 0.51f,
                yFraction = 0.46f,
                intensityPercent = intensityPercent,
                displayNumber = rank
            )
        }

        label.contains("무릎") -> when (view) {
            BodyLoadView.FRONT -> JointMarker(
                xFraction = if (isLeft) 0.47f else if (isRight) 0.55f else 0.50f,
                yFraction = 0.74f,
                intensityPercent = intensityPercent,
                displayNumber = rank
            )
            BodyLoadView.BACK -> JointMarker(
                xFraction = if (isLeft) 0.47f else if (isRight) 0.55f else 0.50f,
                yFraction = 0.74f,
                intensityPercent = intensityPercent,
                displayNumber = rank
            )
        }

        label.contains("발목") -> when (view) {
            BodyLoadView.FRONT -> JointMarker(
                xFraction = if (isLeft) 0.47f else if (isRight) 0.55f else 0.50f,
                yFraction = 0.91f,
                intensityPercent = intensityPercent,
                displayNumber = rank
            )
            BodyLoadView.BACK -> JointMarker(
                xFraction = if (isLeft) 0.45f else if (isRight) 0.55f else 0.50f,
                yFraction = 0.92f,
                intensityPercent = intensityPercent,
                displayNumber = rank
            )
        }

        else -> null
    }
}

@Composable
private fun JointMarkerDot(
    marker: JointMarker,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val dotSize = when {
            marker.intensityPercent >= 80 -> 18.dp
            marker.intensityPercent >= 60 -> 16.dp
            else -> 14.dp
        }
        val xOffset = maxWidth * marker.xFraction
        val yOffset = maxHeight * marker.yFraction

        Box(
            modifier = Modifier
                .offset(x = xOffset - dotSize / 2, y = yOffset - dotSize / 2)
                .size(dotSize)
                .clip(RoundedCornerShape(999.dp))
                .background(DdgoColorTokens.Warning.copy(alpha = 0.98f))
        ) {
            Text(
                text = marker.displayNumber.toString(),
                color = Color.Black,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
