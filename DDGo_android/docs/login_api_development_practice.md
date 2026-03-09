# [실습] Clean Architecture 기반 Login API 연결 가이드 (클론 코딩용)

> 이 문서는 신입 개발자가 DDGo 프로젝트의 Clean Architecture 구조를 이해하고, 실제 코드를 따라하며 첫 API 연결(Login)을 실습하기 위한 가이드입니다. 
> **UI가 없더라도 비즈니스 로직과 API 연결 틀을 완벽하게 구축하는 것**을 목표로 합니다.

---

## 1. 실습 목표
- 서버의 Login API (`POST /v1/users/login`)를 앱에 연결합니다.
- **MVVM + Clean Architecture** 패턴을 철저히 준수합니다.
- 모든 레이어의 코드를 직접 작성하여 데이터의 흐름을 이해합니다.
- **Unit Test**를 통해 UI 없이도 기능이 정상 작동함을 검증합니다.

---

## 2. 레이어별 클론 코딩 코드 (Good Practice)

### 🟢 Phase 1: Domain 계층 (의존성 최하단)

#### 1. `domain/model/AuthToken.kt`
서버 응답에 종속되지 않는 앱 내부용 데이터 모델입니다.
```kotlin
package com.ddgo.app.domain.model

/** 앱 내에서 사용하는 순수 인증 토큰 모델 */
data class AuthToken(
    val accessToken: String,
    val refreshToken: String
)
```

#### 2. `domain/repository/AuthRepository.kt`
데이터 소스를 추상화한 인터페이스입니다.
```kotlin
package com.ddgo.app.domain.repository

import com.ddgo.app.domain.model.AuthToken

interface AuthRepository {
    suspend fun login(username: String, password: String): Result<AuthToken>
}
```

---

### 🟡 Phase 2: Data 계층 (상세 구현)

#### 3. `data/remote/auth/AuthDto.kt`
서버 API 규격과 1:1 매핑되는 DTO입니다.
```kotlin
package com.ddgo.app.data.remote.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequestDto(
    val username: String,
    val password: String
)

@Serializable
data class LoginResponseDto(
    val accessToken: String,
    val refreshToken: String
)
```

#### 4. `data/remote/auth/AuthApi.kt`
Retrofit 인터페이스입니다.
```kotlin
package com.ddgo.app.data.remote.auth

import com.ddgo.app.data.remote.common.ApiResponse
import retrofit2.http.Body
import retrofit2.http.POST

interface AuthApi {
    @POST("v1/users/login")
    suspend fun login(@Body request: LoginRequestDto): ApiResponse<LoginResponseDto>
}
```

#### 5. `data/mapper/AuthMapper.kt`
DTO를 Domain 모델로 변환하는 변환기입니다.
```kotlin
package com.ddgo.app.data.mapper

import com.ddgo.app.data.remote.auth.LoginResponseDto
import com.ddgo.app.domain.model.AuthToken

object AuthMapper {
    fun LoginResponseDto.toDomain(): AuthToken = AuthToken(
        accessToken = this.accessToken,
        refreshToken = this.refreshToken
    )
}
```

#### 6. `data/repository/AuthRepositoryImpl.kt`
실제 API를 호출하고 데이터를 저장하는 구현체입니다.
```kotlin
package com.ddgo.app.data.repository

import com.ddgo.app.core.datastore.TokenDataStore
import com.ddgo.app.data.mapper.AuthMapper.toDomain
import com.ddgo.app.data.remote.auth.AuthApi
import com.ddgo.app.data.remote.auth.LoginRequestDto
import com.ddgo.app.domain.model.AuthToken
import com.ddgo.app.domain.repository.AuthRepository
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val authApi: AuthApi,
    private val tokenDataStore: TokenDataStore
) : AuthRepository {
    override suspend fun login(username: String, password: String): Result<AuthToken> {
        return try {
            val response = authApi.login(LoginRequestDto(username, password))
            if (response.success && response.data != null) {
                // 토큰 로컬 저장
                tokenDataStore.saveTokens(
                    response.data.accessToken,
                    response.data.refreshToken
                )
                // 도메인 모델로 변환하여 반환
                Result.success(response.data.toDomain())
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
```

---

### 🧪 Phase 3: Logic & DI (연결 고리)

#### 7. `domain/usecase/LoginUseCase.kt`
단일 책임 원칙에 따른 로그인 비즈니스 로직입니다.
```kotlin
package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.AuthToken
import com.ddgo.app.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val repository: AuthRepository
) {
    suspend operator fun invoke(username: String, password: String): Result<AuthToken> {
        if (username.isBlank() || password.isBlank()) {
            return Result.failure(Exception("아이디와 비밀번호를 입력해주세요."))
        }
        return repository.login(username, password)
    }
}
```

#### 8. `feature/auth/AuthViewModel.kt`
UI 상태를 관리하는 ViewModel입니다. (UI 없이 테스트 가능)
```kotlin
package com.ddgo.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddgo.app.domain.usecase.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun login(username: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            loginUseCase(username, password)
                .onSuccess { _uiState.value = AuthUiState.Success }
                .onFailure { _uiState.value = AuthUiState.Error(it.message ?: "로그인 실패") }
        }
    }
}

sealed class AuthUiState {
    object Idle : AuthUiState()
    object Loading : AuthUiState()
    object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}
```

---

### 🧪 Phase 4: Verification (검증)

#### 9. `app/src/test/java/com/ddgo/app/domain/usecase/LoginUseCaseTest.kt`
```kotlin
package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.AuthToken
import com.ddgo.app.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LoginUseCaseTest {
    private val repository: AuthRepository = mockk()
    private lateinit var loginUseCase: LoginUseCase

    @Before
    fun setUp() {
        loginUseCase = LoginUseCase(repository)
    }

    @Test
    fun `로그인 성공 시 AuthToken을 반환해야 한다`() = runBlocking {
        // Given
        val expectedToken = AuthToken("access_val", "refresh_val")
        coEvery { repository.login("user", "pass") } returns Result.success(expectedToken)

        // When
        val result = loginUseCase("user", "pass")

        // Then
        assert(result.isSuccess)
        assertEquals(expectedToken, result.getOrNull())
    }

    @Test
    fun `빈 값이 입력되면 실패를 반환해야 한다`() = runBlocking {
        // When
        val result = loginUseCase("", "")

        // Then
        assert(result.isFailure)
        assertEquals("아이디와 비밀번호를 입력해주세요.", result.exceptionOrNull()?.message)
    }
}
```

---

### 🌐 Phase 5: 통합 테스트 (실서버 연동 검증)

> Unit Test가 **"UseCase 로직이 올바른가?"** 를 검증한다면,  
> Integration Test는 **"실제 서버와 통신이 잘 되는가?"** 를 검증합니다.

#### 10. `app/src/test/java/com/ddgo/app/data/remote/auth/AuthApiIntegrationTest.kt`

이 테스트 파일은 Hilt DI 없이 Retrofit을 직접 생성하여 **실 서버와의 HTTP 통신 자체**를 테스트합니다.

```kotlin
package com.ddgo.app.data.remote.auth

import com.ddgo.app.data.remote.common.ApiResponse
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * 실서버(localhost:8080)와의 통신을 테스트하는 통합 테스트 코드입니다.
 * 실제 서버가 실행 중이어야 테스트가 통과합니다.
 */
class AuthApiIntegrationTest {

    private lateinit var authApi: AuthApi
    private val baseUrl = "http://localhost:8080/"

    @Before
    fun setUp() {
        val json = Json {
            ignoreUnknownKeys = true   // 서버에 필드가 추가돼도 앱이 안 깨짐
            coerceInputValues = true   // null 값을 기본값으로 처리
        }
        val okHttpClient = OkHttpClient.Builder().build()
        val retrofit = Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

        authApi = retrofit.create(AuthApi::class.java)
    }

    @Test
    fun `실제_서버_로그인_테스트`() = runBlocking {
        // Given: 서버에 실제로 존재하는 계정 정보로 교체하세요.
        val request = LoginRequestDto(
            username = "string",
            password = "stringst"
        )

        try {
            // When: 실제 HTTP 요청 전송
            val response: ApiResponse<LoginResponseDto> = authApi.login(request)

            // Then: 응답 객체가 null이 아님을 검증
            println("Response: $response")
            assertNotNull(response)
            assertTrue("로그인에 성공해야 합니다.", response.success)

        } catch (e: Exception) {
            e.printStackTrace()
            assertTrue("서버가 실행 중이지 않거나 연결 오류가 발생했습니다: ${e.message}", false)
        }
    }
}
```

---

#### 💡 Unit Test vs Integration Test 비교

| 항목 | Unit Test (`LoginUseCaseTest`) | Integration Test (`AuthApiIntegrationTest`) |
|---|---|---|
| 검증 대상 | UseCase의 비즈니스 로직 | 실제 서버와의 HTTP 통신 |
| 실행 환경 | 서버 불필요 | **서버가 반드시 실행 중이어야 함** |
| 의존성 | `mockk`로 Repository를 모킹 | **실제 Retrofit, 실제 서버** 사용 |
| 속도 | 빠름 (순수 Kotlin) | 느림 (네트워크 I/O 포함) |
| 사용 시점 | 로직 변경 시 항상 | API 엔드포인트 추가/변경 시 |

---

#### ⚠️ 통합 테스트 작성 시 주의사항

1.  **서버 주소**: 에뮬레이터에서 로컬 서버에 접속할 때는 `localhost` 대신 **`10.0.2.2`** 를 사용합니다.  
    (JVM 기반 단위 테스트는 `localhost` 그대로 사용 가능)

2.  **실행 조건**: 이 테스트는 `./gradlew test` 또는 Android Studio에서 직접 실행합니다.  
    **서버가 꺼져 있으면 반드시 실패**합니다. CI/CD에 포함할 때 주의하세요.

3.  **테스트 계정**: `username`/`password`는 반드시 **서버에 실제로 존재하는 계정**으로 교체해야 합니다.

4.  **새 API를 추가할 때** 이 패턴을 그대로 복사해서 사용하세요:
    - 새 `XxxApi` 인터페이스 작성 → `setUp()`에서 `retrofit.create(XxxApi::class.java)` 추가 → `@Test` 함수 작성

---

## 3. 핵심 아키텍처 규칙 체크리스트

1.  **순수 도메인**: `AuthToken`이나 `LoginUseCase`에서 `import android.*`가 포함되어 있나요? (절대 없어야 함)
2.  **데이터 은닉**: ViewModel에서 `AuthApi`나 `LoginResponseDto`를 직접 참조하고 있나요? (반드시 `UseCase`와 `AuthToken`만 참조해야 함)
3.  **예외 처리**: `AuthRepositoryImpl`에서 `try-catch`로 네트워크 예외를 잡아 `Result.failure`로 변환했나요?
4.  **Hilt 연결**: `NetworkModule`에서 `AuthApi`를 제공하고, `RepositoryModule`에서 `AuthRepositoryImpl`을 바인딩했나요?

이 구조를 완벽히 이해하고 클론 코딩을 마쳤다면, 여러분은 DDGo 프로젝트의 어떤 API도 자신 있게 연결할 수 있습니다!
