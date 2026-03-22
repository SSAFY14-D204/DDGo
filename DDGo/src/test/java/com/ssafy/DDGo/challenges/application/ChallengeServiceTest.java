package com.ssafy.DDGo.challenges.application;

import com.ssafy.DDGo.challenges.dao.ChallengeRepository;
import com.ssafy.DDGo.challenges.dao.ChallengeSummaryRepository;
import com.ssafy.DDGo.challenges.domain.Challenge;
import com.ssafy.DDGo.challenges.domain.ChallengeResult;
import com.ssafy.DDGo.challenges.domain.ChallengeStatus;
import com.ssafy.DDGo.challenges.domain.ChallengeSummary;
import com.ssafy.DDGo.challenges.dto.request.ChallengeCloseRequest;
import com.ssafy.DDGo.challenges.dto.response.ChallengeCloseResponse;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChallengeServiceTest {

    @Mock
    private ChallengeRepository challengeRepository;

    @Mock
    private ChallengeSummaryRepository challengeSummaryRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ChallengeService challengeService;

    @Test
    @DisplayName("챌린지 종료 - summary 데이터가 포함된 경우 신규 생성 (Upsert - Insert)")
    void closeChallenge_withSummary_insert() {
        // given: 준비 단계
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
        // 기존 summary 가 없는 상태
        when(challengeSummaryRepository.findByChallengeId(challengeId)).thenReturn(Optional.empty());
        
        // 프론트 데이터 유무와 상관없이 기본값 세팅을 위해 집계 쿼리가 실행되므로 Mock 추가
        when(challengeSummaryRepository.aggregateMetrics(challengeId))
                .thenReturn(java.util.Collections.emptyList());
        when(challengeSummaryRepository.findCruxHoldNos(eq(challengeId), any(Pageable.class)))
                .thenReturn(java.util.Collections.emptyList());

        // 프론트엔드에서 받은 요청 DTO 생성
        ChallengeCloseRequest request = new ChallengeCloseRequest();
        ReflectionTestUtils.setField(request, "challengeResult", ChallengeResult.SUCCESS);

        ChallengeCloseRequest.ChallengeCloseSummaryRequest summaryReq = new ChallengeCloseRequest.ChallengeCloseSummaryRequest();
        ReflectionTestUtils.setField(summaryReq, "averageCenterStabilityRatio", 0.72);
        ReflectionTestUtils.setField(summaryReq, "mostCruxHoldNo", 7);
        ReflectionTestUtils.setField(summaryReq, "maxCruxDurationMs", 2860);
        ReflectionTestUtils.setField(summaryReq, "finalComment", "총 4번 시도 중 1번 완등에 성공했고...");
        ReflectionTestUtils.setField(request, "summary", summaryReq);

        // when: 실행 단계
        ChallengeCloseResponse response = challengeService.closeChallenge(username, challengeId, request);

        // then: 검증 단계
        assertThat(challenge.getChallengeStatus()).isEqualTo(ChallengeStatus.CLOSED);
        assertThat(challenge.getChallengeResult()).isEqualTo(ChallengeResult.SUCCESS);

        // 저장된 (insert된) summary의 값 검증
        ArgumentCaptor<ChallengeSummary> summaryCaptor = ArgumentCaptor.forClass(ChallengeSummary.class);
        verify(challengeSummaryRepository).save(summaryCaptor.capture());

        ChallengeSummary savedSummary = summaryCaptor.getValue();
        assertThat(savedSummary.getAverageCenterStabilityRatio().doubleValue()).isEqualTo(0.72);
        assertThat(savedSummary.getMostCruxHoldNo()).isEqualTo(7);
        assertThat(savedSummary.getMaxCruxDurationMs()).isEqualTo(2860);
        assertThat(savedSummary.getFinalComment()).isEqualTo("총 4번 시도 중 1번 완등에 성공했고...");
    }

    @Test
    @DisplayName("챌린지 종료 (멱등성) - 이미 종료된 경우에도 예외가 발생하지 않고 summary 부분 업데이트가 이루어진다")
    void closeChallenge_alreadyClosed_partialUpdate() {
        // given
        String username = "testuser";
        Long challengeId = 1L;

        User user = User.builder().username(username).build();
        ReflectionTestUtils.setField(user, "id", 1L);

        // 이미 종료된 상태의 챌린지
        Challenge challenge = Challenge.builder()
                .user(user)
                .challengeStatus(ChallengeStatus.CLOSED)
                .build();
        // 종료 시간, 결과 등을 임의로 세팅해 둠
        ReflectionTestUtils.setField(challenge, "id", challengeId);
        ReflectionTestUtils.setField(challenge, "challengeResult", ChallengeResult.SUCCESS);

        // 기존 요약 데이터
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

        // 부분 업데이트 요청 (코멘트 내용과 비율만 수정하고, 나머지는 생략)
        ChallengeCloseRequest request = new ChallengeCloseRequest();
        ChallengeCloseRequest.ChallengeCloseSummaryRequest summaryReq = new ChallengeCloseRequest.ChallengeCloseSummaryRequest();
        ReflectionTestUtils.setField(summaryReq, "averageCenterStabilityRatio", 0.90);
        ReflectionTestUtils.setField(summaryReq, "finalComment", "업데이트된 코멘트");
        ReflectionTestUtils.setField(request, "summary", summaryReq);

        // when (예외 발생 안 함을 검증)
        challengeService.closeChallenge(username, challengeId, request);

        // then
        ArgumentCaptor<ChallengeSummary> summaryCaptor = ArgumentCaptor.forClass(ChallengeSummary.class);
        verify(challengeSummaryRepository).save(summaryCaptor.capture());

        ChallengeSummary updatedSummary = summaryCaptor.getValue();
        // 기존 상태 유지 검증
        assertThat(challenge.getChallengeStatus()).isEqualTo(ChallengeStatus.CLOSED);

        // 부분 업데이트된 값 검증
        assertThat(updatedSummary.getAverageCenterStabilityRatio().doubleValue()).isEqualTo(0.90);
        assertThat(updatedSummary.getFinalComment()).isEqualTo("업데이트된 코멘트");

        // 요청에 포함하지 않은 기존 값은 그대로 유지됨을 검증
        assertThat(updatedSummary.getMostCruxHoldNo()).isEqualTo(3);
        assertThat(updatedSummary.getMaxCruxDurationMs()).isEqualTo(1000);
    }

    @Test
    @DisplayName("챌린지 종료 - summary 데이터가 없는 경우 기존 로직(집계) 동작 확인")
    void closeChallenge_withoutSummary() {
        // given
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

        // 기존처럼 attempt_metrics 집계 모킹
        when(challengeSummaryRepository.aggregateMetrics(challengeId))
                .thenReturn(java.util.Collections.singletonList(new Object[]{0.85, 3000}));
        when(challengeSummaryRepository.findCruxHoldNos(eq(challengeId), any(Pageable.class)))
                .thenReturn(List.of(5));

        // 프론트엔드에서 summary 없이 전송
        ChallengeCloseRequest request = new ChallengeCloseRequest();
        ReflectionTestUtils.setField(request, "challengeResult", ChallengeResult.FAIL);

        // when
        challengeService.closeChallenge(username, challengeId, request);

        // then
        ArgumentCaptor<ChallengeSummary> summaryCaptor = ArgumentCaptor.forClass(ChallengeSummary.class);
        verify(challengeSummaryRepository).save(summaryCaptor.capture());

        // DB에서 집계해 온 값을 바탕으로 생성되었는지 확인
        ChallengeSummary savedSummary = summaryCaptor.getValue();
        assertThat(savedSummary.getAverageCenterStabilityRatio().doubleValue()).isEqualTo(0.85);
        assertThat(savedSummary.getMostCruxHoldNo()).isEqualTo(5);
        assertThat(savedSummary.getMaxCruxDurationMs()).isEqualTo(3000);
        assertThat(savedSummary.getFinalComment()).isNull();
    }
}
