# Backend 패키지 구조

## 개요

기능(도메인) 단위로 패키지를 구성하고, 각 기능 패키지 내부를 계층별로 분리합니다.

## 패키지 구조

```
src/main/java/com/ssafy/DDGo/
│
├── global/                          # 공통 설정 및 유틸
│   ├── config/                      # 설정 클래스 (Security, Swagger, Redis 등)
│   ├── exception/                   # 공통 예외 처리
│   ├── common/                      # 공통 응답, 유틸리티
│   └── auth/                        # 인증/인가 관련 (JWT, Filter 등)
│
├── <feature>/                       # 기능(도메인) 패키지
│   ├── api/                         # Controller 계층
│   ├── application/                 # Service 계층 (비즈니스 로직)
│   ├── dao/                         # Repository 계층 (데이터 접근)
│   ├── domain/                      # Entity, Enum 등 도메인 모델
│   └── dto/                         # 데이터 전송 객체
│       ├── request/                 # 요청 DTO
│       └── response/                # 응답 DTO
│
└── DDGoApplication.java          # 메인 클래스
```

## 계층별 역할

### api

- `@RestController` 클래스가 위치합니다.
- 요청을 받아 Service로 위임하고 응답을 반환합니다.
- 비즈니스 로직을 포함하지 않습니다.

### application

- `@Service` 클래스가 위치합니다.
- 핵심 비즈니스 로직을 처리합니다.
- 트랜잭션 관리를 담당합니다.

### dao

- `@Repository` 인터페이스 및 구현체가 위치합니다.
- JPA Repository, QueryDSL, JDBC 등 데이터 접근 로직을 처리합니다.

### domain

- `@Entity` 클래스가 위치합니다.
- Enum, 값 객체(VO) 등 도메인 모델을 정의합니다.

### dto

- 계층 간 데이터 전달에 사용되는 객체입니다.
- `request/` : 클라이언트 → 서버 요청 데이터
- `response/` : 서버 → 클라이언트 응답 데이터

## 예시: `room` 기능

```
room/
├── api/
│   └── RoomController.java
├── application/
│   └── RoomService.java
├── dao/
│   └── RoomRepository.java
├── domain/
│   ├── Room.java
│   └── RoomStatus.java
└── dto/
    ├── request/
    │   ├── RoomCreateRequest.java
    │   └── RoomJoinRequest.java
    └── response/
        ├── RoomDetailResponse.java
        └── RoomListResponse.java
```

## 네이밍 규칙

| 계층 | 네이밍 패턴 | 예시 |
| --- | --- | --- |
| Controller | `<Feature>Controller` | `RoomController` |
| Service | `<Feature>Service` | `RoomService` |
| Repository | `<Feature>Repository` | `RoomRepository` |
| Entity | `<Feature>` | `Room` |
| Request DTO | `<Feature><Action>Request` | `RoomCreateRequest` |
| Response DTO | `<Feature><Detail>Response` | `RoomDetailResponse` |

## 주의 사항

- Controller에서 다른 도메인의 Service를 직접 호출하지 않습니다.
- 도메인 간 의존이 필요한 경우 자신의 Service를 통해 처리합니다.
- Entity를 Controller 응답으로 직접 반환하지 않고 반드시 Response DTO로 변환합니다.
- Request DTO에는 검증 어노테이션(`@NotNull`, `@Size` 등)을 적극 활용합니다.