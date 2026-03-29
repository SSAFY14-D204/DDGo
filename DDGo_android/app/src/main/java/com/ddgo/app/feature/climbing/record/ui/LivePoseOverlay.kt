package com.ddgo.app.feature.climbing.record.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.ddgo.app.domain.model.AiLandmark3D
import com.ddgo.app.domain.model.AiPoseFrame

@Composable
fun LivePoseOverlay(
    modifier: Modifier = Modifier,
    poseFrame: AiPoseFrame?
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (poseFrame?.poseDetected != true) {
            drawRect(color = Color.Black.copy(alpha = 0.18f))
            return@Canvas
        }

        val landmarks = poseFrame.poseLandmarks
        if (landmarks.isEmpty()) {
            drawRect(color = Color.Black.copy(alpha = 0.18f))
            return@Canvas
        }

        drawConnections(landmarks)
        landmarks.forEach { landmark ->
            if (landmark.x !in 0f..1f || landmark.y !in 0f..1f) return@forEach
            drawCircle(
                color = Color(0xFF7EE2B8),
                radius = 5.dp.toPx(),
                center = Offset(
                    x = landmark.x * size.width,
                    y = landmark.y * size.height
                )
            )
        }
    }
}

private fun DrawScope.drawConnections(landmarks: List<AiLandmark3D>) {
    poseConnections.forEach { (startIndex, endIndex) ->
        val start = landmarks.getOrNull(startIndex) ?: return@forEach
        val end = landmarks.getOrNull(endIndex) ?: return@forEach
        drawLine(
            color = Color(0xFF4CC9F0).copy(alpha = 0.9f),
            start = Offset(start.x * size.width, start.y * size.height),
            end = Offset(end.x * size.width, end.y * size.height),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private val poseConnections = listOf(
    0 to 1,
    0 to 2,
    1 to 3,
    2 to 4,
    0 to 5,
    0 to 6,
    5 to 7,
    7 to 9,
    6 to 8,
    8 to 10,
    5 to 11,
    6 to 12,
    11 to 12,
    11 to 13,
    13 to 15,
    12 to 14,
    14 to 16,
    11 to 23,
    12 to 24,
    23 to 24,
    23 to 25,
    24 to 26,
    25 to 27,
    26 to 28,
    27 to 29,
    28 to 30,
    29 to 31,
    30 to 32
)
