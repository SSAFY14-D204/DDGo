package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.poseanalysis.HandPeakAnnotation
import com.ddgo.app.domain.poseanalysis.HandPeakConfig
import com.ddgo.app.domain.poseanalysis.PoseFrame
import com.ddgo.app.domain.poseanalysis.analyzeHandPeakAndEnd
import com.ddgo.app.domain.poseanalysis.extractBodyPartHeights
import javax.inject.Inject

class AnalyzeHandPeakAndEndUseCase @Inject constructor() {
    operator fun invoke(
        frames: List<PoseFrame>,
        config: HandPeakConfig = HandPeakConfig(),
        wallSegmentIdByFrameTimeMs: Map<Long, Int> = emptyMap()
    ): HandPeakAnnotation? = analyzeHandPeakAndEnd(
        points = extractBodyPartHeights(frames, config),
        config = config,
        wallSegmentIdByFrameTimeMs = wallSegmentIdByFrameTimeMs
    )
}
