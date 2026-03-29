package com.ddgo.app.feature.profile

import com.ddgo.app.domain.model.User
import com.ddgo.app.domain.repository.AuthRepository
import com.ddgo.app.domain.usecase.CheckNicknameAvailabilityUseCase
import com.ddgo.app.domain.usecase.DeleteMeUseCase
import com.ddgo.app.domain.usecase.GetMyInfoUseCase
import com.ddgo.app.domain.usecase.LogoutUseCase
import com.ddgo.app.domain.usecase.UpdateNicknameUseCase
import com.ddgo.app.domain.usecase.UpdatePasswordUseCase
import com.ddgo.app.domain.usecase.UpdateProfileUseCase
import com.ddgo.app.feature.climbing.upload.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `reset to root closes all open editors`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openNicknameEditor()
        assertNotNull(viewModel.uiState.value.nicknameEditor)
        viewModel.resetToRoot()
        assertNull(viewModel.uiState.value.nicknameEditor)

        viewModel.openBodyProfileEditor()
        assertNotNull(viewModel.uiState.value.bodyProfileEditor)
        viewModel.resetToRoot()
        assertNull(viewModel.uiState.value.bodyProfileEditor)

        viewModel.openPasswordEditor()
        assertNotNull(viewModel.uiState.value.passwordEditor)
        viewModel.resetToRoot()
        assertNull(viewModel.uiState.value.passwordEditor)
    }

    @Test
    fun `reset to root keeps saving editor open`() = runTest {
        val updatePasswordGate = CompletableDeferred<Result<Unit>>()
        val updatePasswordUseCase = mockk<UpdatePasswordUseCase>()
        coEvery {
            updatePasswordUseCase(oldPassword = any(), newPassword = any())
        } coAnswers {
            updatePasswordGate.await()
        }
        val viewModel = createViewModel(
            updatePasswordUseCase = updatePasswordUseCase
        )
        advanceUntilIdle()

        viewModel.openPasswordEditor()
        viewModel.updateCurrentPasswordInput("OldPass123!")
        viewModel.updateNewPasswordInput("ValidPass123!")
        viewModel.updateConfirmPasswordInput("ValidPass123!")
        viewModel.submitPasswordChange()
        runCurrent()

        viewModel.resetToRoot()

        assertNotNull(viewModel.uiState.value.passwordEditor)
        assertTrue(viewModel.uiState.value.passwordEditor?.isSaving == true)

        updatePasswordGate.complete(Result.failure(IllegalStateException("cancelled for test")))
        advanceUntilIdle()
    }

    private fun createViewModel(
        getMyInfoUseCase: GetMyInfoUseCase = defaultGetMyInfoUseCase(),
        updateProfileUseCase: UpdateProfileUseCase = mockk(relaxed = true),
        updateNicknameUseCase: UpdateNicknameUseCase = mockk(relaxed = true),
        updatePasswordUseCase: UpdatePasswordUseCase = mockk(relaxed = true),
        logoutUseCase: LogoutUseCase = mockk(relaxed = true),
        deleteMeUseCase: DeleteMeUseCase = mockk(relaxed = true),
        checkNicknameAvailabilityUseCase: CheckNicknameAvailabilityUseCase = mockk(relaxed = true)
    ): ProfileViewModel {
        return ProfileViewModel(
            getMyInfoUseCase = getMyInfoUseCase,
            updateProfileUseCase = updateProfileUseCase,
            updateNicknameUseCase = updateNicknameUseCase,
            updatePasswordUseCase = updatePasswordUseCase,
            logoutUseCase = logoutUseCase,
            deleteMeUseCase = deleteMeUseCase,
            checkNicknameAvailabilityUseCase = checkNicknameAvailabilityUseCase
        )
    }

    private fun defaultGetMyInfoUseCase(): GetMyInfoUseCase {
        val repository = mockk<AuthRepository>()
        coEvery { repository.getMyInfo() } returns Result.success(
            User(
                id = 1L,
                username = "tester@example.com",
                email = "tester@example.com",
                nickname = "tester",
                sex = "M",
                heightCm = 175f,
                weightKg = 70f,
                wingspanCm = 178f
            )
        )
        return GetMyInfoUseCase(repository)
    }
}
