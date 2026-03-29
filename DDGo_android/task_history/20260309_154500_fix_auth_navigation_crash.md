fix(auth): resolve crash during navigation after login by ensuring UI state sync and safe backstack popping

## 1. 재현 방법 (Reproduction Steps)
1. 앱 실행 후 정상 로그인하여 홈 화면 진입 확인
2. 앱 정보에서 데이터 삭제 수행 (토큰 등 로컬 데이터 초기화)
3. 앱 재실행 후 로그인(LoginPasswordScreen) 화면으로 이동
4. 동일한 계정 정보 입력 후 '로그인' 버튼 클릭 시 `IllegalArgumentException` 발생하며 앱 종료

## 2. 원인 분석 (Root Cause Analysis)
- **비동기 상태 불일치:** `LoginPasswordScreen`에서 `viewModel.login()` 호출 직후, API 응답(로그인 성공)을 기다리지 않고 `onLoginComplete()` 콜백을 즉시 실행함.
- **백스택 참조 오류:** `NavGraph`에서 `onLoginSuccess` 시점에 `popUpTo(ScreenRoutes.Auth.route)`를 호출했으나, 데이터 삭제 후 재실행된 상태에서 내비게이션 상태가 불안정하거나 해당 라우트가 아직 백스택에 완전히 등록되지 않은 찰나에 접근을 시도함.
- **스코프 중첩:** `NavGraph`에서 생성된 `AuthViewModel`을 하위 그래프에 직접 주입하면서, 내비게이션 그래프의 수명 주기와 상태 관리가 꼬임 발생.

## 3. 해결 방안 (Solution)
- **UI 상태 기반 이벤트 처리:** `LoginPasswordScreen`에서 `LaunchedEffect`를 사용하여 `AuthUiState.Success` 상태가 감지될 때만 `onLoginComplete` 콜백이 실행되도록 수정.
- **안전한 백스택 제거:** `NavGraph`에서 특정 라우트 이름 대신 `popUpTo(0) { inclusive = true }`를 사용하여 모든 이전 화면(Splash, Auth 등)을 확실히 비우고 `MainScreen`으로 이동하도록 변경.
- **그래프 스코프 ViewModel 적용:** `AuthNavigation`에서 `hiltViewModel(parentEntry)`를 사용하여 `auth_graph` 내에서만 유지되는 ViewModel 스코프를 적용하여 상태의 원자성(Atomicity) 확보.

## 4. 재발 방지 (Prevention)
- **상태 기반 내비게이션:** 버튼 클릭 시 즉시 화면 이동을 하지 않고, 항상 `ViewModel`의 `StateFlow` 성공 상태를 관찰하여 이동하는 패턴을 강제함.
- **내비게이션 앵커 최적화:** 특정 그래프를 팝업할 때 해당 그래프가 확실히 존재하지 않을 가능성이 있는 시나리오(데이터 초기화 후 진입 등)에서는 백스택을 완전히 초기화하는 안전한 팝업 방식을 우선 사용.
- **아키텍처 가이드 준수:** `NavGraph`는 경로만 정의하고, `ViewModel`은 각 `Screen` 또는 서브 그래프(`authGraph`) 내부에서 주입받아 관심사를 분리함.

---

# antigravity가 버그 못 고친 회고록

## 1. 내가 버그를 만든 이유

원래 `AuthNavigation.kt` 코드를 리팩토링할 때, `NavGraph`에서 `ViewModel`을 파라미터로 넘겨주는 방식이 안티패턴임을 인지하고, **"하위 그래프(`auth_graph`) 내에서 공유되는 `ViewModel`을 만들자"** 라고 판단하여 다음과 같은 코드를 작성했습니다.

```kotlin
val viewModel: AuthViewModel = hiltViewModel(
    navController.getBackStackEntry(ScreenRoutes.Auth.route)
)
```

이 코드가 문법적으로나, 일반적인 화면 전환 상황에서는 완벽하게 동작했기 때문에 버그가 잠재되어 있다는 사실을 인지하지 못했습니다. `auth_graph`가 살아 있는 동안에는 언제나 정상적으로 `AuthViewModel` 인스턴스를 반환해 주었으니까요.

---

## 2. 내가 버그를 단번에 고치지 못하고 헤맨 이유

에러 로그(`No destination with route auth_graph is on the NavController's back stack`)를 확인했을 때, 에러가 발생하는 시점이 **"로그인 성공 후 화면이 전환(Pop)되는 순간"** 이라는 것을 파악했습니다.

이때의 첫 번째 문제 해결 접근 방식은 다음과 같았습니다.

> "Recomposition 도중에 `navController.getBackStackEntry`가 다시 호출되면서 터지는구나! 그렇다면 `remember`로 감싸서 한 번만 호출되게 막아보자!"

```kotlin
val parentEntry = remember(backStackEntry) {
    navController.getBackStackEntry(ScreenRoutes.Auth.route)
}
val viewModel: AuthViewModel = hiltViewModel(parentEntry)
```

### 왜 이 접근이 근본적인 해결책이 아니었는가

- **에러의 본질을 외면했습니다.**  
  `remember`로 감싸서 크래시를 "숨기는" 데는 성공했을지 모르지만, `NavController`가 애니메이션 중에 백스택 엔트리를 제대로 관리하지 못하는 근본적인 원인을 고친 것은 아니었습니다.

- **Compose Navigation의 동작을 얕게 이해했습니다.**  
  화면 전환(`Navigate & PopUpTo`)이 일어날 때 컴포저블 블록 전체가 어떤 순서로 파괴되고 리컴포지션되는지 깊게 고민하지 않고, 일차원적인 예외 처리에 급급했습니다.

- **UI State 동기화 누락 시점을 간과했습니다.**  
  `LoginPasswordScreen`에서 로그인 버튼을 누르자마자 API 응답을 기다리지 않고 `onLoginComplete()`를 호출하는 치명적인 버그(비동기 상태 불일치)가 베이스에 깔려있었는데, 내비게이션 에러 로그만 보고 ViewModel 주입 방식만 의심했습니다.

---

## 3. 사용자님의 방식이 더 우수한 이유

`20260309_154500_fix_auth_navigation_crash.md`에 작성된 버그 리포트와 수정된 코드는 이 문제를 훨씬 더 아키텍처 관점에서 우아하게 해결했습니다.

- **UI State 기반 내비게이션 강제 (`LaunchedEffect`)**  
  가장 훌륭한 조치는 버튼 클릭 시점이 아니라, 실제로 로그인 API 연동 후 `AuthUiState.Success` 상태가 되었을 때만 내비게이션 콜백(`onLoginComplete`)이 동작하도록 강제한 것입니다.

- **안전한 PopUpTo (`popUpTo(0)`)**  
  `popUpTo(ScreenRoutes.Auth.route)`처럼 특정 라우트를 지목하면, 앱 초기화 시점이나 딥링크 진입 등 백스택이 예상과 다를 때 무조건 터집니다. 반면 `popUpTo(0)`은 기존의 모든 찌꺼기를 깔끔하게 비우고 메인 화면을 새 루트로 쌓는 매우 안전하고 확실한 방식입니다.

- **스코프 분리에 철저함**  
  `NavGraph`가 `ViewModel`의 생명주기를 책임지는 기존의 스파게티 구조를 끊어내고, 내비게이션 라우팅 정의와 비즈니스 로직(ViewModel) 주입의 관심사를 완벽하게 분리했습니다.

---

## 결론

저는 드러난 "에러 메시지" 자체를 막는 데 급급한 **Patch** 적인 접근을 했고, 사용자님은 "상태 동기화"와 "백스택 관리의 안전성"이라는 근본적인 아키텍처 원칙(**Root Cause**)을 바로잡는 훌륭한 설계를 보여주셨습니다. 한 수 배웠습니다!