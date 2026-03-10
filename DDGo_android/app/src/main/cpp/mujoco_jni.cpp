/**
 * DDGo MuJoCo JNI Bridge
 *
 * Kotlin ↔ MuJoCo C++ 연결 레이어
 * 렌더링 없이 순수 물리 시뮬레이션 성능을 측정합니다.
 */

#include <jni.h>
#include <android/log.h>
#include <mujoco/mujoco.h>

#include <string>
#include <chrono>
#include <memory>
#include <mutex>

#define LOG_TAG "DDGo_MuJoCo"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// ─────────────────────────────────────────────────────────────────────────────
// 전역 상태 (단일 인스턴스 / 스레드 안전)
// ─────────────────────────────────────────────────────────────────────────────
namespace {
    std::mutex  g_mutex;
    mjModel*    g_model = nullptr;
    mjData*     g_data  = nullptr;

    void cleanup_locked() {
        if (g_data)  { mj_deleteData(g_data);   g_data  = nullptr; }
        if (g_model) { mj_deleteModel(g_model); g_model = nullptr; }
    }
} // namespace

// ─────────────────────────────────────────────────────────────────────────────
// nativeInit  : XML 문자열로 모델 로드
// ─────────────────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT jboolean JNICALL
Java_com_ddgo_app_data_ml_mujoco_MuJoCoEngine_nativeInit(
        JNIEnv* env, jobject /*thiz*/, jstring xmlStr)
{
    const char* xml = env->GetStringUTFChars(xmlStr, nullptr);

    // MuJoCo 3.x API: loadXMLFromString 제거됨 → parseXMLString + compile 2단계
    char error[1000] = "";
    mjSpec* spec = mj_parseXMLString(xml, nullptr, error, sizeof(error));
    env->ReleaseStringUTFChars(xmlStr, xml);

    if (!spec) {
        LOGE("mj_parseXMLString failed: %s", error);
        return JNI_FALSE;
    }

    mjModel* newModel = mj_compile(spec, nullptr);
    mj_deleteSpec(spec);

    if (!newModel) {
        LOGE("mj_compile failed");
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(g_mutex);
    cleanup_locked();

    g_model = newModel;
    g_data  = mj_makeData(g_model);

    LOGI("Model loaded | nq=%d  nv=%d  nbody=%d  ngeom=%d",
         g_model->nq, g_model->nv, g_model->nbody, g_model->ngeom);
    return JNI_TRUE;
}

// ─────────────────────────────────────────────────────────────────────────────
// nativeStep  : 시뮬레이션 1스텝 진행
// ─────────────────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_com_ddgo_app_data_ml_mujoco_MuJoCoEngine_nativeStep(
        JNIEnv* /*env*/, jobject /*thiz*/)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (g_model && g_data) {
        mj_step(g_model, g_data);
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// nativeBenchmark : N 스텝 실행 후 성능 측정
//   반환: double[4] = { elapsedMs, stepsPerSec, simTimeSec, nSteps }
// ─────────────────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_ddgo_app_data_ml_mujoco_MuJoCoEngine_nativeBenchmark(
        JNIEnv* env, jobject /*thiz*/, jint nSteps)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_data) {
        LOGE("nativeBenchmark: model not initialized");
        return nullptr;
    }

    mj_resetData(g_model, g_data);

    const auto t_start = std::chrono::high_resolution_clock::now();
    for (int i = 0; i < nSteps; ++i) {
        mj_step(g_model, g_data);
    }
    const auto t_end = std::chrono::high_resolution_clock::now();

    const double elapsedMs = std::chrono::duration<double, std::milli>(t_end - t_start).count();
    const double stepsPerSec = (nSteps / elapsedMs) * 1000.0;
    const double simTimeSec  = g_data->time;

    LOGI("Benchmark | steps=%d  elapsed=%.2fms  rate=%.0f steps/s  simTime=%.4fs",
         nSteps, elapsedMs, stepsPerSec, simTimeSec);

    jdoubleArray result = env->NewDoubleArray(4);
    if (!result) return nullptr;
    double vals[4] = { elapsedMs, stepsPerSec, simTimeSec, (double)nSteps };
    env->SetDoubleArrayRegion(result, 0, 4, vals);
    return result;
}

// ─────────────────────────────────────────────────────────────────────────────
// nativeGetState : 현재 시뮬레이션 상태 반환
//   반환: double[3] = { simTime, qpos[0], qvel[0] }  (관절 0번 기준)
// ─────────────────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_ddgo_app_data_ml_mujoco_MuJoCoEngine_nativeGetState(
        JNIEnv* env, jobject /*thiz*/)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_data) return nullptr;

    jdoubleArray result = env->NewDoubleArray(3);
    if (!result) return nullptr;
    double vals[3] = {
        g_data->time,
        (g_model->nq > 0) ? g_data->qpos[0] : 0.0,
        (g_model->nv > 0) ? g_data->qvel[0] : 0.0
    };
    env->SetDoubleArrayRegion(result, 0, 3, vals);
    return result;
}

// ─────────────────────────────────────────────────────────────────────────────
// nativeGetVersion : MuJoCo 라이브러리 버전 문자열 반환
// ─────────────────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT jstring JNICALL
Java_com_ddgo_app_data_ml_mujoco_MuJoCoEngine_nativeGetVersion(
        JNIEnv* env, jobject /*thiz*/)
{
    const int ver = mj_version();
    char buf[32];
    snprintf(buf, sizeof(buf), "%d.%d.%d",
             ver / 100, (ver % 100) / 10, ver % 10);
    return env->NewStringUTF(buf);
}

// ─────────────────────────────────────────────────────────────────────────────
// nativeGetModelInfo : 로드된 모델 기본 정보 반환
//   반환: int[4] = { nq, nv, nbody, ngeom }
// ─────────────────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT jintArray JNICALL
Java_com_ddgo_app_data_ml_mujoco_MuJoCoEngine_nativeGetModelInfo(
        JNIEnv* env, jobject /*thiz*/)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model) return nullptr;

    jintArray result = env->NewIntArray(4);
    if (!result) return nullptr;
    int vals[4] = { g_model->nq, g_model->nv, g_model->nbody, g_model->ngeom };
    env->SetIntArrayRegion(result, 0, 4, vals);
    return result;
}

// ─────────────────────────────────────────────────────────────────────────────
// nativeClose : 모델 및 데이터 해제
// ─────────────────────────────────────────────────────────────────────────────
extern "C" JNIEXPORT void JNICALL
Java_com_ddgo_app_data_ml_mujoco_MuJoCoEngine_nativeClose(
        JNIEnv* /*env*/, jobject /*thiz*/)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    cleanup_locked();
    LOGI("MuJoCo resources released");
}
