package com.ddgo.app.domain.model

/**
 * 로드된 모델의 구조 정보
 */
data class ModelInfo(
    val nq    : Int,   // 일반화 좌표 수 (자유도)
    val nv    : Int,   // 속도 자유도
    val nbody : Int,   // 바디 개수
    val ngeom : Int    // 지오메트리 개수
) {
    override fun toString() =
        "nq=$nq  nv=$nv  nbody=$nbody  ngeom=$ngeom"
}
