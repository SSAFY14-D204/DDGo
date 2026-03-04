package com.ssafy.DDGo.attempts.application;

import com.ssafy.DDGo.attempts.dao.AttemptRepository;
import com.ssafy.DDGo.attempts.domain.Attempt;
import com.ssafy.DDGo.attempts.dto.response.AttemptStartResponse;
import com.ssafy.DDGo.challenge.dao.ChallengeAttemptCounterRepository;
import com.ssafy.DDGo.challenge.dao.ChallengeRepository;
import com.ssafy.DDGo.challenge.domain.Challenge;
import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttemptService {

    private final ChallengeRepository challengeRepository;
    private final ChallengeAttemptCounterRepository counterRepository;
    private final AttemptRepository attemptRepository;

    @Transactional
    public AttemptStartResponse startAttempt(Long challengeId) {
        // 1. 챌린지 존재 여부 확인
        Challenge challenge = challengeRepository.findById(challengeId)
                .orElseThrow(() -> new CustomException(ErrorCode.CHALLENGE_NOT_FOUND, "해당 챌린지를 찾을 수 없습니다."));

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
}
