package com.ssafy.DDGo.challenge.api;

import com.ssafy.DDGo.challenge.application.ChallengeService;
import com.ssafy.DDGo.challenge.dto.request.ChallengeCreateRequest;
import com.ssafy.DDGo.challenge.dto.response.ChallengeCreateResponse;
import com.ssafy.DDGo.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Challenges", description = "챌린지(문제 세션) 관련 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/v1/challenges")
@RequiredArgsConstructor
public class ChallengeController {

    private final ChallengeService challengeService;

    @Operation(summary = "챌린지 생성", description = "새로운 챌린지(문제 세션)를 시작합니다. 생성과 동시에 attempt 카운터가 자동으로 초기화됩니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<ChallengeCreateResponse>> createChallenge(
            Authentication authentication,
            @RequestBody @Valid ChallengeCreateRequest request) {
        ChallengeCreateResponse response = challengeService.createChallenge(authentication.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("챌린지가 생성되었습니다.", response));
    }
}
