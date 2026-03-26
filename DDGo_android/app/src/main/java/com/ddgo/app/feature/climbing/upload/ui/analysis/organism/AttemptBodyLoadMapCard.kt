package com.ddgo.app.feature.climbing.upload.ui.analysis.organism

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.rememberAsyncImagePainter
import coil.decode.SvgDecoder
import coil.request.ImageRequest
import com.ddgo.app.R
import com.ddgo.app.feature.climbing.upload.AnalysisCardColor
import com.ddgo.app.feature.climbing.upload.AnalysisFailure
import com.ddgo.app.feature.climbing.upload.AnalysisMuted
import com.ddgo.app.feature.climbing.upload.AnalysisPanelColor
import com.ddgo.app.feature.climbing.upload.AnalysisText
import com.ddgo.app.feature.climbing.upload.FinalAnalysisBodyLoadDistribution
import com.ddgo.app.feature.climbing.upload.FinalAnalysisJointLoadSummary

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
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "신체 부위별 부하",
                color = AnalysisText,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                )
            )

            BodyLoadBackFigureSection(
                distribution = distribution,
                topJointLoads = displayJointLoads,
                modifier = Modifier.padding(bottom = 2.dp)
            )

            BodyLoadFocusInset(
                value = loadFocusValue,
                caption = loadFocusCaption,
                accentColor = loadFocusAccentColor
            )

            if (displayJointLoads.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "상위 관절 부하",
                        color = AnalysisText,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    displayJointLoads.forEachIndexed { index, joint ->
                        JointLoadRow(
                            joint = joint,
                            rank = index + 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BodyLoadBackFigureSection(
    distribution: FinalAnalysisBodyLoadDistribution?,
    topJointLoads: List<DisplayJointLoad>,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(368.dp)
    ) {
        val markers = remember(topJointLoads) {
            topJointLoads.map { joint ->
                jointMarkerFor(
                    descriptor = joint.descriptor,
                    intensityPercent = joint.intensityPercent
                )
            }
        }

        val sideLabelWidth = 86.dp
        val topY = maxHeight * 0.25f
        val torsoY = maxHeight * 0.03f
        val legY = maxHeight * 0.63f

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 78.dp)
                .height(240.dp)
                .aspectRatio(315f / 734f)
        ) {
            BodyLoadSvgLayer(
                assetPath = BACK_BASE_ASSET_PATH,
                modifier = Modifier.matchParentSize()
            )

            BodyLoadRegion.entries.forEach { region ->
                val value = distribution.valueFor(region)
                val tint = bodyLoadTint(value)
                if (tint.alpha > 0f) {
                    BodyLoadSvgLayer(
                        assetPath = region.backAssetPath,
                        tint = tint,
                        modifier = Modifier.matchParentSize()
                    )
                }
            }

            BodyLoadSvgLayer(
                assetPath = BACK_GUIDES_ASSET_PATH,
                modifier = Modifier.matchParentSize()
            )

            markers.forEach { marker ->
                JointMarkerDot(
                    marker = marker,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        BodyLoadSideLabel(
            title = "왼팔",
            value = distribution?.leftArm,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 8.dp, y = topY)
                .width(sideLabelWidth)
        )

        BodyLoadSideLabel(
            title = "오른팔",
            value = distribution?.rightArm,
            alignment = Alignment.End,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-8).dp, y = topY)
                .width(sideLabelWidth)
        )

        BodyLoadCenterLabel(
            title = "몸통",
            value = distribution?.torso,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = torsoY)
        )

        BodyLoadSideLabel(
            title = "왼다리",
            value = distribution?.leftLeg,
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = 8.dp, y = legY)
                .width(sideLabelWidth)
        )

        BodyLoadSideLabel(
            title = "오른다리",
            value = distribution?.rightLeg,
            alignment = Alignment.End,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-8).dp, y = legY)
                .width(sideLabelWidth)
        )
    }
}

@Composable
private fun BodyLoadSideLabel(
    title: String,
    value: Int?,
    modifier: Modifier = Modifier,
    alignment: Alignment.Horizontal = Alignment.Start
) {
    Column(
        modifier = modifier,
        horizontalAlignment = alignment,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            color = AnalysisText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value?.let { "$it%" } ?: "--",
            color = value?.let(::bodyLoadTint) ?: AnalysisMuted,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun BodyLoadCenterLabel(
    title: String,
    value: Int?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = title,
            color = AnalysisText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value?.let { "$it%" } ?: "--",
            color = value?.let(::bodyLoadTint) ?: AnalysisMuted,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}

@Composable
private fun BodyLoadFocusInset(
    value: String,
    caption: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AnalysisCardColor)
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
                color = accentColor,
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
                color = AnalysisMuted,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
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
private fun JointLoadRow(
    joint: DisplayJointLoad,
    rank: Int,
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
        Text(
            text = "$rank.",
            color = AnalysisFailure,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold
        )

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

private const val BACK_BASE_ASSET_PATH = "body_load/back_base.svg"
private const val BACK_GUIDES_ASSET_PATH = "body_load/back_guides.svg"

private enum class BodyLoadRegion(
    val backAssetPath: String
) {
    LEFT_ARM("body_load/back_left_arm.svg"),
    RIGHT_ARM("body_load/back_right_arm.svg"),
    TORSO("body_load/back_torso.svg"),
    LEFT_LEG("body_load/back_left_leg.svg"),
    RIGHT_LEG("body_load/back_right_leg.svg")
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
)

private data class JointAnchor(
    val xFraction: Float,
    val yFraction: Float
)

private data class JointMarker(
    val xFraction: Float,
    val yFraction: Float,
    val intensityPercent: Int
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

    return JointDescriptor(side = side, kind = kind, displayNumber = displayNumber)
}

private fun steppedLoadColor(percent: Int): Color {
    val step = percent.coerceIn(0, 100) / 10
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

private fun jointMarkerFor(
    descriptor: JointDescriptor,
    intensityPercent: Int
): JointMarker {
    val anchor = when (descriptor.kind) {
        JointKind.SHOULDER ->
            if (descriptor.side == JointSide.LEFT) JointAnchor(0.29f, 0.19f) else JointAnchor(0.71f, 0.19f)

        JointKind.ELBOW ->
            if (descriptor.side == JointSide.LEFT) JointAnchor(0.22f, 0.33f) else JointAnchor(0.78f, 0.33f)

        JointKind.HIP ->
            if (descriptor.side == JointSide.LEFT) JointAnchor(0.35f, 0.49f) else JointAnchor(0.65f, 0.49f)

        JointKind.KNEE ->
            if (descriptor.side == JointSide.LEFT) JointAnchor(0.38f, 0.73f) else JointAnchor(0.62f, 0.73f)

        JointKind.ANKLE ->
            if (descriptor.side == JointSide.LEFT) JointAnchor(0.45f, 0.92f) else JointAnchor(0.55f, 0.92f)
    }

    return JointMarker(
        xFraction = anchor.xFraction,
        yFraction = anchor.yFraction,
        intensityPercent = intensityPercent
    )
}

@Composable
private fun JointMarkerDot(
    marker: JointMarker,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier) {
        val markerSize = when {
            marker.intensityPercent >= 80 -> 20.dp
            marker.intensityPercent >= 60 -> 18.dp
            else -> 16.dp
        }
        val xOffset = maxWidth * marker.xFraction
        val yOffset = maxHeight * marker.yFraction

        Image(
            painter = painterResource(id = R.drawable.ic_joint_warning_marker),
            contentDescription = null,
            modifier = Modifier
                .offset(x = xOffset - markerSize / 2, y = yOffset - markerSize / 2)
                .size(markerSize),
            contentScale = ContentScale.Fit
        )
    }
}
