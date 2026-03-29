package com.ssafy.DDGo.challenges.application;

import com.ssafy.DDGo.attempts.dao.AttemptRepository;
import com.ssafy.DDGo.attempts.dao.ChallengeDoneAttemptCountProjection;
import com.ssafy.DDGo.challenges.dao.ChallengeRepository;
import com.ssafy.DDGo.challenges.domain.ChallengeResult;
import com.ssafy.DDGo.global.config.StaleActiveAutoCloseProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChallengeAutoCloseServiceTest {

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private AttemptRepository attemptRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private TransactionStatus transactionStatus;

    private StaleActiveAutoCloseProperties properties;
    private ChallengeAutoCloseService challengeAutoCloseService;

    @BeforeEach
    void setUp() {
        properties = new StaleActiveAutoCloseProperties();
        properties.setStaleAfterHours(6);
        properties.setBatchSize(2);
        challengeAutoCloseService = new ChallengeAutoCloseService(
                challengeRepository,
                attemptRepository,
                transactionTemplate,
                properties);

        lenient().doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        }).when(transactionTemplate).execute(any());
    }

    @Test
    @DisplayName("stale ACTIVE 후보가 없으면 아무 것도 닫지 않는다")
    void closeStaleActiveChallenges_withoutCandidates() {
        when(challengeRepository.findStaleActiveChallengeIds(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of());

        ChallengeAutoCloseService.AutoCloseRunSummary summary = challengeAutoCloseService.closeStaleActiveChallenges();

        assertThat(summary.getScanned()).isZero();
        assertThat(summary.getClosedUnknown()).isZero();
        assertThat(summary.getClosedFail()).isZero();
        assertThat(summary.getSkippedAlreadyClosed()).isZero();
        assertThat(summary.getFailed()).isZero();
        verify(attemptRepository, never()).countDoneAttemptsByChallengeIds(any());
        verify(challengeRepository, never()).closeIfActive(anyLong(), any(), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("배치 단위로 stale ACTIVE를 돌며 DONE 수 기준으로 UNKNOWN/FAIL로 닫는다")
    void closeStaleActiveChallenges_closesWithExpectedResultsAcrossBatches() {
        when(challengeRepository.findStaleActiveChallengeIds(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(1L, 2L))
                .thenReturn(List.of(3L));
        when(attemptRepository.countDoneAttemptsByChallengeIds(List.of(1L, 2L)))
                .thenReturn(List.of(new Projection(2L, 1L)));
        when(attemptRepository.countDoneAttemptsByChallengeIds(List.of(3L)))
                .thenReturn(List.of(new Projection(3L, 2L)));
        when(challengeRepository.closeIfActive(eq(1L), eq(ChallengeResult.UNKNOWN), any(LocalDateTime.class)))
                .thenReturn(1);
        when(challengeRepository.closeIfActive(eq(2L), eq(ChallengeResult.FAIL), any(LocalDateTime.class)))
                .thenReturn(1);
        when(challengeRepository.closeIfActive(eq(3L), eq(ChallengeResult.FAIL), any(LocalDateTime.class)))
                .thenReturn(1);

        ChallengeAutoCloseService.AutoCloseRunSummary summary = challengeAutoCloseService.closeStaleActiveChallenges();

        assertThat(summary.getScanned()).isEqualTo(3);
        assertThat(summary.getClosedUnknown()).isEqualTo(1);
        assertThat(summary.getClosedFail()).isEqualTo(2);
        assertThat(summary.getSkippedAlreadyClosed()).isZero();
        assertThat(summary.getFailed()).isZero();
        verify(attemptRepository).countDoneAttemptsByChallengeIds(List.of(1L, 2L));
        verify(attemptRepository).countDoneAttemptsByChallengeIds(List.of(3L));
    }

    @Test
    @DisplayName("개별 close 실패나 이미 닫힌 상태는 집계만 남기고 다음 후보를 계속 처리한다")
    void closeStaleActiveChallenges_recordsSkippedAndFailed() {
        when(challengeRepository.findStaleActiveChallengeIds(any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(10L, 11L))
                .thenReturn(List.of());
        when(attemptRepository.countDoneAttemptsByChallengeIds(List.of(10L, 11L)))
                .thenReturn(List.of(new Projection(11L, 1L)));
        when(challengeRepository.closeIfActive(eq(10L), eq(ChallengeResult.UNKNOWN), any(LocalDateTime.class)))
                .thenReturn(0);
        doThrow(new IllegalStateException("db failure"))
                .when(challengeRepository)
                .closeIfActive(eq(11L), eq(ChallengeResult.FAIL), any(LocalDateTime.class));

        ChallengeAutoCloseService.AutoCloseRunSummary summary = challengeAutoCloseService.closeStaleActiveChallenges();

        assertThat(summary.getScanned()).isEqualTo(2);
        assertThat(summary.getClosedUnknown()).isZero();
        assertThat(summary.getClosedFail()).isZero();
        assertThat(summary.getSkippedAlreadyClosed()).isEqualTo(1);
        assertThat(summary.getFailed()).isEqualTo(1);
        verify(challengeRepository, times(2)).closeIfActive(anyLong(), any(), any(LocalDateTime.class));
    }

    private static final class Projection implements ChallengeDoneAttemptCountProjection {
        private final Long challengeId;
        private final Long doneAttemptCount;

        private Projection(Long challengeId, Long doneAttemptCount) {
            this.challengeId = challengeId;
            this.doneAttemptCount = doneAttemptCount;
        }

        @Override
        public Long getChallengeId() {
            return challengeId;
        }

        @Override
        public Long getDoneAttemptCount() {
            return doneAttemptCount;
        }
    }
}
