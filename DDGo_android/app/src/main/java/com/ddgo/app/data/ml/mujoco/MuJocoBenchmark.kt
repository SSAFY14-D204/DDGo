package com.ddgo.app.data.ml.mujoco

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * MuJoCo 온디바이스 성능 벤치마크 러너
 *
 * 각 모델을 여러 워크로드로 실행해 steps/s 와 실시간 배율(RT factor)을 측정합니다.
 */
object MuJocoBenchmark {

    /** 기본 워크로드: 워밍업 + 본 측정 */
    private val WORKLOADS = listOf(
        WorkLoad("warm-up",  steps =  500,  include = false),
        WorkLoad("1k  steps", steps = 1_000),
        WorkLoad("10k steps", steps = 10_000),
        WorkLoad("50k steps", steps = 50_000),
    )

    // ─────────────────────────────────────────────────────────────────────
    // 단일 모델 벤치마크
    // ─────────────────────────────────────────────────────────────────────

    /**
     * @param modelName 표시용 모델 이름
     * @param xml       MJCF XML 문자열
     * @param onProgress 진행 상황 콜백 (UI 업데이트용)
     * @return [ModelBenchmarkReport] 또는 null (초기화 실패 시)
     */
    suspend fun runModel(
        modelName: String,
        xml: String,
        onProgress: (String) -> Unit = {}
    ): ModelBenchmarkReport? = withContext(Dispatchers.Default) {

        onProgress("[$modelName] Initializing...")
        if (!MuJoCoEngine.init(xml)) return@withContext null

        val modelInfo = MuJoCoEngine.getModelInfo()
        val version   = MuJoCoEngine.version()
        val results   = mutableListOf<WorkLoadResult>()

        for (wl in WORKLOADS) {
            onProgress("[$modelName] Running ${wl.label}...")
            val br = MuJoCoEngine.benchmark(wl.steps) ?: break

            if (wl.include) {
                results += WorkLoadResult(label = wl.label, result = br)
            }
        }

        ModelBenchmarkReport(
            modelName = modelName,
            mujocoVersion = version,
            modelInfo = modelInfo,
            workloads = results
        )
    }

    // ─────────────────────────────────────────────────────────────────────
    // 전체 스위트 실행
    // ─────────────────────────────────────────────────────────────────────

    /**
     * [MuJocoModels.ALL] 에 정의된 모든 모델을 순서대로 벤치마크합니다.
     */
    suspend fun runAll(
        onProgress: (String) -> Unit = {},
        onModelDone: (ModelBenchmarkReport) -> Unit = {}
    ): List<ModelBenchmarkReport> = withContext(Dispatchers.Default) {

        val reports = mutableListOf<ModelBenchmarkReport>()

        for ((name, xml) in MuJocoModels.ALL) {
            val report = runModel(name, xml, onProgress)
            if (report != null) {
                reports += report
                onModelDone(report)
            }
        }

        MuJoCoEngine.close()
        reports
    }

    // ─────────────────────────────────────────────────────────────────────
    // 내부 타입
    // ─────────────────────────────────────────────────────────────────────

    private data class WorkLoad(
        val label   : String,
        val steps   : Int,
        val include : Boolean = true
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 결과 데이터 클래스
// ─────────────────────────────────────────────────────────────────────────────

data class WorkLoadResult(
    val label  : String,
    val result : BenchmarkResult
)

data class ModelBenchmarkReport(
    val modelName     : String,
    val mujocoVersion : String,
    val modelInfo     : ModelInfo?,
    val workloads     : List<WorkLoadResult>
) {
    /** 가장 긴 워크로드의 steps/s (피크 성능 기준) */
    val peakStepsPerSec: Double
        get() = workloads.maxOfOrNull { it.result.stepsPerSec } ?: 0.0

    /** 가장 긴 워크로드의 실시간 배율 */
    val peakRtFactor: Double
        get() = workloads.maxOfOrNull { it.result.realTimeFactor } ?: 0.0

    fun summary(): String = buildString {
        appendLine("══ $modelName ══════════════════════════")
        appendLine("  MuJoCo v$mujocoVersion")
        modelInfo?.let { appendLine("  Model   : $it") }
        appendLine()
        for (wl in workloads) {
            val r = wl.result
            appendLine("  [${wl.label}]")
            appendLine("    ${"%,.0f".format(r.stepsPerSec)} steps/s  |  ${"%,.2f".format(r.elapsedMs)} ms  |  RT×${"%.1f".format(r.realTimeFactor)}")
        }
        append("  → Peak: ${"%.0f".format(peakStepsPerSec)} steps/s (RT×${"%.1f".format(peakRtFactor)})")
    }
}
