package com.ddgo.app.domain.model

/**
 * 현재 시뮬레이션 상태 스냅샷
 */
data class SimState(
    val time  : Double,
    val qpos0 : Double,
    val qvel0 : Double
)
