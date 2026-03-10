feat(main): implement root navigation architecture & common bottom navigation bar

## 1. 개요 (Overview)
- **목적:** 로그인/회원가입에서 인증을 거친 사용자가 접근하는 앱의 핵심 메인 화면 구조(5개의 탭)를 구축하고, 네비게이션을 연동하기 위함입니다.
- **주요 변경 사항:** 
  - 인증(Auth) 흐름과 메인(Main) 탭 화면을 구분하는 라우팅 시스템 강화
  - 공통 하단 네비게이션(CustomBottomNavBar)을 메인 화면과 연동 
  - 토큰 갱신(Splash) 실패 및 재발급 처리 적용
- **기대 효과:** 이후 다른 개발자가 캘린더, 커뮤니티 등의 메인 탭 화면 개발에만 집중할 수 있는 골격이 완성되었습니다.

## 2. 작업 내역 (Implementation Details)
- **추가된 화면/컴포넌트:**
    - `feature/calendar/CalendarScreen.kt`, `feature/community/CommunityScreen.kt`, `feature/climbing/ClimbingScreen.kt`, `feature/analysis/AnalysisScreen.kt`, `feature/profile/ProfileScreen.kt`: 5개의 메인 탭에 대응하는 Placeholder 화면을 추가했습니다.
    - `CustomBottomNavScreen.kt`: 내부적으로 상태(selectedIndex)를 관리하던 기존 UI 컴포넌트에서, 상태와 클릭 이벤트(`selectedIndex`, `onTabSelected`)를 주입받아 동작하는 Dumb Component로 리팩토링했습니다.
    - `MainScreen.kt`: `Scaffold`와 `CustomBottomNavigationBar`를 활용하여 5개 탭을 관리하는 메인 컨테이너 화면으로 재작성되었습니다. 
- **비즈니스 로직 / 상태 관리:**
    - `SplashViewModel.kt`: 시작 시 `DataStore`에서 `accessToken`을 검사하고, 유효하지 않으면 `refreshToken`으로 API 호출을 시도(최대 2회)한 뒤 실패 시에만 Auth 화면으로 보내는 로직을 추가했습니다.
    - `LoginPasswordScreen.kt`: 로그인 동작(`viewModel.login()`)과 결과 리스너(`onLoginComplete()`)를 동기적으로 처리하는 기존 방식에서, `LaunchedEffect`를 통해 `AuthUiState.Success` 상태를 구독하여 비동기 응답 후 화면을 전환하도록 변경했습니다.
- **데이터 흐름 (API / 로컬 DB):**
    - `ScreenRoutes.kt`: 5개의 탭 화면(Calendar, Community, Climbing, Analysis, Profile) 라우트를 추가했습니다.
    - `AuthNavigation.kt`: `viewModel` 파라미터를 외부에서 주입받지 않고 내부적으로 `hiltViewModel(parentEntry)`를 호출하여 로그인, 회원가입 단계에서 ViewModel의 상태를 원자적으로 공유하도록 개선했습니다. 이 과정에서 팝업 시 크래시를 방지하기 위해 `remember(backStackEntry)` 형태로 안전성을 확보했습니다.

## 3. 설계 결정 (Design Decisions)
- **MainScreen 탭 상태 관리:** 탭 전환 간 상태와 백스택을 파편화하지 않고, 하나의 `MainScreen` Compose 내에서 `rememberSaveable { mutableIntStateOf() }` 상태만으로 화면을 전환(`when(selectedTab)`)하는 방식을 적용했습니다. 이는 복잡한 중첩 네비게이션(Nested Navigation graph)의 오버헤드를 줄이고 데이터 공유를 용이하게 함을 목적으로 합니다.
- **AuthViewModel 범위 공유 (ViewModel Scoping):** `NavGraphBuilder.authGraph` 선언 내에서 `navController.getBackStackEntry(ScreenRoutes.Auth.route)`를 통해 최상단 루트 엔트리를 가져와 `hiltViewModel()` 스코프를 묶었습니다. 이를 통해 이메일 입력 화면부터 패스워드 화면까지 하나의 ViewModel 인스턴스로 사용자 입력을 유지합니다. 

## 4. 참고 사항 (Notes for Reviewers/Next Developers)
- **하단 네비게이션 활성화 연동:** 각 `*Screen.kt` 개발 담당자는 화면 내부에 네비게이션 로직을 둘 필요 없이(화면 자체는 UI만 집중), 메인 화면 `MainScreen.kt`이나 최상위 라우트에 네비게이션 상태를 위임하면 됩니다.
- **레거시 정리 필요:** 기존 `UploadScreen` 및 `ReportScreen` 라우트 코드는 앱이 새 구조로 넘어갈 때까지 호환되도록 주석과 함께 보존하였으며, 나중에 `ClimbingScreen`이나 `AnalysisScreen` 내부로 병합될 예정입니다.
