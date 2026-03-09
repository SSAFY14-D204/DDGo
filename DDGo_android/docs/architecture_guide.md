# DDGo Android 아키텍처 가이드

> 이 문서는 **신입 개발자가 팀에 합류했을 때 첫날부터 코드를 작성**할 수 있도록 작성된 가이드입니다.

---

## 1. 전체 아키텍처 개요

DDGo는 **Google 권장 단방향 데이터 흐름(Clean Architecture)** 을 따릅니다.

```
┌─────────────────────────────────────────────────────────────────────┐
│   feature (UI)         domain (비즈니스)       data (데이터)         │
│                                                                     │
│  Screen ──▶ ViewModel ──▶ UseCase ──▶ Repository ──▶ RemoteApi      │
│                                          (interface)      ML Model  │
│                                             ▲              Worker   │
│                                          RepositoryImpl             │
└─────────────────────────────────────────────────────────────────────┘
           UI 레이어         도메인 레이어         데이터 레이어
```

**핵심 규칙: 의존성은 항상 안쪽(domain)을 향합니다.**
- `feature` → `domain` ✅
- [data](file:///c:/ssafy/project-second/S14P21D204/DDGo-android/app/src/main/java/com/ddgo/app/data/remote/upload/UploadDto.kt#15-21) → `domain` ✅
- `domain` → `feature` ❌ (절대 금지)
- `domain` → [data](file:///c:/ssafy/project-second/S14P21D204/DDGo-android/app/src/main/java/com/ddgo/app/data/remote/upload/UploadDto.kt#15-21) ❌ (절대 금지)

---

## 2. 폴더 구조 한눈에 보기

```
app/src/main/java/com/ddgo/app/
│
├── DDGoApplication.kt       # Hilt 시작점
├── MainActivity.kt          # 앱의 유일한 Activity
│
├── di/                      # 💉 의존성 주입 설정
│   ├── NetworkModule.kt     # Retrofit, OkHttp
│   ├── RepositoryModule.kt  # Repository 인터페이스 → 구현체 바인딩
│   ├── DataStoreModule.kt   # 로컬 저장소
│   ├── MlModule.kt          # AI 모델 바인딩
│   └── WorkModule.kt        # WorkManager
│
├── core/                    # 🛠 공통 인프라 (비즈니스 로직 없음)
│   ├── network/
│   │   └── AuthInterceptor.kt
│   ├── datastore/
│   │   └── TokenDataStore.kt
│   └── ui/
│       ├── theme/           # Color, Type, Theme
│       └── components/      # 공용 Composable 컴포넌트
│
├── data/                    # 🗄 데이터 수집/가공
│   ├── remote/              # 서버 API
│   │   ├── common/ApiResponse.kt
│   │   ├── auth/            # AuthApi.kt, AuthDto.kt
│   │   ├── upload/          # UploadApi.kt, UploadDto.kt
│   │   └── report/          # ReportApi.kt, ReportDto.kt
│   ├── ml/                  # 온디바이스 AI
│   │   ├── mediapipe/PoseEstimatorImpl.kt
│   │   └── yolo/HoldDetectorImpl.kt
│   ├── mapper/              # DTO/AI결과 → Domain 변환
│   │   ├── AuthMapper.kt
│   │   ├── UploadMapper.kt
│   │   └── VisionMapper.kt
│   ├── work/                # 백그라운드 Worker
│   │   └── VideoAnalyzeWorker.kt
│   └── repository/          # Repository 구현체
│       ├── AuthRepositoryImpl.kt
│       └── UploadRepositoryImpl.kt
│
├── domain/                  # 🧠 순수 비즈니스 로직 (Android 의존성 없음)
│   ├── model/               # 앱 내부 표준 데이터 모델
│   │   ├── User.kt
│   │   ├── Hold.kt
│   │   ├── Pose.kt
│   │   └── AttemptReport.kt
│   ├── repository/          # Repository & AI 인터페이스 (계약서)
│   │   ├── AuthRepository.kt
│   │   ├── UploadRepository.kt
│   │   ├── PoseEstimator.kt
│   │   └── HoldDetector.kt
│   └── usecase/             # 복잡한 비즈니스 로직
│       └── ExtractFailClipUseCase.kt
│
├── feature/                 # 📱 화면 (UI만 담당)
│   ├── auth/                # AuthScreen.kt, AuthViewModel.kt
│   ├── upload/              # UploadScreen.kt, UploadViewModel.kt
│   └── report/              # ReportScreen.kt, ReportViewModel.kt
│
└── navigation/              # 🧭 화면 이동
    ├── NavGraph.kt
    └── ScreenRoutes.kt
```

---

## 3. 기능 추가 시 체크리스트

### ✅ Case 1: 새 API 화면 추가 (예: "내 프로필" 화면)

**총 작업 파일: 8개**

```
① data/remote/profile/ProfileApi.kt         # API 엔드포인트
② data/remote/profile/ProfileDto.kt         # 서버 응답 형태
③ data/mapper/ProfileMapper.kt              # DTO → Domain 변환
④ domain/model/Profile.kt                   # 앱 내부 데이터 모델
⑤ domain/repository/ProfileRepository.kt   # 계약서 (인터페이스)
⑥ data/repository/ProfileRepositoryImpl.kt # 실제 구현체
⑦ feature/profile/ProfileViewModel.kt      # 상태 관리
⑧ feature/profile/ProfileScreen.kt         # UI
```

**+ 연결 작업 (기존 파일 수정):**

```kotlin
// di/RepositoryModule.kt 에 추가
@Binds @Singleton
abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

// di/NetworkModule.kt 에 추가
@Provides @Singleton
fun provideProfileApi(retrofit: Retrofit): ProfileApi = retrofit.create(ProfileApi::class.java)

// navigation/ScreenRoutes.kt 에 추가
object Profile : ScreenRoutes("profile")

// navigation/NavGraph.kt 에 추가
composable(route = ScreenRoutes.Profile.route) { ProfileScreen() }
```

---

### ✅ Case 2: 새 AI 모델 추가 (예: "동작 분류 AI")

**총 작업 파일: 4개**

```
① domain/model/Motion.kt                           # AI 출력 도메인 모델
② domain/repository/MotionClassifier.kt           # AI 인터페이스 (계약서)
③ data/ml/motionclassifier/MotionClassifierImpl.kt # TFLite 구현체
④ data/mapper/VisionMapper.kt                      # toMotion() 함수 추가
```

**+ 연결 작업 (기존 파일 수정):**

```kotlin
// di/MlModule.kt 에 추가
@Binds @Singleton
abstract fun bindMotionClassifier(impl: MotionClassifierImpl): MotionClassifier

// data/work/VideoAnalyzeWorker.kt 에 추가
// MotionClassifier를 AssistedInject로 주입 후 doWork()에서 호출
```

---

### ✅ Case 3: 새 백그라운드 작업 추가

`data/work/` 에 새 Worker 파일을 만들고 `@HiltWorker`를 붙입니다.

```kotlin
// data/work/ThumbnailWorker.kt
@HiltWorker
class ThumbnailWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    // 필요한 의존성 주입
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result { ... }
}
```

---

## 4. 계층별 코딩 규칙

### 🗄 data 계층

| 파일 | 규칙 |
|:-----|:-----|
| `*Api.kt` | Retrofit interface만. 로직 없음 |
| `*Dto.kt` | `@Serializable` 필수. `@SerialName`으로 서버 필드명 매핑 |
| `*Impl.kt` | Repository/AI 구현체. try-catch로 `Result<T>` 반환 |
| `*Mapper.kt` | `object`로 선언. 순수 변환 함수만. 사이드이펙트 없음 |
| `*Worker.kt` | `@HiltWorker` + `@AssistedInject`. doWork()에서 `Result.success/failure/retry` 반환 |

```kotlin
// ✅ 올바른 Mapper 작성법
object AuthMapper {
    fun LoginResponseDto.toUser(): User = User(
        id = userId, email = email  // 필드 이름만 매핑
    )
}

// ❌ 잘못된 예: Mapper에서 API 호출
object BadMapper {
    fun toUser(dto: LoginResponseDto): User {
        api.log() // 절대 금지!
        return User(...)
    }
}
```

---

### 🧠 domain 계층

> **황금 규칙: `import android.*` 가 있으면 잘못된 것입니다.**

| 파일 | 규칙 |
|:-----|:-----|
| `domain/model/*.kt` | 순수 `data class`. Android 의존성 없음 |
| `domain/repository/*.kt` | `interface`만. 구현 없음 |
| `domain/usecase/*.kt` | `@Inject constructor()`. 단 하나의 `operator fun invoke()` |

```kotlin
// ✅ UseCase 표준 패턴
class MyUseCase @Inject constructor(
    private val repository: MyRepository  // 인터페이스 주입
) {
    operator fun invoke(param: String): Result<MyModel> {
        // 비즈니스 로직만
    }
}
```

---

### 📱 feature 계층

| 파일 | 규칙 |
|:-----|:-----|
| `*ViewModel.kt` | `@HiltViewModel`. StateFlow로 상태 노출. suspend 함수는 viewModelScope에서 |
| `*Screen.kt` | `@Composable`. 상태는 viewModel에서만 받음. 직접 API 호출 금지 |

```kotlin
// ✅ ViewModel 상태 관리 표준 패턴
@HiltViewModel
class MyViewModel @Inject constructor(
    private val useCase: MyUseCase
) : ViewModel() {

    // 외부: 읽기 전용 StateFlow
    val uiState: StateFlow<MyUiState> = _uiState.asStateFlow()
    // 내부: 변경 가능한 StateFlow
    private val _uiState = MutableStateFlow<MyUiState>(MyUiState.Loading)

    fun doSomething() {
        viewModelScope.launch {
            _uiState.value = MyUiState.Loading
            useCase(param)
                .onSuccess { _uiState.value = MyUiState.Success(it) }
                .onFailure { _uiState.value = MyUiState.Error(it.message) }
        }
    }
}
```

---

## 5. Compose UI 핵심 패턴

### 상태 수집

```kotlin
@Composable
fun MyScreen(viewModel: MyViewModel = hiltViewModel()) {
    // StateFlow → Compose State 변환
    val uiState by viewModel.uiState.collectAsState()

    when (uiState) {
        is MyUiState.Loading -> DdgoFullScreenLoading()
        is MyUiState.Success -> SuccessContent((uiState as MyUiState.Success).data)
        is MyUiState.Error -> ErrorContent((uiState as MyUiState.Error).message)
    }
}
```

### 로컬 상태 (TextField 등)

```kotlin
// remember: 리컴포지션(재렌더링) 시에도 값 유지
var email by remember { mutableStateOf("") }

OutlinedTextField(
    value = email,
    onValueChange = { email = it }  // 직접 변경
)
```

### 사이드 이펙트 (화면 이동 등)

```kotlin
// LaunchedEffect: 특정 State가 변경될 때 한 번만 실행
LaunchedEffect(uiState) {
    if (uiState is UiState.Success) {
        navController.navigate(ScreenRoutes.Home.route)
    }
}
```

---

## 6. 새 화면 추가 템플릿

> **복사 붙여넣기 후 `Feature` 부분을 원하는 이름으로 바꾸세요.**

### ViewModel 템플릿

```kotlin
@HiltViewModel
class FeatureViewModel @Inject constructor(
    private val featureUseCase: FeatureUseCase  // 필요한 UseCase 주입
) : ViewModel() {

    private val _uiState = MutableStateFlow<FeatureUiState>(FeatureUiState.Loading)
    val uiState: StateFlow<FeatureUiState> = _uiState.asStateFlow()

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = FeatureUiState.Loading
            featureUseCase()
                .onSuccess { _uiState.value = FeatureUiState.Success(it) }
                .onFailure { _uiState.value = FeatureUiState.Error(it.message ?: "오류") }
        }
    }
}

sealed class FeatureUiState {
    object Loading : FeatureUiState()
    data class Success(val data: MyModel) : FeatureUiState()
    data class Error(val message: String) : FeatureUiState()
}
```

### Screen 템플릿

```kotlin
@Composable
fun FeatureScreen(
    onNavigateNext: () -> Unit,
    viewModel: FeatureViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadData() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("화면 제목") }) }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (uiState) {
                is FeatureUiState.Loading -> DdgoFullScreenLoading(Modifier.fillMaxSize())
                is FeatureUiState.Success -> SuccessContent(
                    data = (uiState as FeatureUiState.Success).data
                )
                is FeatureUiState.Error -> Text((uiState as FeatureUiState.Error).message)
            }
        }
    }
}
```

---

## 7. 자주 하는 실수 & 해결법

| 실수 | 문제 | 해결법 |
|:-----|:-----|:-------|
| ViewModel에서 직접 `context` 사용 | 메모리 릭 위험 | `@ApplicationContext Context` 를 usecase/repository로 이동 |
| DTO를 UI에 직접 사용 | 서버 변경 시 앱 전체 수정 필요 | Mapper를 통해 Domain Model로 변환 후 사용 |
| Repository에 비즈니스 로직 | SRP 위반 | UseCase로 분리 |
| `runBlocking`을 ViewModel에서 사용 | ANR 위험 | `viewModelScope.launch` 사용 |
| domain에 `import android.*` | 테스트 불가, 의존성 오염 | Android 타입은 data 계층에만 |
| 에뮬레이터에서 localhost API 연결 안 됨 | 로컬호스트 주소 오인식 | [local.properties](file:///c:/ssafy/project-second/S14P21D204/DDGo-android/local.properties)에 `api.base.url=http://10.0.2.2:8080/` 으로 설정 |

---

## 8. DI(의존성 주입) 빠른 이해

**Hilt**가 모든 의존성 생성/주입을 담당합니다. 개발자는 **"어떻게 만드는지(Module)"** 와 **"무엇이 필요한지(@Inject)"** 만 선언하면 됩니다.

```
di/NetworkModule.kt     → Retrofit 인스턴스를 "이렇게 만든다" 선언
di/RepositoryModule.kt  → AuthRepository 요청 오면 AuthRepositoryImpl 줘 선언

AuthRepositoryImpl.kt   → @Inject constructor(authApi: AuthApi) → Hilt가 자동 주입
AuthViewModel.kt        → @Inject constructor(repo: AuthRepository) → Hilt가 자동 주입
```

> **새 클래스를 만들 때**: 생성자에 `@Inject constructor()`를 붙이면 Hilt가 자동으로 의존성을 주입합니다. 인터페이스 바인딩만 Module에 추가하면 됩니다.

---

## 9. WorkManager 흐름 이해

```
사용자 버튼 클릭
    ↓
UploadViewModel.startAnalyze()
    ↓ workManager.enqueue(VideoAnalyzeWorker)
WorkManager (OS 관리, 앱 종료해도 실행)
    ↓
VideoAnalyzeWorker.doWork()
    ├── poseEstimator.estimateFromVideo() → data/ml/mediapipe
    ├── holdDetector.detectFromVideo()   → data/ml/yolo
    ├── extractFailClipUseCase(poses, holds) → domain/usecase
    └── uploadApi.uploadVideo()          → data/remote/upload
```

---

## 10. [실습] Clean Architecture 한 사이클 돌아보기

신입 개발자를 위한 **Login API 연결 실습 가이드**가 준비되어 있습니다. 
아래 가이드를 따라하며 DDGo 프로젝트의 구조를 직접 익혀보세요.

👉 **[Login API 개발 실습 가이드 바로가기](login_api_development_practice.md)**

---

*문서 최종 업데이트: 2026-03-09*
