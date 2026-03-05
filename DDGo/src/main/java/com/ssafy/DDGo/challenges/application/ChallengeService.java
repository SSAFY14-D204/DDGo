package com.ssafy.DDGo.challenges.application;

import com.ssafy.DDGo.challenges.dao.ChallengeRepository;
import com.ssafy.DDGo.challenges.dao.ChallengeSummaryRepository;
import com.ssafy.DDGo.challenges.domain.Challenge;
import com.ssafy.DDGo.challenges.domain.ChallengeResult;
import com.ssafy.DDGo.challenges.domain.ChallengeStatus;
import com.ssafy.DDGo.challenges.domain.ChallengeSummary;
import com.ssafy.DDGo.challenges.dto.request.ChallengeCloseRequest;
import com.ssafy.DDGo.challenges.dto.request.ChallengeCreateRequest;
import com.ssafy.DDGo.challenges.dto.request.HoldSaveRequest;
import com.ssafy.DDGo.challenges.dto.response.HoldSaveResponse;
import com.ssafy.DDGo.challenges.dto.response.ChallengeCloseResponse;
import com.ssafy.DDGo.challenges.dto.response.ChallengeCreateResponse;
import com.ssafy.DDGo.challenges.dto.response.ChallengeListResponse;
import com.ssafy.DDGo.challenges.dto.response.ChallengeStatusResponse;
import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import com.ssafy.DDGo.users.dao.UserRepository;
import com.ssafy.DDGo.users.domain.User;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChallengeService {

    private final ChallengeRepository challengeRepository;
    private final ChallengeSummaryRepository challengeSummaryRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ChallengeCreateResponse createChallenge(String username, ChallengeCreateRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Challenge challenge = Challenge.builder()
                .user(user)
                .gymName(request.getGymName())
                .problemColor(request.getProblemColor())
                .gradeLabel(request.getGradeLabel())
                .challengeStatus(ChallengeStatus.ACTIVE)
                .startedAt(request.getStartedAt())
                .build();

        // DB INSERT → 트리거(trg_challenges_init_attempt_counter)가 자동으로
        // challenge_attempt_counters row를 생성
        challengeRepository.save(challenge);

        return ChallengeCreateResponse.from(challenge);
    }

    // 내 챌린지 목록 조회 (최신순)
    public List<ChallengeListResponse> getChallenges(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        return challengeRepository.findAllByUserOrderByCreatedAtDesc(user)
                .stream()
                .map(ChallengeListResponse::from)
                .collect(Collectors.toList());
    }

    // 챌린지 종료
    @Transactional
    public ChallengeCloseResponse closeChallenge(String username, Long challengeId, ChallengeCloseRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Challenge challenge = challengeRepository.findByIdAndUser(challengeId, user)
                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_FOUND, "챌린지를 찾을 수 없습니다."));

        if (challenge.getChallengeStatus() == ChallengeStatus.CLOSED) {
            throw new CustomException(ErrorCode.CHALLENGE_ALREADY_CLOSED, "이미 종료된 챌린지입니다.");
        }

        // challengeResult 미입력 시 UNKNOWN으로 처리
        ChallengeResult result = (request.getChallengeResult() != null)
                ? request.getChallengeResult()
                : ChallengeResult.UNKNOWN;

        challenge.close(result);

        // attempt_metrics 집계 → challenge_summaries 생성
        List<Object[]> aggList = challengeSummaryRepository.aggregateMetrics(challengeId);
        List<Integer> cruxHoldNos = challengeSummaryRepository.findCruxHoldNos(challengeId, PageRequest.of(0, 1));

        Object[] agg = aggList.isEmpty() ? new Object[]{null, null} : aggList.get(0);
        Double avgRatio = agg[0] != null ? ((Number) agg[0]).doubleValue() : null;
        Integer maxDurationMs = agg[1] != null ? ((Number) agg[1]).intValue() : null;
        Integer mostCruxHoldNo = cruxHoldNos.isEmpty() ? null : cruxHoldNos.get(0);

        ChallengeSummary summary = ChallengeSummary.builder()
                .challengeId(challengeId)
                .averageCenterStabilityRatio(avgRatio != null ? BigDecimal.valueOf(avgRatio) : null)
                .mostCruxHoldNo(mostCruxHoldNo)
                .maxCruxDurationMs(maxDurationMs)
                .build();

        challengeSummaryRepository.save(summary);

        return ChallengeCloseResponse.from(challenge, summary);
    }

    // 홀드 좌표 저장
    @Transactional
    public HoldSaveResponse saveHolds(String username, Long challengeId, HoldSaveRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Challenge challenge = challengeRepository.findByIdAndUser(challengeId, user)
                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_FOUND, "챌린지를 찾을 수 없습니다."));

        try {
            String holdsJsonStr = objectMapper.writeValueAsString(request.getHolds());
            challenge.updateHoldsJson(holdsJsonStr);
        } catch (JsonProcessingException e) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "홀드 데이터 직렬화에 실패했습니다.");
        }

        return HoldSaveResponse.builder()
                .challengeId(challenge.getId())
                .holdCount(request.getHolds().size())
                .holds(request.getHolds())
                .build();
    }

    // 챌린지 상태 조회
    public ChallengeStatusResponse getChallengeStatus(String username, Long challengeId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Challenge challenge = challengeRepository.findByIdAndUser(challengeId, user)
                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_FOUND, "챌린지를 찾을 수 없습니다."));

        return ChallengeStatusResponse.from(challenge);
    }
}
