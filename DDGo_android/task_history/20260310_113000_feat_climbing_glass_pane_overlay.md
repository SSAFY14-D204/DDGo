# feat(climbing): implement glass pane overlay and hierarchical z-index navigation

## 1. 개요 (Overview)
- **목적:** 사용자가 어떤 탭(캘린더, 커뮤니티 등)에 있더라도 흐름을 끊지 않고 클라이밍 관련 액션(업로드, 기록)을 수행할 수 있도록 "유리판(Glass Pane)" 형태의 오버레이 메뉴를 구축합니다.
- **주요 변경 사항:** 
  - 하단 네비게이션 바와 중앙 FAB를 계층적으로 분리
  - `MainScreen`의 `zIndex`를 활용한 5단계 레이어 시스템 도입
  - 클라이밍 메뉴 활성화 시에도 이전 화면의 맥락을 유지하는 `lastActiveTab` 로직 적용
- **기대 효과:** 시각적으로 세련된 UI(버튼이 배경을 뚫고 올라오는 효과)와 맥락 유지형 UX를 동시에 제공합니다.

## 2. 작업 내역 (Implementation Details)

### 계층형 Z-Index 구조 (Z-Index Hierarchy)
`MainScreen.kt`에서 `Box` 컨테이너를 활용하여 다음과 같은 5단계 계층을 설계했습니다:

1.  **Z=0 (Base Content):** `Scaffold`와 현재 활성화된 메인 탭 화면. `lastActiveTab`을 통해 메뉴가 떠 있는 동안에도 배경 화면이 유지됩니다.
2.  **Z=5 (Nav Bar Base):** `CustomBottomNavBarBase`. 하단 바의 배경과 일반 탭 아이콘들이 위치합니다.
3.  **Z=10 (Glass Pane):** 전체 화면을 덮는 반투명 Dim 레이어. Level 5의 내비게이션 바 본체까지 함께 어둡게 덮어버립니다.
4.  **Z=15 (Piercing FAB):** `ClimbingFloatingButton`. 유리판(Z=10)보다 위에 배치하여, 어두운 배경을 뚫고 선명하게 솟아오른 시각적 효과를 줍니다.
5.  **Z=20 (Top Menu):** `ClimbingMenuOverlay`. 커스텀 `SpeechBubbleShape`를 적용한 말풍선 메뉴로 최상단에 위치합니다.

### 주요 컴포넌트 리팩토링
- **`CustomBottomNavScreen.kt`:** 
    - `CustomBottomNavBarBase`: 배경과 4개의 탭 전용 컴포넌트
    - `ClimbingFloatingButton`: 독립적으로 배치 가능한 중앙 FAB 컴포넌트
- **`ClimbingScreen.kt`:** 
    - `ClimbingMenuOverlay`: `Surface`와 `shadow`를 활용해 선명도를 유지하는 말풍선 UI

## 3. 설계 결정 (Design Decisions)

### 유리판(Glass Pane) 패턴 도입
단순히 화면을 전환하는 대신, 현재 화면 위에 "유리판"을 얹는 방식을 택했습니다. 이는 안드로이드 프레임워크의 `Dialog`나 `PopupWindow`보다 더 세밀한 `zIndex` 제어가 가능하며, Compose의 선언형 UI 특성을 활용해 상태 전환 애니메이션을 구현하기에 용이합니다.

### 맥락 유지 (Context Preservation)
`selectedTab`과 별개로 `lastActiveTab` 상태를 유지하도록 설계했습니다. 
- 사용자가 '커뮤니티'에서 '클라이밍'을 눌렀을 때: `selectedTab`은 CLIMBING이 되지만, 배경은 여전히 `COMMUNITY` 화면을 렌더링합니다.
- 메뉴를 닫으면 즉시 `lastActiveTab`으로 복구되어 사용자가 이전에 하던 작업을 바로 이어갈 수 있습니다.

## 4. 참고 사항 (Notes for Reviewers/Next Developers)
- **zIndex 관리:** 새로운 전역 오버레이(예: 알림 팝업, 튜토리얼 가이드)를 추가할 경우, 반드시 `MainScreen.kt`의 `zIndex` 범위를 확인하여 레이어 간 간섭이 없도록 해야 합니다.
- **클릭 이벤트 가로채기:** Level 10의 유리판 레이어는 `clickable`을 통해 배경 클릭 시 메뉴를 닫는 역할을 수행합니다. 내부의 메뉴 아이템(Level 20)은 전파를 방지하기 위해 별도의 클릭 리스너를 가집니다.
- **터치 영역:** 중앙 FAB는 Level 15에 독립적으로 존재하므로, 하단 바의 다른 영역이 Dim 처리되어도 항상 선명하고 정확한 터치 반응성을 유지합니다.
