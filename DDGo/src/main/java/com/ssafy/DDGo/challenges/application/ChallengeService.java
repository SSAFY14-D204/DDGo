package com.ssafy.DDGo.challenges.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.DDGo.attempts.application.AttemptVideoService;
import com.ssafy.DDGo.attempts.dao.AttemptFeedbackRepository;
import com.ssafy.DDGo.attempts.dao.AttemptMetricsRepository;
import com.ssafy.DDGo.attempts.dao.AttemptRepository;
import com.ssafy.DDGo.attempts.dao.ChallengeDoneAttemptCountProjection;
import com.ssafy.DDGo.attempts.domain.Attempt;
import com.ssafy.DDGo.attempts.domain.AttemptFeedback;
import com.ssafy.DDGo.attempts.domain.AttemptMetrics;
import com.ssafy.DDGo.challenges.dao.ChallengeRepository;
import com.ssafy.DDGo.challenges.dao.ChallengeSummaryRepository;
import com.ssafy.DDGo.challenges.domain.Challenge;
import com.ssafy.DDGo.challenges.domain.ChallengeResult;
import com.ssafy.DDGo.challenges.domain.ChallengeStatus;
import com.ssafy.DDGo.challenges.domain.ChallengeSummary;
import com.ssafy.DDGo.challenges.dto.request.ChallengeCloseRequest;
import com.ssafy.DDGo.challenges.dto.request.ChallengeCreateRequest;
import com.ssafy.DDGo.challenges.dto.request.HoldSaveRequest;
import com.ssafy.DDGo.challenges.dto.response.ChallengeAttemptDetailResponse;
import com.ssafy.DDGo.challenges.dto.response.ChallengeCloseResponse;
import com.ssafy.DDGo.challenges.dto.response.ChallengeCreateResponse;
import com.ssafy.DDGo.challenges.dto.response.ChallengeListResponse;
import com.ssafy.DDGo.challenges.dto.response.ChallengeStatusResponse;
import com.ssafy.DDGo.challenges.dto.response.ChallengeSummaryResponse;
import com.ssafy.DDGo.challenges.dto.response.HoldSaveResponse;
import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import com.ssafy.DDGo.gyms.dao.ClimbingGymGradeRepository;
import com.ssafy.DDGo.gyms.dao.ClimbingGymRepository;
import com.ssafy.DDGo.gyms.domain.ClimbingGym;
import com.ssafy.DDGo.gyms.domain.ClimbingGymGrade;
import com.ssafy.DDGo.users.dao.UserRepository;
import com.ssafy.DDGo.users.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChallengeService {

    private final ChallengeRepository challengeRepository;
    private final ChallengeSummaryRepository challengeSummaryRepository;
    private final AttemptRepository attemptRepository;
    private final AttemptMetricsRepository attemptMetricsRepository;
    private final AttemptFeedbackRepository attemptFeedbackRepository;
    private final AttemptVideoService attemptVideoService;
    private final UserRepository userRepository;
    private final ClimbingGymRepository gymRepository;
    private final ClimbingGymGradeRepository gymGradeRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ChallengeCreateResponse createChallenge(String username, ChallengeCreateRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        ClimbingGym gym = gymRepository.findById(request.getGymId())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                        "유효하지 않은 클라이밍장입니다."));
        if (!Boolean.TRUE.equals(gym.getIsActive())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "비활성화된 클라이밍장입니다.");
        }

        ClimbingGymGrade gymGrade = gymGradeRepository.findById(request.getGymGradeId())
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                        "유효하지 않은 문제 난이도입니다."));
        if (!gymGrade.getGym().getId().equals(gym.getId())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "해당 클라이밍장의 문제가 아닙니다.");
        }
        if (!Boolean.TRUE.equals(gymGrade.getIsEnabled())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "비활성화된 문제 난이도입니다.");
        }

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

        challengeRepository.save(challenge);
        return ChallengeCreateResponse.from(challenge);
    }

    public List<ChallengeListResponse> getChallenges(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        List<Challenge> challenges = challengeRepository.findAllByUserOrderByCreatedAtDesc(user);
        if (challenges.isEmpty()) {
            return List.of();
        }

        List<Long> challengeIds = challenges.stream()
                .map(Challenge::getId)
                .collect(Collectors.toList());
        Map<Long, Integer> doneAttemptCountByChallengeId = attemptRepository.countDoneAttemptsByChallengeIds(
                        challengeIds).stream()
                .collect(Collectors.toMap(
                        ChallengeDoneAttemptCountProjection::getChallengeId,
                        projection -> Math.toIntExact(
                                projection.getDoneAttemptCount() != null
                                        ? projection.getDoneAttemptCount()
                                        : 0L)));

        return challenges.stream()
                .map(challenge -> ChallengeListResponse.from(challenge,
                        doneAttemptCountByChallengeId.getOrDefault(challenge.getId(), 0)))
                .collect(Collectors.toList());
    }

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

        if (challenge.getChallengeStatus() != ChallengeStatus.CLOSED) {
            ChallengeResult result = request.getChallengeResult() != null
                    ? request.getChallengeResult()
                    : ChallengeResult.UNKNOWN;
            challenge.close(result);
        }

        Optional<ChallengeSummary> existingSummaryOpt = challengeSummaryRepository.findByChallengeId(challengeId);
        ChallengeSummary summary;

        if (existingSummaryOpt.isPresent()) {
            summary = existingSummaryOpt.get();

            BigDecimal updatedRatio = summary.getAverageCenterStabilityRatio();
            Integer updatedCruxHoldNo = summary.getMostCruxHoldNo();
            Integer updatedMaxDurationMs = summary.getMaxCruxDurationMs();
            String updatedFinalComment = summary.getFinalComment();

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
            List<Object[]> aggList = challengeSummaryRepository.aggregateMetrics(challengeId);
            List<Integer> cruxHoldNos = challengeSummaryRepository.findCruxHoldNos(challengeId,
                    PageRequest.of(0, 1));

            Object[] agg = aggList.isEmpty() ? new Object[]{null, null} : aggList.get(0);
            Double avgRatio = agg[0] != null ? ((Number) agg[0]).doubleValue() : null;
            Integer maxDurationMs = agg[1] != null ? ((Number) agg[1]).intValue() : null;
            Integer mostCruxHoldNo = cruxHoldNos.isEmpty() ? null : cruxHoldNos.get(0);
            String finalComment = null;

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

        challengeSummaryRepository.save(summary);
        return ChallengeCloseResponse.from(challenge, buildChallengeSummaryResponse(challengeId, summary));
    }

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
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "홀드 정보를 저장할 수 없습니다.");
        }

        return HoldSaveResponse.builder()
                .challengeId(challenge.getId())
                .holdCount(request.getHolds().size())
                .holds(request.getHolds())
                .build();
    }

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

    public List<ChallengeAttemptDetailResponse> getChallengeAttemptDetails(String username, Long challengeId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_FOUND,
                        "챌린지를 찾을 수 없습니다."));
        if (!challenge.getUser().getId().equals(user.getId())) {
            throw new CustomException(ErrorCode.CHALLENGE_ACCESS_DENIED, "해당 챌린지에 대한 권한이 없습니다.");
        }

        List<Attempt> attempts = attemptRepository.findByChallengeIdOrderByAttemptNoAsc(challengeId);
        Map<Long, AttemptMetrics> metricsByAttemptId = attemptMetricsRepository.findByChallengeIdOrderByAttemptNo(challengeId)
                .stream()
                .collect(Collectors.toMap(metrics -> metrics.getAttempt().getId(), metrics -> metrics));
        Map<Long, AttemptFeedback> feedbackByAttemptId = attemptFeedbackRepository.findByChallengeId(challengeId)
                .stream()
                .collect(Collectors.toMap(feedback -> feedback.getAttempt().getId(), feedback -> feedback));

        return attempts.stream()
                .map(attempt -> ChallengeAttemptDetailResponse.from(
                        attempt,
                        metricsByAttemptId.get(attempt.getId()),
                        feedbackByAttemptId.get(attempt.getId()),
                        attemptVideoService.getVideoUrlForAttempt(attempt.getId())))
                .collect(Collectors.toList());
    }

    public ChallengeSummaryResponse getChallengeSummary(String username, Long challengeId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));

        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_FOUND,
                        "챌린지를 찾을 수 없습니다."));
        if (!challenge.getUser().getId().equals(user.getId())) {
            throw new CustomException(ErrorCode.CHALLENGE_ACCESS_DENIED, "해당 챌린지에 대한 권한이 없습니다.");
        }

        return buildChallengeSummaryResponse(challengeId, null);
    }

    private ChallengeSummaryResponse buildChallengeSummaryResponse(Long challengeId, ChallengeSummary preloadedSummary) {
        List<Attempt> attempts = attemptRepository.findByChallengeIdOrderByAttemptNoAsc(challengeId);
        List<AttemptMetrics> metricsList = attemptMetricsRepository.findByChallengeIdOrderByAttemptNo(challengeId);
        Optional<ChallengeSummary> storedSummary = Optional.ofNullable(preloadedSummary)
                .or(() -> challengeSummaryRepository.findByChallengeId(challengeId));

        Double computedAverageCenterStabilityRatio = averageDouble(
                metricsList.stream()
                        .map(AttemptMetrics::getCenterStabilityRatio)
                        .filter(Objects::nonNull)
                        .toList());
        Integer computedMostCruxHoldNo = mostFrequent(
                metricsList.stream()
                        .map(AttemptMetrics::getCruxHoldNo)
                        .filter(Objects::nonNull)
                        .toList());
        Integer computedMaxCruxDurationMs = maxInteger(
                metricsList.stream()
                        .map(AttemptMetrics::getCruxDurationMs)
                        .filter(Objects::nonNull)
                        .toList());

        return ChallengeSummaryResponse.builder()
                .attemptCount(attempts.size())
                .averageCenterStabilityRatio(storedSummary
                        .map(ChallengeSummary::getAverageCenterStabilityRatio)
                        .map(BigDecimal::doubleValue)
                        .orElse(computedAverageCenterStabilityRatio))
                .averageStabilityRecoveryScore(averageInteger(
                        metricsList.stream()
                                .map(AttemptMetrics::getStabilityRecoveryScore)
                                .filter(Objects::nonNull)
                                .toList()))
                .averageStableContactRatio(averageDouble(
                        metricsList.stream()
                                .map(AttemptMetrics::getStableContactRatio)
                                .filter(Objects::nonNull)
                                .toList()))
                .averageLowerBodyDriveScore(averageInteger(
                        metricsList.stream()
                                .map(AttemptMetrics::getLowerBodyDriveScore)
                                .filter(Objects::nonNull)
                                .toList()))
                .averageOverallMovementScore(averageInteger(
                        metricsList.stream()
                                .map(AttemptMetrics::getOverallMovementScore)
                                .filter(Objects::nonNull)
                                .toList()))
                .mostCruxHoldNo(storedSummary.map(ChallengeSummary::getMostCruxHoldNo)
                        .orElse(computedMostCruxHoldNo))
                .maxCruxDurationMs(storedSummary.map(ChallengeSummary::getMaxCruxDurationMs)
                        .orElse(computedMaxCruxDurationMs))
                .repeatedLoadFocusLabel(mostFrequent(
                        metricsList.stream()
                                .map(AttemptMetrics::getLoadFocusLabel)
                                .filter(this::hasText)
                                .toList()))
                .finalComment(storedSummary.map(ChallengeSummary::getFinalComment).orElse(null))
                .build();
    }

    private Double averageDouble(List<Double> values) {
        if (values.isEmpty()) {
            return null;
        }
        return values.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElseThrow();
    }

    private Double averageInteger(List<Integer> values) {
        if (values.isEmpty()) {
            return null;
        }
        return values.stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElseThrow();
    }

    private Integer maxInteger(List<Integer> values) {
        return values.stream()
                .max(Integer::compareTo)
                .orElse(null);
    }

    private <T> T mostFrequent(List<T> values) {
        if (values.isEmpty()) {
            return null;
        }

        Map<T, Integer> countByValue = new LinkedHashMap<>();
        for (T value : values) {
            countByValue.merge(value, 1, Integer::sum);
        }

        T bestValue = null;
        int bestCount = -1;
        for (Map.Entry<T, Integer> entry : countByValue.entrySet()) {
            if (entry.getValue() > bestCount) {
                bestValue = entry.getKey();
                bestCount = entry.getValue();
            }
        }

        return bestValue;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
