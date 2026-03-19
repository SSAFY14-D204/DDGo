package com.ddgo.app.domain.usecase

import com.ddgo.app.domain.model.LogoutResult
import com.ddgo.app.domain.repository.AuthRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    fun `로그아웃 성공 시 성공 결과를 반환한다`() = runBlocking {
        coEvery { repository.logout() } returns Result.success(LogoutResult.ServerConfirmed)

        val result = logoutUseCase()

        assertTrue(result.isSuccess)
        assertEquals(LogoutResult.ServerConfirmed, result.getOrNull())
    }

    @Test
    fun `로그아웃 실패 시 failure를 반환한다`() = runBlocking {
        val expectedError = "로그아웃 실패"
        coEvery { repository.logout() } returns Result.failure(Exception(expectedError))

        val result = logoutUseCase()

        assertTrue(result.isFailure)
        assertEquals(expectedError, result.exceptionOrNull()?.message)
    }
}
