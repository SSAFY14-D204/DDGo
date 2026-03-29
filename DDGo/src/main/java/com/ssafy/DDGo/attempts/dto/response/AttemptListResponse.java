package com.ssafy.DDGo.attempts.dto.response;

import java.util.List;

public record AttemptListResponse(
        Long challengeId,
        List<AttemptDetailResponse> attempts) {
}
