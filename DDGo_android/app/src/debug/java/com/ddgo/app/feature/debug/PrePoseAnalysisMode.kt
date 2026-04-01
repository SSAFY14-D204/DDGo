package com.ddgo.app.feature.debug

enum class PrePoseAnalysisMode(
    val chipLabel: String,
    val resultLabel: String
) {
    NORMAL(
        chipLabel = "일반",
        resultLabel = "일반 (MediaCodec + Bitmap)"
    ),
    OPTIMIZED(
        chipLabel = "최적화",
        resultLabel = "최적화 (Surface + EGL)"
    ),
    OFFICIAL_SAMPLED(
        chipLabel = "공식샘플",
        resultLabel = "공식샘플 (Retriever)"
    )
}
