# SSAFY 특화 프로젝트 D204


# Git Commit Convention

## 커밋 메시지 구조

```
[<scope>]<type>: <subject>


```

---

## Type (필수)

| Type | 설명 |
|------|------|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `docs` | 문서 수정 (README, WIKI 등) |
| `refactor` | 코드 리팩토링 (기능 변경 없음) |
| `chore` | 빌드 설정, 패키지 매니저 설정 등 기타 자잘한 변경 |
| `revert` | 이전 커밋 되돌리기 |
| `remove` | 파일 삭제 |
| `design` | UI/UX 디자인 변경 (CSS 등) |

---

## Scope (필수)

- 간단하게 영역만 구분 





---

## Subject (필수)

- 50자 이내로 작성
- 마침표(.) 사용하지 않음
- 한글로 간결하게 작성
- 명령형으로 작성 (예: "추가", "수정", "삭제")



---

## 커밋 메시지 예시



### Scope 포함
```
[BE]fix: JWT 토큰 만료 시간 수정
```



---

## 브랜치 전략

### 브랜치 네이밍

| 브랜치 | 용도 | 네이밍 규칙 |
|--------|------|-------------|
| `main` | 배포 가능한 상태 유지 | - |
| `dev` | 개발 통합 브랜치 | - |
| `feat/*` | 기능 개발 | `feat/기능명` |
| `fix/*` | 버그 수정 | `fix/버그명` |
| `hotfix/*` | 긴급 수정 | `hotfix/이슈명` |

### 브랜치 네이밍 예시

```
feature/user-auth
feature/game-room
fix/token-expiration
hotfix/login-error
```

---

## MR(Merge Request) / PR(Pull Request) 규칙

### 제목
```
[Type] 작업 내용 요약
```

### 예시
```
[BE]Feat 회원가입 및 로그인 API 구현
[BE]Fix 게임 점수 계산 오류 수정
[BE]Refactor 공통 응답 DTO 구조 개선
```

### 본문 템플릿
```markdown

## 📌 작업 요약
<!-- 무엇을 했는지 -->


---

## 🔗 관련 이슈
- #

---

## ✅ 체크
- [ ] 정상 동작 확인
- [ ] 로컬 테스트 완료


```

---
