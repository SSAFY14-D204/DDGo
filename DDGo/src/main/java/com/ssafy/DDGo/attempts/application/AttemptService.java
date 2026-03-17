package com.ssafy.DDGo.attempts.application;

import com.ssafy.DDGo.attempts.dao.AttemptFeedbackRepository;
import com.ssafy.DDGo.attempts.dao.AttemptMetricsRepository;
import com.ssafy.DDGo.attempts.dao.AttemptRepository;
import com.ssafy.DDGo.attempts.domain.Attempt;
import com.ssafy.DDGo.attempts.domain.AttemptFeedback;
import com.ssafy.DDGo.attempts.domain.AttemptMetrics;
import com.ssafy.DDGo.attempts.dto.request.AttemptEndRequest;
import com.ssafy.DDGo.attempts.dto.response.AttemptDetailResponse;
import com.ssafy.DDGo.attempts.dto.response.AttemptFullResponse;
import com.ssafy.DDGo.attempts.dto.response.AttemptListResponse;
import com.ssafy.DDGo.attempts.dto.response.AttemptStartResponse;
import com.ssafy.DDGo.challenges.dao.ChallengeAttemptCounterRepository;
import com.ssafy.DDGo.challenges.dao.ChallengeRepository;
import com.ssafy.DDGo.challenges.domain.Challenge;
import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final AttemptVideoService attemptVideoService;

    @Transactional
    public AttemptStartResponse startAttempt(String username, Long challengeId) {
        // 1. 챌린지 존재 여부 확인
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_FOUND, "해당 챌린지를 찾을 수 없습니다."));

        // 1-1. 본인의 챌린지인지 인가 검증
        if (!challenge.getUser().getUsername().equals(username)) {
            throw new CustomException(ErrorCode.CHALLENGE_ACCESS_DENIED, "본인이 생성한 챌린지에만 시도를 추가할 수 있습니다.");
        }

        // 1-2. 챌린지가 아직 진행 중(ACTIVE)인지 확인
        if (challenge.getChallengeStatus() == com.ssafy.DDGo.challenges.domain.ChallengeStatus.CLOSED) {
            throw new CustomException(ErrorCode.CHALLENGE_ALREADY_CLOSED, "이미 종료된 챌린지에는 시도를 추가할 수 없습니다.");
        }

        // 2. 카운터 원자적 증가 (next_attempt_no += 1)
        int updatedRows = counterRepository.incrementAttemptNo(challengeId);
        if (updatedRows == 0) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "시도 번호 발급에 실패했습니다. (카운터 누락)");
        }

        // 3. 증가된 번호 조회
        Integer newAttemptNo = counterRepository.findNextAttemptNo(challengeId)
                .orElseThrow(() -> new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "시도 번호를 조회할 수 없습니다."));

        // 4. Attempt 엔티티 생성 및 DB 저장 (초기 상태: UPLOADING)
        Attempt attempt = Attempt.builder()
                .challenge(challenge)
                .attemptNo(newAttemptNo)
                .build();

        attemptRepository.save(attempt);

        return AttemptStartResponse.from(attempt);
    }

    public AttemptListResponse getAttempts(String username, Long challengeId) {
        // 1. 챌린지 존재 여부 확인
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_FOUND, "해당 챌린지를 찾을 수 없습니다."));

        // 2. 인가 검증 (본인의 챌린지에만 시도 목록 조회 가능)
        if (!challenge.getUser().getUsername().equals(username)) {
            throw new CustomException(ErrorCode.CHALLENGE_ACCESS_DENIED, "해당 챌린지의 접근 권한이 없습니다.");
        }

        // 3. 챌린지 ID에 해당하는 시도 목록 조회 (시도 번호 순서 오름차순 정렬)
        List<Attempt> attempts = attemptRepository.findByChallengeIdOrderByAttemptNoAsc(challengeId);

        // 4. 응답 DTO 매핑
        List<AttemptDetailResponse> attemptDetails = attempts.stream()
                .map(AttemptDetailResponse::from)
                .collect(Collectors.toList());

        return new AttemptListResponse(challengeId, attemptDetails);
    }

    public AttemptFullResponse getAttemptDetail(String username, Long challengeId, Long attemptId) {
        // 1. 시도 조회
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new CustomException(ErrorCode.ATTEMPT_NOT_FOUND, "존재하지 않는 시도입니다. ID: " + attemptId));

        // 2. 경로의 챌린지 아이디와 시도의 챌린지 아이디가 일치하는지 검증
        if (!attempt.getChallenge().getId().equals(challengeId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "해당 챌린지에 속한 시도가 아닙니다.");
        }

        // 3. 인가 검증
        if (!attempt.getChallenge().getUser().getUsername().equals(username)) {
            throw new CustomException(ErrorCode.CHALLENGE_ACCESS_DENIED, "해당 시도 정보에 접근할 권한이 없습니다.");
        }

        // 3. 영상 URL 발급 (존재하는 경우)
        String videoUrl = attemptVideoService.getVideoUrlForAttempt(attemptId);

        // 4. Metrics 및 Feedback 조회
        AttemptMetrics metrics = attemptMetricsRepository.findByAttemptId(attemptId).orElse(null);
        AttemptFeedback feedback = attemptFeedbackRepository.findByAttemptId(attemptId).orElse(null);

        // 5. 응답 DTO 변환
        return AttemptFullResponse.from(attempt, videoUrl, metrics, feedback);
    }

    @Transactional
    public AttemptDetailResponse endAttempt(String username, Long challengeId, Long attemptId,
            AttemptEndRequest request) {
        // 1. 시도 조회
        Attempt attempt = attemptRepository.findById(attemptId)
                .orElseThrow(() -> new CustomException(ErrorCode.ATTEMPT_NOT_FOUND, "존재하지 않는 시도입니다. ID: " + attemptId));

        // 2. 경로의 챌린지 아이디와 시도의 챌린지 아이디가 일치하는지 검증
        if (!attempt.getChallenge().getId().equals(challengeId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "해당 챌린지에 속한 시도가 아닙니다.");
        }

        // 3. 인가 검증
        if (!attempt.getChallenge().getUser().getUsername().equals(username)) {
            throw new CustomException(ErrorCode.CHALLENGE_ACCESS_DENIED, "해당 시도를 종료할 권한이 없습니다.");
        }

        // 3-1. 상태 검증 (이미 종료된 시도인지 확인)
        if (attempt.getAttemptStatus() == com.ssafy.DDGo.attempts.domain.AttemptStatus.DONE) {
            throw new CustomException(ErrorCode.INVALID_ATTEMPT_STATUS, "이미 종료된 시도입니다.");
        }

        // 4. 시도 종료 처리 (기본 정보)
        if (request.baseData() != null) {
            attempt.endAttempt(request.baseData().attemptResult(), request.baseData().durationMs(),
                    request.baseData().maxHoldNo());
        } else {
            // baseData가 필수라고 가정하더라도 기본적으로 DONE 처리는 수행
            attempt.endAttempt(null, null, null);
        }

        // 5. 정량 분석 데이터(Metrics) 저장
        if (request.metricsData() != null) {
            AttemptMetrics metrics = AttemptMetrics.builder()
                    .attempt(attempt)
                    .centerStabilityRatio(request.metricsData().centerStabilityRatio())
                    .cruxHoldNo(request.metricsData().cruxHoldNo())
                    .cruxDurationMs(request.metricsData().cruxDurationMs())
                    .dangerEventCount(request.metricsData().dangerEventCount())
                    .build();
            attemptMetricsRepository.save(metrics);
        }

        // 6. AI 텍스트 피드백(Feedbacks) 저장
        if (request.feedbacksData() != null) {
            AttemptFeedback feedback = AttemptFeedback.builder()
                    .attempt(attempt)
                    .failureReason(request.feedbacksData().failureReason())
                    .riskAlert(request.feedbacksData().riskAlert())
                    .nextMission(request.feedbacksData().nextMission())
                    .build();
            attemptFeedbackRepository.save(feedback);
        }

        return AttemptDetailResponse.from(attempt);
    }
}
