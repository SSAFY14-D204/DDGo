package com.ssafy.DDGo.attempts.application;

import com.ssafy.DDGo.attempts.dao.AttemptFeedbackRepository;
import com.ssafy.DDGo.attempts.dao.AttemptMetricsRepository;
import com.ssafy.DDGo.attempts.dao.AttemptRepository;
import com.ssafy.DDGo.challenges.dao.ChallengeAttemptCounterRepository;
import com.ssafy.DDGo.challenges.dao.ChallengeRepository;
import com.ssafy.DDGo.challenges.domain.Challenge;
import com.ssafy.DDGo.challenges.domain.ChallengeStatus;
import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import com.ssafy.DDGo.users.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttemptServiceTest {

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private ChallengeAttemptCounterRepository counterRepository;

    @Mock
    private AttemptRepository attemptRepository;

    @Mock
    private AttemptMetricsRepository attemptMetricsRepository;

    @Mock
    private AttemptFeedbackRepository attemptFeedbackRepository;

    @Mock
    private AttemptVideoService attemptVideoService;

    @InjectMocks
    private AttemptService attemptService;

    @Test
    @DisplayName("시도 시작 중 챌린지가 닫히면 CHALLENGE_ALREADY_CLOSED를 반환한다")
    void startAttempt_whenChallengeClosesDuringCounterIncrement_throwsAlreadyClosed() {
        String username = "testuser";
        Long challengeId = 1L;

        User user = User.builder().username(username).build();
        ReflectionTestUtils.setField(user, "id", 1L);

        Challenge activeChallenge = Challenge.builder()
                .user(user)
                .challengeStatus(ChallengeStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(activeChallenge, "id", challengeId);

        Challenge closedChallenge = Challenge.builder()
                .user(user)
                .challengeStatus(ChallengeStatus.CLOSED)
                .build();
        ReflectionTestUtils.setField(closedChallenge, "id", challengeId);

        when(challengeRepository.findById(challengeId))
                .thenReturn(Optional.of(activeChallenge))
                .thenReturn(Optional.of(closedChallenge));
        when(counterRepository.incrementAttemptNoIfChallengeActive(challengeId)).thenReturn(0);

        assertThatThrownBy(() -> attemptService.startAttempt(username, challengeId))
                .isInstanceOf(CustomException.class)
                .satisfies(exception ->
                        assertThat(((CustomException) exception).getErrorCode()).isEqualTo(ErrorCode.CHALLENGE_ALREADY_CLOSED));

        verify(attemptRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
