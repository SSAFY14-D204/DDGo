package com.ddgo.app.domain.model

/**
 * 벤치마크 결과
 * @param elapsedMs   실제 소요 시간 (ms)
 * @param stepsPerSec 초당 시뮬레이션 스텝 수
 * @param simTimeSec  시뮬레이션 내 경과 시간 (초)
 * @param totalSteps  실행한 총 스텝 수
 */
data class BenchmarkResult(
    val elapsedMs   : Double,
    val stepsPerSec : Double,
    val simTimeSec  : Double,
    val totalSteps  : Int
) {
    /** 실시간 배율 (simTime / realTime) */
    val realTimeFactor: Double
        get() = if (elapsedMs > 0) simTimeSec / (elapsedMs / 1000.0) else 0.0

    override fun toString(): String = buildString {
        appendLine("── MuJoCo Benchmark ──────────────────")
        appendLine("  Steps      : $totalSteps")
        appendLine("  Elapsed    : ${"%.2f".format(elapsedMs)} ms")
        appendLine("  Rate       : ${"%.0f".format(stepsPerSec)} steps/s")
        appendLine("  Sim time   : ${"%.4f".format(simTimeSec)} s")
        append(  "  RT factor  : ${"%.2f".format(realTimeFactor)}x")
    }
}
