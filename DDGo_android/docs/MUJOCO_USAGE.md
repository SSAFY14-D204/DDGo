# DDGo Android - MuJoCo 커스텀 모델 가이드

> **대상 독자**: MuJoCo 물리 모델을 새로 만들거나 기존 모델을 수정하려는 개발자

## 목차
1. [아키텍처 개요](#아키텍처-개요)
2. [파일 구조](#파일-구조)
3. [모델 추가 방법 (빠른 시작)](#모델-추가-방법-빠른-시작)
4. [MJCF XML 문법 가이드](#mjcf-xml-문법-가이드)
5. [내장 모델 레퍼런스](#내장-모델-레퍼런스)
6. [Kotlin API 사용법](#kotlin-api-사용법)
7. [JNI 브릿지 확장](#jni-브릿지-확장)
8. [주의사항 및 제약](#주의사항-및-제약)

---

## 아키텍처 개요

DDGo 앱의 Clean Architecture 레이어에 맞게 구성되어 있습니다.

```
[feature 계층]
MyViewModel
    │  @Inject PhysicsEngine (인터페이스)
    ▼
[domain 계층]
PhysicsEngine.kt (interface)        ← 계약서만 존재, Android 의존성 없음
domain/model/
    ├── SimState.kt
    ├── ModelInfo.kt
    └── BenchmarkResult.kt
    ▼
[data 계층]
MuJocoModels.kt          ← 여기에 MJCF XML 문자열 추가
      │
      ▼
MuJoCoEngine.kt          ← PhysicsEngine 구현체, init(xml) 으로 모델 로드
      │  JNI
      ▼
mujoco_jni.cpp           ← C++ 물리 연산 실행
      │
      ▼
libmujoco.so             ← MuJoCo 3.2.5 네이티브 라이브러리 (NDK 빌드)
```

**핵심 원칙**
- 모델은 **MJCF XML 문자열** 형태로 관리. 별도 파일(.xml) 없이 Kotlin 코드 안에 정의.
- 렌더링 없음 — 순수 물리 연산(CPU)만 수행.
- 모델은 한 번에 1개만 로드 가능 (단일 인스턴스).
- ViewModel은 `MuJoCoEngine`을 직접 참조하지 않고 `PhysicsEngine` 인터페이스를 주입받아 사용.

---

## 파일 구조

```
app/src/main/
├── cpp/
│   └── mujoco_jni.cpp                              # C++ JNI 브릿지 (⚠️ 패키지경로 고정)
└── java/com/ddgo/app/
    ├── domain/
    │   ├── model/
    │   │   ├── SimState.kt                         # 시뮬레이션 상태 (time, qpos0, qvel0)
    │   │   ├── ModelInfo.kt                        # 모델 구조 (nq, nv, nbody, ngeom)
    │   │   └── BenchmarkResult.kt                  # 벤치마크 결과 (stepsPerSec 등)
    │   └── repository/
    │       └── PhysicsEngine.kt                    # 인터페이스 (계약서)
    ├── data/ml/mujoco/
    │   ├── MuJocoModels.kt    ← ✏️ 모델 추가는 여기
    │   ├── MuJoCoEngine.kt                         # PhysicsEngine 구현체 (JNI object 싱글톤)
    │   └── MuJocoBenchmark.kt                      # 성능 벤치마크 유틸
    └── di/
        └── MlModule.kt                             # PhysicsEngine → MuJoCoEngine 바인딩
```

---

## 모델 추가 방법 (빠른 시작)

### Step 1 — `MuJocoModels.kt`에 XML 추가

```kotlin
object MuJocoModels {

    // 기존 모델들 (PENDULUM, CARTPOLE, HUMANOID) ...

    // ✏️ 새 모델 추가
    val MY_MODEL = """
        <mujoco model="my_model">
          <option timestep="0.002" integrator="RK4" gravity="0 0 -9.81"/>
          <worldbody>
            <!-- 바디 정의 -->
          </worldbody>
        </mujoco>
    """.trimIndent()

    // ALL 목록에도 추가해야 MuJocoBenchmark.runAll()에 포함됨
    val ALL: List<Pair<String, String>> = listOf(
        "Pendulum (simple)"   to PENDULUM,
        "CartPole (RL env)"   to CARTPOLE,
        "Humanoid (climbing)" to HUMANOID,
        "My Model"            to MY_MODEL,   // ← 추가
    )
}
```

### Step 2 — ViewModel에서 주입받아 사용

`MuJoCoEngine`을 직접 참조하지 않고 **`PhysicsEngine` 인터페이스를 Hilt로 주입**받습니다.

```kotlin
@HiltViewModel
class MyViewModel @Inject constructor(
    private val physicsEngine: PhysicsEngine   // MuJoCoEngine이 주입됨
) : ViewModel() {

    fun runSimulation() {
        viewModelScope.launch(Dispatchers.Default) {
            physicsEngine.load()                        // 라이브러리 로드 (최초 1회)
            physicsEngine.init(MuJocoModels.MY_MODEL)  // 모델 초기화

            repeat(500) {
                physicsEngine.step()                    // 물리 1스텝 (0.002s)
            }
            // 시뮬레이션 1.0초 경과

            val state: SimState? = physicsEngine.getState()      // 상태 읽기
            physicsEngine.close()                                 // 리소스 해제
        }
    }
}
```

---

## MJCF XML 문법 가이드

### 최상위 구조

```xml
<mujoco model="모델이름">
  <option .../>      <!-- 시뮬레이션 옵션 -->
  <default>...</default>  <!-- 공통 기본값 -->
  <worldbody>...</worldbody>  <!-- 물리 바디 트리 (필수) -->
  <actuator>...</actuator>    <!-- 액츄에이터 (선택) -->
</mujoco>
```

---

### `<option>` — 시뮬레이션 설정

```xml
<option
  timestep="0.002"       <!-- 스텝 크기(초). 작을수록 정확, 느림. 기본 0.002 -->
  integrator="RK4"       <!-- 적분기: Euler(빠름) / RK4(정확) / implicit -->
  gravity="0 0 -9.81"    <!-- 중력 벡터 (x y z). 기본 -9.81 -->
/>
```

| integrator | 특징 | 권장 상황 |
|---|---|---|
| `Euler` | 빠름, 덜 안정적 | 단순 모델, 빠른 프로토타이핑 |
| `RK4` | 4배 느림, 안정적 | 복잡한 관절, 클라이밍 분석 |
| `implicit` | 강체 시스템에 안정적 | 강성(stiff) 접촉 처리 |

---

### `<worldbody>` — 바디 트리

모든 물리 오브젝트는 `<worldbody>` 안에 **트리 구조**로 정의합니다.

```xml
<worldbody>
  <!-- 고정 바닥 -->
  <geom name="floor" type="plane" size="5 5 0.1" rgba=".8 .8 .8 1"/>

  <!-- 이동 가능한 바디 -->
  <body name="upper_arm" pos="0 0 1.0">
    <joint name="shoulder" type="hinge" axis="0 1 0" range="-90 90" damping="0.5"/>
    <geom type="capsule" fromto="0 0 0  0 0 -0.3" size="0.04"/>

    <!-- 자식 바디 (부모 좌표계 기준) -->
    <body name="lower_arm" pos="0 0 -0.3">
      <joint name="elbow" type="hinge" axis="0 1 0" range="-150 0" damping="0.3"/>
      <geom type="capsule" fromto="0 0 0  0 0 -0.25" size="0.031"/>
    </body>
  </body>
</worldbody>
```

---

### `<joint>` — 관절

```xml
<joint
  name="joint_name"
  type="hinge"          <!-- 관절 종류 (아래 표 참고) -->
  axis="0 1 0"          <!-- 회전/이동 축 벡터 -->
  range="-90 90"        <!-- 각도 범위(도) 또는 거리 범위(m). limited=true 자동 적용 -->
  damping="0.5"         <!-- 감쇠 계수. 클수록 관절이 빨리 멈춤 -->
  stiffness="0.0"       <!-- 스프링 강성. 0이면 스프링 없음 -->
/>
```

| type | 설명 | axis 의미 |
|---|---|---|
| `hinge` | 1축 회전 (경첩) | 회전축 방향 |
| `slide` | 1축 직선 이동 | 이동 방향 |
| `ball` | 3축 자유 회전 (볼조인트) | 미사용 |
| `free` | 6자유도 (위치+회전) | 미사용 |

> `freejoint` 태그는 `<joint type="free"/>` 와 동일하며 루트 바디에 사용합니다.

---

### `<geom>` — 지오메트리 (충돌·시각화)

```xml
<!-- 구체 -->
<geom type="sphere" size="0.05" rgba="1 0 0 1"/>

<!-- 캡슐 (시작점 → 끝점) -->
<geom type="capsule" fromto="0 0 0  0 0 -0.3" size="0.04" rgba=".7 .5 .3 1"/>

<!-- 박스 (반크기: x y z) -->
<geom type="box" size=".1 .1 .05" rgba=".5 .5 .9 1"/>

<!-- 평면 (바닥) -->
<geom type="plane" size="5 5 0.1" rgba=".8 .8 .8 1"/>
```

공통 속성:
- `rgba`: 색상 (r g b alpha, 각 0~1)
- `contype` / `conaffinity`: 충돌 그룹 (기본값 1)
- `friction`: 마찰 계수 `"sliding torsional rolling"` (기본 `"1 0.005 0.0001"`)

---

### `<default>` — 공통 기본값

반복되는 속성을 한 곳에서 설정합니다.

```xml
<default>
  <joint damping="0.5" limited="true"/>
  <geom rgba=".7 .5 .3 1" friction="1 0.5 0.5"/>
</default>
```

---

### `<actuator>` — 액츄에이터 (힘 입력)

```xml
<actuator>
  <!-- 모터: 관절에 직접 토크/힘 인가 -->
  <motor name="elbow_motor" joint="elbow" gear="100"
         ctrllimited="true" ctrlrange="-1 1"/>
</actuator>
```

> 현재 JNI API에는 액츄에이터 제어(`ctrl` 배열 설정) 함수가 없습니다.
> 액츄에이터가 필요하면 [JNI 브릿지 확장](#jni-브릿지-확장) 섹션을 참고하세요.

---

## 내장 모델 레퍼런스

### PENDULUM — 이중 진자

```
nq=2, nv=2 | timestep=0.002 | integrator=RK4
관절: shoulder(hinge), elbow(hinge)
```

가장 단순한 모델. 새 모델 작성 전 **기준 성능 측정**에 활용합니다.

---

### CARTPOLE — 카트폴

```
nq=2, nv=2 | timestep=0.01 | integrator=RK4
관절: slider(slide), hinge(hinge)
액츄에이터: slide 모터 (gear=100)
```

직선 이동 + 회전의 조합. **RL 환경 호환성** 확인에 활용합니다.

---

### HUMANOID — 간소화 인체 ⭐ 클라이밍 분석 대상

```
nq=28, nv=27 | timestep=0.002 | integrator=RK4
루트: pelvis (freejoint → 6자유도)
상체: torso(2) → 머리, 왼팔(3+elbow), 오른팔(3+elbow)
하체: 왼다리(3+knee+ankle), 오른다리(3+knee+ankle)
```

클라이밍 동작 분석의 주 모델. 수정 시 아래 관절 목록을 참고하세요.

| 관절 이름 | 유형 | range |
|---|---|---|
| `torso_z` | hinge (z축) | -45 ~ 45° |
| `torso_y` | hinge (y축) | -60 ~ 30° |
| `left_shoulder_x/y` | hinge | -85~60° / -85~170° |
| `left_elbow` | hinge (z축) | -10 ~ 150° |
| `right_shoulder_x/y` | hinge | -85~60° / -170~85° |
| `right_elbow` | hinge (z축) | -150 ~ 10° |
| `left/right_hip_x/y/z` | hinge | 각 별도 |
| `left/right_knee` | hinge (y축) | -160~2° / -2~160° |
| `left/right_ankle_y` | hinge (y축) | -50 ~ 50° |

---

## Kotlin API 사용법

### 전체 생명주기

ViewModel에서 `PhysicsEngine` 인터페이스로 주입받아 사용합니다.
반환 타입 (`SimState`, `ModelInfo`, `BenchmarkResult`)은 모두 `domain.model` 패키지에 있습니다.

```kotlin
// 1. 라이브러리 로드 (앱 기동 시 1회)
if (!physicsEngine.load()) return  // API 28 미만 기기 차단

// 2. 모델 초기화
if (!physicsEngine.init(MuJocoModels.HUMANOID)) return  // XML 오류 시 false

// 3. 모델 구조 확인
val info: ModelInfo? = physicsEngine.getModelInfo()
// info.nq / info.nv / info.nbody / info.ngeom

// 4. 시뮬레이션 루프
repeat(N) {
    physicsEngine.step()                      // 1스텝 (timestep 만큼 진행)
    val state: SimState? = physicsEngine.getState()  // time / qpos[0] / qvel[0]
}

// 5. 성능 측정
val result: BenchmarkResult? = physicsEngine.benchmark(nSteps = 10_000)
// result.stepsPerSec / result.realTimeFactor / result.elapsedMs

// 6. 해제 (모델 교체 또는 종료 시)
physicsEngine.close()
```

### import 경로

```kotlin
import com.ddgo.app.domain.repository.PhysicsEngine   // 인터페이스
import com.ddgo.app.domain.model.SimState             // 시뮬레이션 상태
import com.ddgo.app.domain.model.ModelInfo            // 모델 구조 정보
import com.ddgo.app.domain.model.BenchmarkResult      // 벤치마크 결과
import com.ddgo.app.data.ml.mujoco.MuJocoModels       // MJCF XML 상수
```

### `realTimeFactor` 해석

| 값 | 의미 |
|---|---|
| `≥ 1.0` | 실시간 시뮬레이션 가능 |
| `< 1.0` | 연산이 너무 느려 실시간 불가 |
| `≥ 10.0` | 안정적 실시간 (여유 있음) |

---

## JNI 브릿지 확장

Kotlin API에 없는 기능이 필요하면 `mujoco_jni.cpp`와 `MuJoCoEngine.kt`를 함께 수정합니다.

### 함수명 규칙 ⚠️ 반드시 준수

```
Java_{패키지경로}_{클래스명}_{메서드명}
     ↑ 점(.) → 언더스코어(_) 로 변환

패키지: com.ddgo.app.data.ml.mujoco
→ com_ddgo_app_data_ml_mujoco

완성형: Java_com_ddgo_app_data_ml_mujoco_MuJoCoEngine_nativeXxx
```

### 예시: 액츄에이터 제어값(ctrl) 설정

**`mujoco_jni.cpp`**

```cpp
extern "C" JNIEXPORT void JNICALL
Java_com_ddgo_app_data_ml_mujoco_MuJoCoEngine_nativeSetCtrl(
        JNIEnv* env, jobject /*thiz*/, jdoubleArray ctrlArray)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_data) return;

    jsize len = env->GetArrayLength(ctrlArray);
    if (len > g_model->nu) len = g_model->nu;  // nu: 액츄에이터 수

    jdouble* buf = env->GetDoubleArrayElements(ctrlArray, nullptr);
    for (int i = 0; i < len; ++i) g_data->ctrl[i] = buf[i];
    env->ReleaseDoubleArrayElements(ctrlArray, buf, JNI_ABORT);
}
```

**`MuJoCoEngine.kt`**

```kotlin
fun setCtrl(ctrl: DoubleArray) = nativeSetCtrl(ctrl)

@JvmStatic private external fun nativeSetCtrl(ctrl: DoubleArray)
```

**사용**

```kotlin
MuJoCoEngine.setCtrl(doubleArrayOf(0.5, -0.3))  // 액츄에이터 0, 1에 힘 인가
MuJoCoEngine.step()
```

---

### 예시: 특정 바디의 월드 좌표 읽기

**`mujoco_jni.cpp`**

```cpp
extern "C" JNIEXPORT jdoubleArray JNICALL
Java_com_ddgo_app_data_ml_mujoco_MuJoCoEngine_nativeGetBodyPos(
        JNIEnv* env, jobject /*thiz*/, jint bodyId)
{
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_data || bodyId < 0 || bodyId >= g_model->nbody)
        return nullptr;

    jdoubleArray result = env->NewDoubleArray(3);
    // xpos: [nbody × 3] 배열 (world 좌표 x, y, z)
    env->SetDoubleArrayRegion(result, 0, 3, g_data->xpos + bodyId * 3);
    return result;
}
```

**`MuJoCoEngine.kt`**

```kotlin
fun getBodyPos(bodyId: Int): DoubleArray? = nativeGetBodyPos(bodyId)

@JvmStatic private external fun nativeGetBodyPos(bodyId: Int): DoubleArray?
```

---

## 주의사항 및 제약

| 항목 | 내용 |
|---|---|
| **최소 OS** | Android 9.0 (API 28) — MuJoCo 3.x의 `qsort_r` 의존 |
| **ABI** | `arm64-v8a`, `armeabi-v7a` 지원 |
| **동시 모델** | 단일 인스턴스. 모델 교체 시 반드시 `close()` → `init()` 순서 |
| **스레드** | `step()` / `benchmark()`는 `Dispatchers.Default`(백그라운드)에서 호출 |
| **렌더링** | 현재 미지원. 물리 연산 결과만 수치로 반환 |
| **JNI 패키지** | 함수명은 반드시 `data_ml_mujoco` 포함 (`data_mujoco` 아님). `MuJoCoEngine`의 패키지/클래스명 변경 금지 |
| **DI 사용** | ViewModel은 `PhysicsEngine` 인터페이스로 주입받음. `MuJoCoEngine`을 직접 참조하지 말 것 |
| **XML 오류** | `init()` 반환값이 `false`일 때 Logcat `DDGo_MuJoCo` 태그에서 상세 오류 확인 가능 |
