package com.ddgo.app.data.ml.mujoco

import android.os.Build
import android.util.Log
import com.ddgo.app.domain.model.BenchmarkResult
import com.ddgo.app.domain.model.ModelInfo
import com.ddgo.app.domain.model.SimState
import com.ddgo.app.domain.repository.PhysicsEngine

/**
 * MuJoCo 물리 시뮬레이션 엔진 싱글톤
 *
 * JNI를 통해 네이티브 MuJoCo 라이브러리를 호출합니다.
 * 렌더링 없이 순수 물리 연산만 수행 (on-device 성능 테스트용)
 *
 * ⚠️ 패키지명/클래스명 변경 금지: C++ JNI 함수명이 이 경로에 고정되어 있음
 *    (mujoco_jni.cpp: Java_com_ddgo_app_data_ml_mujoco_MuJoCoEngine_*)
 */
object MuJoCoEngine : PhysicsEngine {

    private const val TAG = "MuJoCoEngine"
    private var isLoaded = false

    /** 네이티브 라이브러리 로드 (최초 1회) */
    override fun load(): Boolean {
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

    override fun init(xml: String): Boolean {
        if (!isLoaded) {
            Log.e(TAG, "load() must be called before init()")
            return false
        }
        return nativeInit(xml).also { ok ->
            if (ok) Log.i(TAG, "Model initialized")
            else    Log.e(TAG, "Model initialization failed")
        }
    }

    override fun step() = nativeStep()

    override fun benchmark(nSteps: Int): BenchmarkResult? {
        val arr = nativeBenchmark(nSteps) ?: return null
        return BenchmarkResult(
            elapsedMs    = arr[0],
            stepsPerSec  = arr[1],
            simTimeSec   = arr[2],
            totalSteps   = arr[3].toInt()
        )
    }

    override fun getState(): SimState? {
        val arr = nativeGetState() ?: return null
        return SimState(time = arr[0], qpos0 = arr[1], qvel0 = arr[2])
    }

    override fun getModelInfo(): ModelInfo? {
        val arr = nativeGetModelInfo() ?: return null
        return ModelInfo(nq = arr[0], nv = arr[1], nbody = arr[2], ngeom = arr[3])
    }

    override fun version(): String = if (isLoaded) nativeGetVersion() else "not loaded"

    override fun close() {
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
