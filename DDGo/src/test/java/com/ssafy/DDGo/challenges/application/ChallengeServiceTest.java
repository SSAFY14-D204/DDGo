package com.ssafy.DDGo.challenges.application;

import com.ssafy.DDGo.attempts.application.AttemptVideoService;
import com.ssafy.DDGo.attempts.dao.AttemptFeedbackRepository;
import com.ssafy.DDGo.attempts.dao.AttemptMetricsRepository;
import com.ssafy.DDGo.attempts.dao.AttemptRepository;
import com.ssafy.DDGo.attempts.domain.Attempt;
import com.ssafy.DDGo.attempts.domain.AttemptFeedback;
import com.ssafy.DDGo.attempts.domain.AttemptMetrics;
import com.ssafy.DDGo.attempts.domain.AttemptResult;
import com.ssafy.DDGo.challenges.dao.ChallengeRepository;
import com.ssafy.DDGo.challenges.dao.ChallengeSummaryRepository;
import com.ssafy.DDGo.challenges.domain.Challenge;
import com.ssafy.DDGo.challenges.domain.ChallengeResult;
import com.ssafy.DDGo.challenges.domain.ChallengeStatus;
import com.ssafy.DDGo.challenges.domain.ChallengeSummary;
import com.ssafy.DDGo.challenges.dto.request.ChallengeCloseRequest;
import com.ssafy.DDGo.challenges.dto.response.ChallengeAttemptDetailResponse;
import com.ssafy.DDGo.challenges.dto.response.ChallengeCloseResponse;
import com.ssafy.DDGo.challenges.dto.response.ChallengeSummaryResponse;
import com.ssafy.DDGo.users.dao.UserRepository;
import com.ssafy.DDGo.users.domain.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChallengeServiceTest {

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private ChallengeSummaryRepository challengeSummaryRepository;

    @Mock
    private AttemptRepository attemptRepository;

    @Mock
    private AttemptMetricsRepository attemptMetricsRepository;

    @Mock
    private AttemptFeedbackRepository attemptFeedbackRepository;

    @Mock
    private AttemptVideoService attemptVideoService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ChallengeService challengeService;

    @Test
    @DisplayName("챌린지 종료 시 summary 요청값으로 요약을 저장한다")
    void closeChallenge_withSummary_insert() {
        String username = "testuser";
        Long challengeId = 1L;

        User user = User.builder().username(username).build();
        ReflectionTestUtils.setField(user, "id", 1L);

        Challenge challenge = Challenge.builder()
                .user(user)
                .challengeStatus(ChallengeStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(challenge, "id", challengeId);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(challengeSummaryRepository.findByChallengeId(challengeId)).thenReturn(Optional.empty());
        when(challengeSummaryRepository.aggregateMetrics(challengeId)).thenReturn(Collections.emptyList());
        when(challengeSummaryRepository.findCruxHoldNos(eq(challengeId), any(Pageable.class)))
                .thenReturn(Collections.emptyList());
        when(attemptRepository.findByChallengeIdOrderByAttemptNoAsc(challengeId)).thenReturn(Collections.emptyList());
        when(attemptMetricsRepository.findByChallengeIdOrderByAttemptNo(challengeId)).thenReturn(Collections.emptyList());

        ChallengeCloseRequest request = new ChallengeCloseRequest();
        ReflectionTestUtils.setField(request, "challengeResult", ChallengeResult.SUCCESS);

        ChallengeCloseRequest.ChallengeCloseSummaryRequest summaryReq =
                new ChallengeCloseRequest.ChallengeCloseSummaryRequest();
        ReflectionTestUtils.setField(summaryReq, "averageCenterStabilityRatio", 0.72);
        ReflectionTestUtils.setField(summaryReq, "mostCruxHoldNo", 7);
        ReflectionTestUtils.setField(summaryReq, "maxCruxDurationMs", 2860);
        ReflectionTestUtils.setField(summaryReq, "finalComment", "전체적으로 안정성이 좋아졌습니다.");
        ReflectionTestUtils.setField(request, "summary", summaryReq);

        ChallengeCloseResponse response = challengeService.closeChallenge(username, challengeId, request);

        assertThat(response.getChallengeId()).isEqualTo(challengeId);
        assertThat(challenge.getChallengeStatus()).isEqualTo(ChallengeStatus.CLOSED);
        assertThat(challenge.getChallengeResult()).isEqualTo(ChallengeResult.SUCCESS);
        assertThat(response.getSummary().getAttemptCount()).isEqualTo(0);
        assertThat(response.getSummary().getAverageStabilityRecoveryScore()).isNull();
        assertThat(response.getSummary().getRepeatedLoadFocusLabel()).isNull();

        ArgumentCaptor<ChallengeSummary> summaryCaptor = ArgumentCaptor.forClass(ChallengeSummary.class);
        verify(challengeSummaryRepository).save(summaryCaptor.capture());

        ChallengeSummary savedSummary = summaryCaptor.getValue();
        assertThat(savedSummary.getAverageCenterStabilityRatio().doubleValue()).isEqualTo(0.72);
        assertThat(savedSummary.getMostCruxHoldNo()).isEqualTo(7);
        assertThat(savedSummary.getMaxCruxDurationMs()).isEqualTo(2860);
        assertThat(savedSummary.getFinalComment()).isEqualTo("전체적으로 안정성이 좋아졌습니다.");
    }

    @Test
    @DisplayName("이미 종료된 챌린지는 전달된 요약 필드만 부분 갱신한다")
    void closeChallenge_alreadyClosed_partialUpdate() {
        String username = "testuser";
        Long challengeId = 1L;

        User user = User.builder().username(username).build();
        ReflectionTestUtils.setField(user, "id", 1L);

        Challenge challenge = Challenge.builder()
                .user(user)
                .challengeStatus(ChallengeStatus.CLOSED)
                .build();
        ReflectionTestUtils.setField(challenge, "id", challengeId);
        ReflectionTestUtils.setField(challenge, "challengeResult", ChallengeResult.SUCCESS);

        ChallengeSummary existingSummary = ChallengeSummary.builder()
                .challengeId(challengeId)
                .averageCenterStabilityRatio(BigDecimal.valueOf(0.50))
                .mostCruxHoldNo(3)
                .maxCruxDurationMs(1000)
                .build();
        existingSummary.updateSummary(BigDecimal.valueOf(0.50), 3, 1000, "기존 코멘트");

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(challengeSummaryRepository.findByChallengeId(challengeId)).thenReturn(Optional.of(existingSummary));
        when(attemptRepository.findByChallengeIdOrderByAttemptNoAsc(challengeId)).thenReturn(Collections.emptyList());
        when(attemptMetricsRepository.findByChallengeIdOrderByAttemptNo(challengeId)).thenReturn(Collections.emptyList());

        ChallengeCloseRequest request = new ChallengeCloseRequest();
        ChallengeCloseRequest.ChallengeCloseSummaryRequest summaryReq =
                new ChallengeCloseRequest.ChallengeCloseSummaryRequest();
        ReflectionTestUtils.setField(summaryReq, "averageCenterStabilityRatio", 0.90);
        ReflectionTestUtils.setField(summaryReq, "finalComment", "업데이트된 코멘트");
        ReflectionTestUtils.setField(request, "summary", summaryReq);

        challengeService.closeChallenge(username, challengeId, request);

        ArgumentCaptor<ChallengeSummary> summaryCaptor = ArgumentCaptor.forClass(ChallengeSummary.class);
        verify(challengeSummaryRepository).save(summaryCaptor.capture());

        ChallengeSummary updatedSummary = summaryCaptor.getValue();
        assertThat(updatedSummary.getAverageCenterStabilityRatio().doubleValue()).isEqualTo(0.90);
        assertThat(updatedSummary.getFinalComment()).isEqualTo("업데이트된 코멘트");
        assertThat(updatedSummary.getMostCruxHoldNo()).isEqualTo(3);
        assertThat(updatedSummary.getMaxCruxDurationMs()).isEqualTo(1000);
    }

    @Test
    @DisplayName("summary 요청값이 없으면 attempt metrics 집계값으로 요약을 저장한다")
    void closeChallenge_withoutSummary_usesAggregatedMetrics() {
        String username = "testuser";
        Long challengeId = 1L;

        User user = User.builder().username(username).build();
        ReflectionTestUtils.setField(user, "id", 1L);

        Challenge challenge = Challenge.builder()
                .user(user)
                .challengeStatus(ChallengeStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(challenge, "id", challengeId);

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(challengeSummaryRepository.findByChallengeId(challengeId)).thenReturn(Optional.empty());
        when(challengeSummaryRepository.aggregateMetrics(challengeId))
                .thenReturn(Collections.singletonList(new Object[]{0.85, 3000}));
        when(challengeSummaryRepository.findCruxHoldNos(eq(challengeId), any(Pageable.class)))
                .thenReturn(List.of(5));

        Attempt firstAttempt = Attempt.builder().challenge(challenge).attemptNo(1).build();
        ReflectionTestUtils.setField(firstAttempt, "id", 21L);
        Attempt secondAttempt = Attempt.builder().challenge(challenge).attemptNo(2).build();
        ReflectionTestUtils.setField(secondAttempt, "id", 22L);

        AttemptMetrics firstMetrics = AttemptMetrics.builder()
                .attempt(firstAttempt)
                .centerStabilityRatio(0.80)
                .stabilityRecoveryScore(70)
                .stableContactRatio(0.55)
                .lowerBodyDriveScore(65)
                .overallMovementScore(75)
                .loadFocusLabel("왼팔")
                .build();
        AttemptMetrics secondMetrics = AttemptMetrics.builder()
                .attempt(secondAttempt)
                .centerStabilityRatio(0.60)
                .stabilityRecoveryScore(50)
                .stableContactRatio(0.45)
                .lowerBodyDriveScore(55)
                .overallMovementScore(65)
                .loadFocusLabel("왼팔")
                .build();

        when(attemptRepository.findByChallengeIdOrderByAttemptNoAsc(challengeId))
                .thenReturn(List.of(firstAttempt, secondAttempt));
        when(attemptMetricsRepository.findByChallengeIdOrderByAttemptNo(challengeId))
                .thenReturn(List.of(firstMetrics, secondMetrics));

        ChallengeCloseRequest request = new ChallengeCloseRequest();
        ReflectionTestUtils.setField(request, "challengeResult", ChallengeResult.FAIL);

        ChallengeCloseResponse response = challengeService.closeChallenge(username, challengeId, request);

        ArgumentCaptor<ChallengeSummary> summaryCaptor = ArgumentCaptor.forClass(ChallengeSummary.class);
        verify(challengeSummaryRepository).save(summaryCaptor.capture());

        ChallengeSummary savedSummary = summaryCaptor.getValue();
        assertThat(savedSummary.getAverageCenterStabilityRatio().doubleValue()).isEqualTo(0.85);
        assertThat(savedSummary.getMostCruxHoldNo()).isEqualTo(5);
        assertThat(savedSummary.getMaxCruxDurationMs()).isEqualTo(3000);
        assertThat(savedSummary.getFinalComment()).isNull();
        assertThat(response.getSummary().getAttemptCount()).isEqualTo(2);
        assertThat(response.getSummary().getAverageStabilityRecoveryScore()).isEqualTo(60.0);
        assertThat(response.getSummary().getAverageStableContactRatio()).isEqualTo(0.5);
        assertThat(response.getSummary().getAverageLowerBodyDriveScore()).isEqualTo(60.0);
        assertThat(response.getSummary().getAverageOverallMovementScore()).isEqualTo(70.0);
        assertThat(response.getSummary().getRepeatedLoadFocusLabel()).isEqualTo("왼팔");
    }

    @Test
    @DisplayName("챌린지 시도 상세는 metrics, feedback, videoUrl을 함께 내려준다")
    void getChallengeAttemptDetails_returnsExpandedFieldsInOrder() {
        String username = "testuser";
        Long challengeId = 1L;

        User user = User.builder().username(username).build();
        ReflectionTestUtils.setField(user, "id", 1L);

        Challenge challenge = Challenge.builder()
                .user(user)
                .challengeStatus(ChallengeStatus.ACTIVE)
                .build();
        ReflectionTestUtils.setField(challenge, "id", challengeId);

        Attempt firstAttempt = Attempt.builder().challenge(challenge).attemptNo(1).build();
        ReflectionTestUtils.setField(firstAttempt, "id", 10L);
        ReflectionTestUtils.setField(firstAttempt, "attemptResult", AttemptResult.FAIL);
        ReflectionTestUtils.setField(firstAttempt, "durationMs", 12345);
        ReflectionTestUtils.setField(firstAttempt, "maxHoldNo", 7);

        Attempt secondAttempt = Attempt.builder().challenge(challenge).attemptNo(2).build();
        ReflectionTestUtils.setField(secondAttempt, "id", 11L);
        ReflectionTestUtils.setField(secondAttempt, "attemptResult", AttemptResult.SUCCESS);
        ReflectionTestUtils.setField(secondAttempt, "durationMs", 11111);
        ReflectionTestUtils.setField(secondAttempt, "maxHoldNo", 8);

        AttemptMetrics metrics = AttemptMetrics.builder()
                .attempt(firstAttempt)
                .centerStabilityRatio(0.82)
                .stabilityRecoveryScore(78)
                .stableContactRatio(0.66)
                .lowerBodyDriveScore(81)
                .overallMovementScore(88)
                .cruxHoldNo(5)
                .cruxDurationMs(2100)
                .dangerEventCount(3)
                .loadFocusLabel("왼팔")
                .build();

        AttemptFeedback feedback = AttemptFeedback.builder()
                .attempt(firstAttempt)
                .failureReason("오른발 밀어주기 전에 상체로 먼저 당겼어요.")
                .riskAlert("크럭스 직전 팔 의존이 커졌습니다.")
                .nextMission("왼발 고정 후 오른발로 먼저 밀어보세요.")
                .build();

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(attemptRepository.findByChallengeIdOrderByAttemptNoAsc(challengeId))
                .thenReturn(List.of(firstAttempt, secondAttempt));
        when(attemptMetricsRepository.findByChallengeIdOrderByAttemptNo(challengeId))
                .thenReturn(List.of(metrics));
        when(attemptFeedbackRepository.findByChallengeId(challengeId))
                .thenReturn(List.of(feedback));
        when(attemptVideoService.getVideoUrlForAttempt(10L)).thenReturn("https://video.example.com/10");
        when(attemptVideoService.getVideoUrlForAttempt(11L)).thenReturn(null);

        List<ChallengeAttemptDetailResponse> responses =
                challengeService.getChallengeAttemptDetails(username, challengeId);

        assertThat(responses).hasSize(2);

        ChallengeAttemptDetailResponse first = responses.get(0);
        assertThat(first.getAttemptId()).isEqualTo(10L);
        assertThat(first.getAttemptNo()).isEqualTo(1);
        assertThat(first.getCenterStabilityRatio()).isEqualTo(0.82);
        assertThat(first.getStabilityRecoveryScore()).isEqualTo(78);
        assertThat(first.getStableContactRatio()).isEqualTo(0.66);
        assertThat(first.getLowerBodyDriveScore()).isEqualTo(81);
        assertThat(first.getOverallMovementScore()).isEqualTo(88);
        assertThat(first.getLoadFocusLabel()).isEqualTo("왼팔");
        assertThat(first.getFailureReason()).isEqualTo("오른발 밀어주기 전에 상체로 먼저 당겼어요.");
        assertThat(first.getRiskAlert()).isEqualTo("크럭스 직전 팔 의존이 커졌습니다.");
        assertThat(first.getNextMission()).isEqualTo("왼발 고정 후 오른발로 먼저 밀어보세요.");
        assertThat(first.getVideoUrl()).isEqualTo("https://video.example.com/10");

        ChallengeAttemptDetailResponse second = responses.get(1);
        assertThat(second.getAttemptId()).isEqualTo(11L);
        assertThat(second.getAttemptNo()).isEqualTo(2);
        assertThat(second.getCenterStabilityRatio()).isNull();
        assertThat(second.getFailureReason()).isNull();
        assertThat(second.getVideoUrl()).isNull();
    }

    @Test
    @DisplayName("챌린지 summary는 새 평균 필드를 계산하고 기존 저장 필드는 유지한다")
    void getChallengeSummary_returnsExpandedFieldsAndPreservesStoredSummary() {
        String username = "testuser";
        Long challengeId = 1L;

        User user = User.builder().username(username).build();
        ReflectionTestUtils.setField(user, "id", 1L);

        Challenge challenge = Challenge.builder()
                .user(user)
                .challengeStatus(ChallengeStatus.CLOSED)
                .build();
        ReflectionTestUtils.setField(challenge, "id", challengeId);

        Attempt firstAttempt = Attempt.builder().challenge(challenge).attemptNo(1).build();
        ReflectionTestUtils.setField(firstAttempt, "id", 10L);
        Attempt secondAttempt = Attempt.builder().challenge(challenge).attemptNo(2).build();
        ReflectionTestUtils.setField(secondAttempt, "id", 11L);

        AttemptMetrics firstMetrics = AttemptMetrics.builder()
                .attempt(firstAttempt)
                .centerStabilityRatio(0.8)
                .stabilityRecoveryScore(70)
                .stableContactRatio(0.6)
                .lowerBodyDriveScore(80)
                .overallMovementScore(90)
                .cruxHoldNo(5)
                .cruxDurationMs(2100)
                .loadFocusLabel("왼팔")
                .build();
        AttemptMetrics secondMetrics = AttemptMetrics.builder()
                .attempt(secondAttempt)
                .centerStabilityRatio(0.6)
                .stabilityRecoveryScore(50)
                .stableContactRatio(0.4)
                .lowerBodyDriveScore(60)
                .overallMovementScore(70)
                .cruxHoldNo(4)
                .cruxDurationMs(1800)
                .loadFocusLabel("왼팔")
                .build();

        ChallengeSummary storedSummary = ChallengeSummary.builder()
                .challengeId(challengeId)
                .averageCenterStabilityRatio(BigDecimal.valueOf(0.91))
                .mostCruxHoldNo(8)
                .maxCruxDurationMs(4000)
                .build();
        storedSummary.updateSummary(BigDecimal.valueOf(0.91), 8, 4000,
                "전체적으로 안정성은 좋아졌지만 크럭스 구간에서 왼팔 부담이 반복되었습니다.");

        when(userRepository.findByUsername(username)).thenReturn(Optional.of(user));
        when(challengeRepository.findById(challengeId)).thenReturn(Optional.of(challenge));
        when(attemptRepository.findByChallengeIdOrderByAttemptNoAsc(challengeId))
                .thenReturn(List.of(firstAttempt, secondAttempt));
        when(attemptMetricsRepository.findByChallengeIdOrderByAttemptNo(challengeId))
                .thenReturn(List.of(firstMetrics, secondMetrics));
        when(challengeSummaryRepository.findByChallengeId(challengeId))
                .thenReturn(Optional.of(storedSummary));

        ChallengeSummaryResponse response = challengeService.getChallengeSummary(username, challengeId);

        assertThat(response.getAttemptCount()).isEqualTo(2);
        assertThat(response.getAverageCenterStabilityRatio()).isEqualTo(0.91);
        assertThat(response.getAverageStabilityRecoveryScore()).isEqualTo(60.0);
        assertThat(response.getAverageStableContactRatio()).isEqualTo(0.5);
        assertThat(response.getAverageLowerBodyDriveScore()).isEqualTo(70.0);
        assertThat(response.getAverageOverallMovementScore()).isEqualTo(80.0);
        assertThat(response.getMostCruxHoldNo()).isEqualTo(8);
        assertThat(response.getMaxCruxDurationMs()).isEqualTo(4000);
        assertThat(response.getRepeatedLoadFocusLabel()).isEqualTo("왼팔");
        assertThat(response.getFinalComment())
                .isEqualTo("전체적으로 안정성은 좋아졌지만 크럭스 구간에서 왼팔 부담이 반복되었습니다.");
    }
}
