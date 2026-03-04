package com.ssafy.DDGo.attempts.dto.response;

import com.ssafy.DDGo.attempts.domain.Attempt;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AttemptStartResponse {
    private Long attemptId;
    private Integer attemptNo;

    public static AttemptStartResponse from(Attempt attempt) {
        return AttemptStartResponse.builder()
                .attemptId(attempt.getId())
                .attemptNo(attempt.getAttemptNo())
                .build();
    }
}
