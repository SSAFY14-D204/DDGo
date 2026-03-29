package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.LogoutResult
import com.ddgo.app.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LogoutUseCaseTest {
    private val repository: AuthRepository = mockk()
    private lateinit var logoutUseCase: LogoutUseCase

    @Before
    fun setUp() {
        logoutUseCase = LogoutUseCase(repository)
    }

    @Test
    fun `로그아웃 성공 시 Success를 반환해야 한다`() = runBlocking {
        // Given
        coEvery { repository.logout() } returns Result.success(LogoutResult.ServerConfirmed)

        // When
        val result = logoutUseCase()

        // Then
        assert(result.isSuccess)
        assertEquals(LogoutResult.ServerConfirmed, result.getOrNull())
    }

    @Test
    fun `로그아웃 실패 시 Failure를 반환해야 한다`() = runBlocking {
        // Given
        val expectedError = "로그아웃 실패"
        coEvery { repository.logout() } returns Result.failure(Exception(expectedError))

        // When
        val result = logoutUseCase()

        // Then
        assert(result.isFailure)
        assertEquals(expectedError, result.exceptionOrNull()?.message)
    }
}
