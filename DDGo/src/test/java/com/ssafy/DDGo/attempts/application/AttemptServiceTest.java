package com.ssafy.DDGo.attempts.application;

import com.ssafy.DDGo.attempts.dao.AttemptFeedbackRepository;
import com.ssafy.DDGo.attempts.dao.AttemptHeartRateSampleRepository;
import com.ssafy.DDGo.attempts.dao.AttemptMetricsRepository;
import com.ssafy.DDGo.attempts.dao.AttemptRepository;
import com.ssafy.DDGo.attempts.dao.AttemptStabilityPointRepository;
import com.ssafy.DDGo.attempts.domain.Attempt;
import com.ssafy.DDGo.attempts.domain.AttemptHeartRateSample;
import com.ssafy.DDGo.attempts.domain.AttemptMetrics;
import com.ssafy.DDGo.attempts.domain.AttemptResult;
import com.ssafy.DDGo.attempts.domain.AttemptStabilityPoint;
import com.ssafy.DDGo.attempts.domain.AttemptStatus;
import com.ssafy.DDGo.attempts.dto.request.AttemptEndRequest;
import com.ssafy.DDGo.attempts.dto.response.AttemptDetailResponse;
import com.ssafy.DDGo.attempts.dto.response.AttemptFullResponse;
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
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private AttemptStabilityPointRepository attemptStabilityPointRepository;

    @Mock
    private AttemptHeartRateSampleRepository attemptHeartRateSampleRepository;

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

        verify(attemptRepository, never()).save(any());
    }

    @Test
    @DisplayName("시도 종료 시 확장 metrics와 raw series를 새로 저장한다")
    void endAttempt_savesExtendedMetricsAndRawSeriesWhenAbsent() {
        String username = "testuser";
        Long challengeId = 1L;
        Long attemptId = 10L;

        Attempt attempt = createProcessingAttempt(username, challengeId, attemptId);
        AttemptEndRequest request = new AttemptEndRequest(
                new AttemptEndRequest.BaseData(AttemptResult.SUCCESS, 12345, 7),
                new AttemptEndRequest.MetricsData(0.82, 78, 0.66, 81, 88, 5, 2100, 3, "왼팔"),
                null,
                List.of(
                        new AttemptEndRequest.StabilityTimelinePoint(0L, 0.72),
                        new AttemptEndRequest.StabilityTimelinePoint(500L, 0.81)),
                List.of(
                        new AttemptEndRequest.HeartRateSample(0L, 122),
                        new AttemptEndRequest.HeartRateSample(500L, 128)));

        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(attemptMetricsRepository.findByAttemptId(attemptId)).thenReturn(Optional.empty());

        AttemptDetailResponse response = attemptService.endAttempt(username, challengeId, attemptId, request);

        ArgumentCaptor<AttemptMetrics> metricsCaptor = ArgumentCaptor.forClass(AttemptMetrics.class);
        verify(attemptMetricsRepository).save(metricsCaptor.capture());
        AttemptMetrics savedMetrics = metricsCaptor.getValue();

        assertThat(savedMetrics.getCenterStabilityRatio()).isEqualTo(0.82);
        assertThat(savedMetrics.getStabilityRecoveryScore()).isEqualTo(78);
        assertThat(savedMetrics.getStableContactRatio()).isEqualTo(0.66);
        assertThat(savedMetrics.getLowerBodyDriveScore()).isEqualTo(81);
        assertThat(savedMetrics.getOverallMovementScore()).isEqualTo(88);
        assertThat(savedMetrics.getCruxHoldNo()).isEqualTo(5);
        assertThat(savedMetrics.getCruxDurationMs()).isEqualTo(2100);
        assertThat(savedMetrics.getDangerEventCount()).isEqualTo(3);
        assertThat(savedMetrics.getLoadFocusLabel()).isEqualTo("왼팔");

        verify(attemptStabilityPointRepository).deleteByAttemptId(attemptId);
        verify(attemptHeartRateSampleRepository).deleteByAttemptId(attemptId);

        ArgumentCaptor<List<AttemptStabilityPoint>> stabilityCaptor = ArgumentCaptor.forClass(List.class);
        verify(attemptStabilityPointRepository).saveAll(stabilityCaptor.capture());
        assertThat(stabilityCaptor.getValue()).hasSize(2);
        assertThat(stabilityCaptor.getValue().get(0).getPointOrder()).isEqualTo(0);
        assertThat(stabilityCaptor.getValue().get(1).getTimestampMs()).isEqualTo(500L);

        ArgumentCaptor<List<AttemptHeartRateSample>> heartCaptor = ArgumentCaptor.forClass(List.class);
        verify(attemptHeartRateSampleRepository).saveAll(heartCaptor.capture());
        assertThat(heartCaptor.getValue()).hasSize(2);
        assertThat(heartCaptor.getValue().get(0).getSampleOrder()).isEqualTo(0);
        assertThat(heartCaptor.getValue().get(1).getBpm()).isEqualTo(128);

        assertThat(response.attemptResult()).isEqualTo(AttemptResult.SUCCESS);
        assertThat(response.durationMs()).isEqualTo(12345);
        assertThat(response.maxHoldNo()).isEqualTo(7);
        assertThat(attempt.getAttemptStatus()).isEqualTo(AttemptStatus.DONE);
    }

    @Test
    @DisplayName("시도 종료 시 기존 raw series를 비우고 새 값으로 교체한다")
    void endAttempt_replacesRawSeriesWhenPresent() {
        String username = "testuser";
        Long challengeId = 1L;
        Long attemptId = 11L;

        Attempt attempt = createProcessingAttempt(username, challengeId, attemptId);
        AttemptMetrics metrics = AttemptMetrics.builder()
                .attempt(attempt)
                .centerStabilityRatio(0.11)
                .stabilityRecoveryScore(10)
                .stableContactRatio(0.22)
                .lowerBodyDriveScore(30)
                .overallMovementScore(40)
                .cruxHoldNo(1)
                .cruxDurationMs(500)
                .dangerEventCount(0)
                .loadFocusLabel("코어")
                .build();

        AttemptEndRequest request = new AttemptEndRequest(
                new AttemptEndRequest.BaseData(AttemptResult.FAIL, 5432, 4),
                new AttemptEndRequest.MetricsData(0.57, 64, 0.49, 72, 77, 3, 1500, 2, "오른다리"),
                null,
                List.of(new AttemptEndRequest.StabilityTimelinePoint(1000L, 0.57)),
                List.of(new AttemptEndRequest.HeartRateSample(1000L, 141)));

        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(attemptMetricsRepository.findByAttemptId(attemptId)).thenReturn(Optional.of(metrics));

        attemptService.endAttempt(username, challengeId, attemptId, request);

        verify(attemptMetricsRepository, never()).save(any());
        verify(attemptStabilityPointRepository).deleteByAttemptId(attemptId);
        verify(attemptHeartRateSampleRepository).deleteByAttemptId(attemptId);

        assertThat(metrics.getCenterStabilityRatio()).isEqualTo(0.57);
        assertThat(metrics.getStabilityRecoveryScore()).isEqualTo(64);
        assertThat(metrics.getStableContactRatio()).isEqualTo(0.49);
        assertThat(metrics.getLowerBodyDriveScore()).isEqualTo(72);
        assertThat(metrics.getOverallMovementScore()).isEqualTo(77);
        assertThat(metrics.getCruxHoldNo()).isEqualTo(3);
        assertThat(metrics.getCruxDurationMs()).isEqualTo(1500);
        assertThat(metrics.getDangerEventCount()).isEqualTo(2);
        assertThat(metrics.getLoadFocusLabel()).isEqualTo("오른다리");
    }

    @Test
    @DisplayName("시도 상세 조회 시 raw timeline과 heart rate series를 함께 반환한다")
    void getAttemptDetail_returnsTimelineAndHeartRateSeries() {
        String username = "testuser";
        Long challengeId = 1L;
        Long attemptId = 20L;

        Attempt attempt = createDoneAttempt(username, challengeId, attemptId);
        AttemptMetrics metrics = AttemptMetrics.builder()
                .attempt(attempt)
                .centerStabilityRatio(0.61)
                .build();

        when(attemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));
        when(attemptVideoService.getVideoUrlForAttempt(attemptId)).thenReturn("https://example.com/video.mp4");
        when(attemptMetricsRepository.findByAttemptId(attemptId)).thenReturn(Optional.of(metrics));
        when(attemptFeedbackRepository.findByAttemptId(attemptId)).thenReturn(Optional.empty());
        when(attemptStabilityPointRepository.findByAttemptIdOrderByPointOrderAsc(attemptId)).thenReturn(List.of(
                AttemptStabilityPoint.builder().attempt(attempt).pointOrder(0).timestampMs(0L).stabilityScore(0.51).build(),
                AttemptStabilityPoint.builder().attempt(attempt).pointOrder(1).timestampMs(1000L).stabilityScore(0.64).build()));
        when(attemptHeartRateSampleRepository.findByAttemptIdOrderBySampleOrderAsc(attemptId)).thenReturn(List.of(
                AttemptHeartRateSample.builder().attempt(attempt).sampleOrder(0).timestampMs(0L).bpm(118).build(),
                AttemptHeartRateSample.builder().attempt(attempt).sampleOrder(1).timestampMs(1000L).bpm(126).build()));

        AttemptFullResponse response = attemptService.getAttemptDetail(username, challengeId, attemptId);

        assertThat(response.videoUrl()).isEqualTo("https://example.com/video.mp4");
        assertThat(response.videoDurationMs()).isEqualTo(attempt.getDurationMs());
        assertThat(response.stabilityTimeline()).hasSize(2);
        assertThat(response.stabilityTimeline().get(1).timestampMs()).isEqualTo(1000L);
        assertThat(response.heartRateSeries()).hasSize(2);
        assertThat(response.heartRateSeries().get(1).bpm()).isEqualTo(126);
    }

    private Attempt createProcessingAttempt(String username, Long challengeId, Long attemptId) {
        User user = User.builder().username(username).build();
        ReflectionTestUtils.setField(user, "id", 1L);

        Challenge challenge = Challenge.builder()
                .user(user)
                .gymNameSnapshot("테스트 암장")
                .problemColorSnapshot("RED")
                .gradeLabelSnapshot("V3")
                .sortOrderSnapshot(1)
                .challengeStatus(ChallengeStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(challenge, "id", challengeId);

        Attempt attempt = Attempt.builder()
                .challenge(challenge)
                .attemptNo(1)
                .build();
        ReflectionTestUtils.setField(attempt, "id", attemptId);
        ReflectionTestUtils.setField(attempt, "attemptStatus", AttemptStatus.PROCESSING);
        return attempt;
    }

    private Attempt createDoneAttempt(String username, Long challengeId, Long attemptId) {
        Attempt attempt = createProcessingAttempt(username, challengeId, attemptId);
        attempt.endAttempt(AttemptResult.FAIL, 12340, 6);
        return attempt;
    }
}
