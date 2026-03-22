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
import com.ssafy.DDGo.gyms.dao.ClimbingGymGradeRepository;
import com.ssafy.DDGo.gyms.dao.ClimbingGymRepository;
import com.ssafy.DDGo.gyms.domain.ClimbingGym;
import com.ssafy.DDGo.gyms.domain.ClimbingGymGrade;
import com.ssafy.DDGo.attempts.dao.AttemptMetricsRepository;
import com.ssafy.DDGo.challenges.dto.response.ChallengeAttemptDetailResponse;
import com.ssafy.DDGo.challenges.dto.response.ChallengeSummaryResponse;
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
        private final AttemptMetricsRepository attemptMetricsRepository;
        private final UserRepository userRepository;
        private final ClimbingGymRepository gymRepository;
        private final ClimbingGymGradeRepository gymGradeRepository;
        private final ObjectMapper objectMapper;

        @Transactional
        public ChallengeCreateResponse createChallenge(String username, ChallengeCreateRequest request) {
                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

                // 1. Validate Gym
                ClimbingGym gym = gymRepository.findById(request.getGymId())
                                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                                                "존재하지 않는 암장입니다."));
                if (!Boolean.TRUE.equals(gym.getIsActive())) {
                        throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "비활성화된 암장입니다.");
                }

                // 2. Validate GymGrade
                ClimbingGymGrade gymGrade = gymGradeRepository.findById(request.getGymGradeId())
                                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                                                "존재하지 않는 난이도입니다."));
                if (!gymGrade.getGym().getId().equals(gym.getId())) {
                        throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "해당 암장의 난이도가 아닙니다.");
                }
                if (!Boolean.TRUE.equals(gymGrade.getIsEnabled())) {
                        throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "비활성화된 난이도입니다.");
                }

                // 3. Create Challenge with Snapshots
                Challenge challenge = Challenge.builder()
                                .user(user)
                                .gym(gym)
                                .gymGrade(gymGrade)
                                .gymNameSnapshot(gym.getDisplayName())
                                .problemColorSnapshot(gymGrade.getColorName())
                                .gradeLabelSnapshot(gymGrade.getGradeLabel())
                                .sortOrderSnapshot(gymGrade.getSortOrder())
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

                Challenge challenge = challengeRepository.findById(challengeId)
                                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_FOUND,
                                                "챌린지를 찾을 수 없습니다."));
                if (!challenge.getUser().getId().equals(user.getId())) {
                        throw new CustomException(ErrorCode.CHALLENGE_ACCESS_DENIED, "해당 챌린지에 대한 권한이 없습니다.");
                }

                if (challenge.getChallengeStatus() == ChallengeStatus.CLOSED) {
                        // 이미 종료된 챌린지인 경우 예외를 던지지 않고 그대로 진행하여 Idempotent(멱등성) 보장
                        // 단, 프론트엔드가 새로운 결과를 명시적으로 보냈다면 부분 업데이트 허용 (옵션)
                        if (request.getChallengeResult() != null && challenge.getChallengeResult() == ChallengeResult.UNKNOWN) {
                                // 기존 상태가 UNKNOWN이었고, 새롭게 명확한 결과가 들어왔다면 반영 (선택적)
                                // challenge.close(request.getChallengeResult()); 
                                // (이 부분은 기획에 맞춰 오픈해둘 수 있지만, 안전을 위해 막아두거나 그냥 진행)
                        }
                } else {
                        // challengeResult 미입력 시 UNKNOWN으로 처리
                        ChallengeResult result = (request.getChallengeResult() != null)
                                        ? request.getChallengeResult()
                                        : ChallengeResult.UNKNOWN;
                        challenge.close(result);
                }

                // 기존 challenge_summaries 데이터 존부 확인
                java.util.Optional<ChallengeSummary> existingSummaryOpt = challengeSummaryRepository.findByChallengeId(challengeId);
                ChallengeSummary summary;

                if (existingSummaryOpt.isPresent()) {
                        // 1. 기존 데이터가 있는 경우 (Update)
                        summary = existingSummaryOpt.get();
                        
                        // 기존 값 백업
                        BigDecimal updatedRatio = summary.getAverageCenterStabilityRatio();
                        Integer updatedCruxHoldNo = summary.getMostCruxHoldNo();
                        Integer updatedMaxDurationMs = summary.getMaxCruxDurationMs();
                        String updatedFinalComment = summary.getFinalComment();

                        // 프론트엔드에서 보낸 값 중 null이 아닌 것만 덮어쓰기 (부분 업데이트 지원)
                        if (request.getSummary() != null) {
                                ChallengeCloseRequest.ChallengeCloseSummaryRequest summaryReq = request.getSummary();
                                if (summaryReq.getAverageCenterStabilityRatio() != null) {
                                        updatedRatio = BigDecimal.valueOf(summaryReq.getAverageCenterStabilityRatio());
                                }
                                if (summaryReq.getMostCruxHoldNo() != null) {
                                        updatedCruxHoldNo = summaryReq.getMostCruxHoldNo();
                                }
                                if (summaryReq.getMaxCruxDurationMs() != null) {
                                        updatedMaxDurationMs = summaryReq.getMaxCruxDurationMs();
                                }
                                if (summaryReq.getFinalComment() != null) {
                                        updatedFinalComment = summaryReq.getFinalComment();
                                }
                        }
                        
                        summary.updateSummary(updatedRatio, updatedCruxHoldNo, updatedMaxDurationMs, updatedFinalComment);
                } else {
                        // 2. 기존 데이터가 없는 경우 (Insert)
                        // 1차적으로 DB 통계(attempt_metrics) 기반 집계를 수행하여 Base 데이터 세팅
                        List<Object[]> aggList = challengeSummaryRepository.aggregateMetrics(challengeId);
                        List<Integer> cruxHoldNos = challengeSummaryRepository.findCruxHoldNos(challengeId,
                                        PageRequest.of(0, 1));

                        Object[] agg = aggList.isEmpty() ? new Object[] { null, null } : aggList.get(0);
                        Double avgRatio = agg[0] != null ? ((Number) agg[0]).doubleValue() : null;
                        Integer maxDurationMs = agg[1] != null ? ((Number) agg[1]).intValue() : null;
                        Integer mostCruxHoldNo = cruxHoldNos.isEmpty() ? null : cruxHoldNos.get(0);
                        String finalComment = null;

                        // 2차적으로 프론트엔드에서 넘어온 summary 데이터가 있다면 Null이 아닌 필드들만 오버라이드 (Partial Override)
                        if (request.getSummary() != null) {
                                ChallengeCloseRequest.ChallengeCloseSummaryRequest summaryReq = request.getSummary();
                                if (summaryReq.getAverageCenterStabilityRatio() != null) {
                                        avgRatio = summaryReq.getAverageCenterStabilityRatio();
                                }
                                if (summaryReq.getMaxCruxDurationMs() != null) {
                                        maxDurationMs = summaryReq.getMaxCruxDurationMs();
                                }
                                if (summaryReq.getMostCruxHoldNo() != null) {
                                        mostCruxHoldNo = summaryReq.getMostCruxHoldNo();
                                }
                                if (summaryReq.getFinalComment() != null) {
                                        finalComment = summaryReq.getFinalComment();
                                }
                        }

                        BigDecimal averageCenterStabilityRatio = avgRatio != null ? BigDecimal.valueOf(avgRatio) : null;
                        
                        summary = ChallengeSummary.builder()
                                        .challengeId(challengeId)
                                        .averageCenterStabilityRatio(averageCenterStabilityRatio)
                                        .mostCruxHoldNo(mostCruxHoldNo)
                                        .maxCruxDurationMs(maxDurationMs)
                                        .build();
                        
                        summary.updateSummary(averageCenterStabilityRatio, mostCruxHoldNo, maxDurationMs, finalComment);
                }

                // challenge_summaries 테이블 반영 (insert 또는 update)
                challengeSummaryRepository.save(summary);

                return ChallengeCloseResponse.from(challenge, summary);
        }

        // 홀드 좌표 저장
        @Transactional
        public HoldSaveResponse saveHolds(String username, Long challengeId, HoldSaveRequest request) {
                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

                Challenge challenge = challengeRepository.findById(challengeId)
                                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_FOUND,
                                                "챌린지를 찾을 수 없습니다."));
                if (!challenge.getUser().getId().equals(user.getId())) {
                        throw new CustomException(ErrorCode.CHALLENGE_ACCESS_DENIED, "해당 챌린지에 대한 권한이 없습니다.");
                }

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

                Challenge challenge = challengeRepository.findById(challengeId)
                                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_FOUND,
                                                "챌린지를 찾을 수 없습니다."));
                if (!challenge.getUser().getId().equals(user.getId())) {
                        throw new CustomException(ErrorCode.CHALLENGE_ACCESS_DENIED, "해당 챌린지에 대한 권한이 없습니다.");
                }

                return ChallengeStatusResponse.from(challenge);
        }

        // 챌린지 시도별 상세 분석 조회 (그래프용)
        public List<ChallengeAttemptDetailResponse> getChallengeAttemptDetails(String username, Long challengeId) {
                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

                Challenge challenge = challengeRepository.findById(challengeId)
                                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_FOUND,
                                                "챌린지를 찾을 수 없습니다."));
                if (!challenge.getUser().getId().equals(user.getId())) {
                        throw new CustomException(ErrorCode.CHALLENGE_ACCESS_DENIED, "해당 챌린지에 대한 권한이 없습니다.");
                }

                return attemptMetricsRepository.findByChallengeIdOrderByAttemptNo(challengeId)
                                .stream()
                                .map(ChallengeAttemptDetailResponse::from)
                                .collect(Collectors.toList());
        }

        // 챌린지 종합 분석 조회
        public ChallengeSummaryResponse getChallengeSummary(String username, Long challengeId) {
                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

                Challenge challenge = challengeRepository.findById(challengeId)
                                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_FOUND,
                                                "챌린지를 찾을 수 없습니다."));
                if (!challenge.getUser().getId().equals(user.getId())) {
                        throw new CustomException(ErrorCode.CHALLENGE_ACCESS_DENIED, "해당 챌린지에 대한 권한이 없습니다.");
                }

                ChallengeSummary summary = challengeSummaryRepository.findByChallengeId(challengeId)
                                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_SUMMARY_NOT_FOUND,
                                                "종합 분석 결과가 없습니다. 챌린지를 먼저 종료해 주세요."));

                return ChallengeSummaryResponse.from(summary);
        }
}
