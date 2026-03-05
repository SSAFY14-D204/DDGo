package com.ssafy.DDGo.attempts.dto.response;

import com.ssafy.DDGo.attempts.domain.Attempt;
import com.ssafy.DDGo.attempts.domain.AttemptResult;
import com.ssafy.DDGo.attempts.domain.AttemptStatus;

import java.time.LocalDateTime;

public record AttemptFullResponse(
        Long attemptId,
        Integer attemptNo,
        AttemptStatus attemptStatus,
        AttemptResult attemptResult,
        LocalDateTime createdAt,
        Integer durationMs,
        Integer maxHoldNo,
        String videoUrl) {

    public static AttemptFullResponse from(Attempt attempt, String videoUrl) {
        return new AttemptFullResponse(
                attempt.getId(),
                attempt.getAttemptNo(),
                attempt.getAttemptStatus(),
                attempt.getAttemptResult(),
                attempt.getCreatedAt(),
                attempt.getDurationMs(),
                attempt.getMaxHoldNo(),
                videoUrl);
    }
}
