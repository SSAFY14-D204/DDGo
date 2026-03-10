package com.ddgo.app.data.ml.mujoco

import android.os.Build
import android.util.Log

/**
 * MuJoCo 물리 시뮬레이션 엔진 싱글톤
 *
 * JNI를 통해 네이티브 MuJoCo 라이브러리를 호출합니다.
 * 렌더링 없이 순수 물리 연산만 수행 (on-device 성능 테스트용)
 */
object MuJoCoEngine {

    private const val TAG = "MuJoCoEngine"
    private var isLoaded = false

    /** 네이티브 라이브러리 로드 (최초 1회) */
    fun load(): Boolean {
        if (isLoaded) return true
        // MuJoCo 3.x 는 qsort_r (API 28+) 을 사용 → API 28 미만 기기에서 크래시 방지
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.e(TAG, "MuJoCo requires Android 9.0 (API 28) or higher. Current: API ${Build.VERSION.SDK_INT}")
            return false
        }
        return try {
            System.loadLibrary("ddgo_mujoco")
            isLoaded = true
            Log.i(TAG, "libddgo_mujoco.so loaded | MuJoCo v${nativeGetVersion()}")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load native library: ${e.message}")
            false
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // Public API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * XML 문자열로 MuJoCo 모델을 초기화합니다.
     * @param xml MuJoCo MJCF XML 문자열
     * @return 초기화 성공 여부
     */
    fun init(xml: String): Boolean {
        if (!isLoaded) {
            Log.e(TAG, "load() must be called before init()")
            return false
        }
        return nativeInit(xml).also { ok ->
            if (ok) Log.i(TAG, "Model initialized")
            else    Log.e(TAG, "Model initialization failed")
        }
    }

    /**
     * 시뮬레이션 1스텝 진행 (dt = model.opt.timestep, 기본 0.002s)
     */
    fun step() = nativeStep()

    /**
     * N스텝 실행 후 성능 측정
     * @param nSteps 실행할 시뮬레이션 스텝 수
     * @return [BenchmarkResult] 또는 null (초기화 안 된 경우)
     */
    fun benchmark(nSteps: Int): BenchmarkResult? {
        val arr = nativeBenchmark(nSteps) ?: return null
        return BenchmarkResult(
            elapsedMs    = arr[0],
            stepsPerSec  = arr[1],
            simTimeSec   = arr[2],
            totalSteps   = arr[3].toInt()
        )
    }

    /**
     * 현재 시뮬레이션 상태 반환 (시간, 첫 번째 관절 위치/속도)
     */
    fun getState(): SimState? {
        val arr = nativeGetState() ?: return null
        return SimState(time = arr[0], qpos0 = arr[1], qvel0 = arr[2])
    }

    /**
     * 로드된 모델 정보 반환
     */
    fun getModelInfo(): ModelInfo? {
        val arr = nativeGetModelInfo() ?: return null
        return ModelInfo(nq = arr[0], nv = arr[1], nbody = arr[2], ngeom = arr[3])
    }

    /**
     * MuJoCo 라이브러리 버전 문자열
     */
    fun version(): String = if (isLoaded) nativeGetVersion() else "not loaded"

    /**
     * 리소스 해제 (앱 종료 시 호출 권장)
     */
    fun close() {
        if (isLoaded) {
            nativeClose()
            Log.i(TAG, "MuJoCo closed")
        }
    }

    // ─────────────────────────────────────────────────────────────────────
    // JNI 네이티브 함수 선언
    // ─────────────────────────────────────────────────────────────────────

    @JvmStatic private external fun nativeInit(xmlStr: String): Boolean
    @JvmStatic private external fun nativeStep()
    @JvmStatic private external fun nativeBenchmark(nSteps: Int): DoubleArray?
    @JvmStatic private external fun nativeGetState(): DoubleArray?
    @JvmStatic private external fun nativeGetVersion(): String
    @JvmStatic private external fun nativeGetModelInfo(): IntArray?
    @JvmStatic private external fun nativeClose()
}

// ─────────────────────────────────────────────────────────────────────────────
// 데이터 클래스
// ─────────────────────────────────────────────────────────────────────────────

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

/**
 * 현재 시뮬레이션 상태 스냅샷
 */
data class SimState(
    val time  : Double,
    val qpos0 : Double,
    val qvel0 : Double
)

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
