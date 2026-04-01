package com.ssafy.DDGo.attempts.application;

import com.ssafy.DDGo.attempts.dao.AttemptFeedbackRepository;
import com.ssafy.DDGo.attempts.dao.AttemptHeartRateSampleRepository;
import com.ssafy.DDGo.attempts.dao.AttemptMetricsRepository;
import com.ssafy.DDGo.attempts.dao.AttemptRepository;
import com.ssafy.DDGo.attempts.dao.AttemptStabilityPointRepository;
import com.ssafy.DDGo.attempts.domain.Attempt;
import com.ssafy.DDGo.attempts.domain.AttemptFeedback;
import com.ssafy.DDGo.attempts.domain.AttemptHeartRateSample;
import com.ssafy.DDGo.attempts.domain.AttemptMetrics;
import com.ssafy.DDGo.attempts.domain.AttemptResult;
import com.ssafy.DDGo.attempts.domain.AttemptStabilityPoint;
import com.ssafy.DDGo.attempts.domain.AttemptStatus;
import com.ssafy.DDGo.attempts.dto.request.AttemptEndRequest;
import com.ssafy.DDGo.attempts.dto.response.AttemptDetailResponse;
import com.ssafy.DDGo.attempts.dto.response.AttemptFullResponse;
import com.ssafy.DDGo.attempts.dto.response.AttemptListResponse;
import com.ssafy.DDGo.attempts.dto.response.AttemptStartResponse;
import com.ssafy.DDGo.challenges.dao.ChallengeAttemptCounterRepository;
import com.ssafy.DDGo.challenges.dao.ChallengeRepository;
import com.ssafy.DDGo.challenges.domain.Challenge;
import com.ssafy.DDGo.challenges.domain.ChallengeStatus;
import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttemptService {

    private final ChallengeRepository challengeRepository;
    private final ChallengeAttemptCounterRepository counterRepository;
    private final AttemptRepository attemptRepository;
    private final AttemptMetricsRepository attemptMetricsRepository;
    private final AttemptFeedbackRepository attemptFeedbackRepository;
    private final AttemptStabilityPointRepository attemptStabilityPointRepository;
    private final AttemptHeartRateSampleRepository attemptHeartRateSampleRepository;
    private final AttemptVideoService attemptVideoService;

    @Transactional
    public AttemptStartResponse startAttempt(String username, Long challengeId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_FOUND, "해당 챌린지를 찾을 수 없습니다."));

        if (!challenge.getUser().getUsername().equals(username)) {
            throw new CustomException(ErrorCode.CHALLENGE_ACCESS_DENIED, "본인 챌린지에서만 시도를 시작할 수 있습니다.");
        }

        if (challenge.getChallengeStatus() == ChallengeStatus.CLOSED) {
            throw new CustomException(ErrorCode.CHALLENGE_ALREADY_CLOSED, "이미 종료된 챌린지에는 새 시도를 시작할 수 없습니다.");
        }

        int updatedRows = counterRepository.incrementAttemptNoIfChallengeActive(challengeId);
        if (updatedRows == 0) {
            Challenge refreshedChallenge = challengeRepository.findById(challengeId)
                    .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_FOUND, "해당 챌린지를 찾을 수 없습니다."));
            if (refreshedChallenge.getChallengeStatus() == ChallengeStatus.CLOSED) {
                throw new CustomException(ErrorCode.CHALLENGE_ALREADY_CLOSED, "이미 종료된 챌린지에는 시도를 시작할 수 없습니다.");
            }
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "시도 번호를 발급하는 중 오류가 발생했습니다.");
        }

        Integer newAttemptNo = counterRepository.findNextAttemptNo(challengeId)
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "시도 번호를 조회할 수 없습니다."));

        Attempt attempt = Attempt.builder()
                .challenge(challenge)
                .attemptNo(newAttemptNo)
                .build();

        attemptRepository.save(attempt);
        return AttemptStartResponse.from(attempt);
    }

    public AttemptListResponse getAttempts(String username, Long challengeId) {
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_FOUND, "해당 챌린지를 찾을 수 없습니다."));

        if (!challenge.getUser().getUsername().equals(username)) {
            throw new CustomException(ErrorCode.CHALLENGE_ACCESS_DENIED, "해당 챌린지의 시도 목록을 조회할 권한이 없습니다.");
        }

        List<Attempt> attempts = attemptRepository.findByChallengeIdOrderByAttemptNoAsc(challengeId);
        List<AttemptDetailResponse> attemptDetails = attempts.stream()
                .map(AttemptDetailResponse::from)
                .collect(Collectors.toList());

        return new AttemptListResponse(challengeId, attemptDetails);
    }

    public AttemptFullResponse getAttemptDetail(String username, Long challengeId, Long attemptId) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new CustomException(ErrorCode.ATTEMPT_NOT_FOUND, "해당 시도를 찾을 수 없습니다. ID: " + attemptId));

        if (!attempt.getChallenge().getId().equals(challengeId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "해당 챌린지에 속한 시도가 아닙니다.");
        }

        if (!attempt.getChallenge().getUser().getUsername().equals(username)) {
            throw new CustomException(ErrorCode.CHALLENGE_ACCESS_DENIED, "해당 시도를 조회할 권한이 없습니다.");
        }

        String videoUrl = attemptVideoService.getVideoUrlForAttempt(attemptId);
        AttemptMetrics metrics = attemptMetricsRepository.findByAttemptId(attemptId).orElse(null);
        AttemptFeedback feedback = attemptFeedbackRepository.findByAttemptId(attemptId).orElse(null);
        List<AttemptStabilityPoint> stabilityTimeline =
                attemptStabilityPointRepository.findByAttemptIdOrderByPointOrderAsc(attemptId);
        List<AttemptHeartRateSample> heartRateSeries =
                attemptHeartRateSampleRepository.findByAttemptIdOrderBySampleOrderAsc(attemptId);

        return AttemptFullResponse.from(attempt, videoUrl, metrics, feedback, stabilityTimeline, heartRateSeries);
    }

    @Transactional
    public AttemptDetailResponse endAttempt(String username, Long challengeId, Long attemptId, AttemptEndRequest request) {
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new CustomException(ErrorCode.ATTEMPT_NOT_FOUND, "해당 시도를 찾을 수 없습니다. ID: " + attemptId));

        if (!attempt.getChallenge().getId().equals(challengeId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "해당 챌린지에 속한 시도가 아닙니다.");
        }

        if (!attempt.getChallenge().getUser().getUsername().equals(username)) {
            throw new CustomException(ErrorCode.CHALLENGE_ACCESS_DENIED, "해당 시도를 종료할 권한이 없습니다.");
        }

        AttemptStatus currentStatus = attempt.getAttemptStatus();
        boolean isAlreadyDone = currentStatus == AttemptStatus.DONE;

        if (!isAlreadyDone && currentStatus != AttemptStatus.PROCESSING) {
            throw new CustomException(
                    ErrorCode.INVALID_ATTEMPT_STATUS,
                    "분석 종료는 PROCESSING 상태이거나 이미 DONE 상태인 시도에만 허용됩니다. 현재 상태: " + currentStatus);
        }

        if (!isAlreadyDone) {
            if (request.baseData() == null || request.baseData().attemptResult() == null) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "시도 종료 요청에는 baseData.attemptResult가 필요합니다.");
            }
            attempt.endAttempt(
                    request.baseData().attemptResult(),
                    request.baseData().durationMs(),
                    request.baseData().maxHoldNo());
            attempt.markAnalysisEnded();
        } else if (request.baseData() != null) {
            AttemptResult newResult = request.baseData().attemptResult() != null
                    ? request.baseData().attemptResult()
                    : attempt.getAttemptResult();
            Integer newDuration = request.baseData().durationMs() != null
                    ? request.baseData().durationMs()
                    : attempt.getDurationMs();
            Integer newMaxHold = request.baseData().maxHoldNo() != null
                    ? request.baseData().maxHoldNo()
                    : attempt.getMaxHoldNo();
            attempt.endAttempt(newResult, newDuration, newMaxHold);
        }

        if (request.metricsData() != null) {
            AttemptMetrics metrics = attemptMetricsRepository.findByAttemptId(attemptId).orElse(null);
            if (metrics != null) {
                metrics.updateMetrics(
                        request.metricsData().centerStabilityRatio(),
                        request.metricsData().stabilityRecoveryScore(),
                        request.metricsData().stableContactRatio(),
                        request.metricsData().lowerBodyDriveScore(),
                        request.metricsData().overallMovementScore(),
                        request.metricsData().cruxHoldNo(),
                        request.metricsData().cruxDurationMs(),
                        request.metricsData().dangerEventCount(),
                        request.metricsData().loadFocusLabel());
            } else {
                metrics = AttemptMetrics.builder()
                        .attempt(attempt)
                        .centerStabilityRatio(request.metricsData().centerStabilityRatio())
                        .stabilityRecoveryScore(request.metricsData().stabilityRecoveryScore())
                        .stableContactRatio(request.metricsData().stableContactRatio())
                        .lowerBodyDriveScore(request.metricsData().lowerBodyDriveScore())
                        .overallMovementScore(request.metricsData().overallMovementScore())
                        .cruxHoldNo(request.metricsData().cruxHoldNo())
                        .cruxDurationMs(request.metricsData().cruxDurationMs())
                        .dangerEventCount(request.metricsData().dangerEventCount())
                        .loadFocusLabel(request.metricsData().loadFocusLabel())
                        .build();
                attemptMetricsRepository.save(metrics);
            }
        }

        if (request.feedbacksData() != null) {
            AttemptFeedback feedback = attemptFeedbackRepository.findByAttemptId(attemptId).orElse(null);
            if (feedback != null) {
                feedback.updateFeedback(
                        request.feedbacksData().failureReason(),
                        request.feedbacksData().riskAlert(),
                        request.feedbacksData().nextMission());
            } else {
                feedback = AttemptFeedback.builder()
                        .attempt(attempt)
                        .failureReason(request.feedbacksData().failureReason())
                        .riskAlert(request.feedbacksData().riskAlert())
                        .nextMission(request.feedbacksData().nextMission())
                        .build();
                attemptFeedbackRepository.save(feedback);
            }
        }

        replaceStabilityTimeline(attempt, request.stabilityTimeline());
        replaceHeartRateSeries(attempt, request.heartRateSeries());

        return AttemptDetailResponse.from(attempt);
    }

    private void replaceStabilityTimeline(Attempt attempt, List<AttemptEndRequest.StabilityTimelinePoint> timeline) {
        if (timeline == null) {
            return;
        }

        attemptStabilityPointRepository.deleteByAttemptId(attempt.getId());

        if (timeline.isEmpty()) {
            return;
        }

        List<AttemptStabilityPoint> points = new ArrayList<>();
        for (int i = 0; i < timeline.size(); i++) {
            AttemptEndRequest.StabilityTimelinePoint point = timeline.get(i);
            points.add(AttemptStabilityPoint.builder()
                    .attempt(attempt)
                    .pointOrder(i)
                    .timestampMs(point.timestampMs())
                    .stabilityScore(point.stabilityScore())
                    .build());
        }
        attemptStabilityPointRepository.saveAll(points);
    }

    private void replaceHeartRateSeries(Attempt attempt, List<AttemptEndRequest.HeartRateSample> series) {
        if (series == null) {
            return;
        }

        attemptHeartRateSampleRepository.deleteByAttemptId(attempt.getId());

        if (series.isEmpty()) {
            return;
        }

        List<AttemptHeartRateSample> samples = new ArrayList<>();
        for (int i = 0; i < series.size(); i++) {
            AttemptEndRequest.HeartRateSample sample = series.get(i);
            samples.add(AttemptHeartRateSample.builder()
                    .attempt(attempt)
                    .sampleOrder(i)
                    .timestampMs(sample.timestampMs())
                    .bpm(sample.bpm())
                    .build());
        }
        attemptHeartRateSampleRepository.saveAll(samples);
    }
}
