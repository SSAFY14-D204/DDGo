package com.ssafy.DDGo.attempts.api;

import com.ssafy.DDGo.attempts.application.AttemptService;
import com.ssafy.DDGo.attempts.dto.response.AttemptListResponse;
import com.ssafy.DDGo.attempts.dto.response.AttemptStartResponse;
import com.ssafy.DDGo.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Attempts", description = "등반 시도(Attempt) 관련 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/v1/challenges/{challengeId}/attempts")
@RequiredArgsConstructor
public class AttemptController {

    private final AttemptService attemptService;

    @Operation(summary = "시도(Attempt) 생성", description = "특정 챌린지 내에 새로운 시도를 생성하고, 시도 번호(Attempt No)를 발급받습니다.")
    @PostMapping
    public ResponseEntity<ApiResponse<AttemptStartResponse>> startAttempt(
            Authentication authentication,
            @PathVariable("challengeId") Long challengeId) {

        AttemptStartResponse response = attemptService.startAttempt(authentication.getName(), challengeId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("새로운 시도가 생성되었습니다.", response));
    }

    @Operation(summary = "시도 목록 조회", description = "특정 챌린지의 모든 시도(Attempt) 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<AttemptListResponse>> getAttempts(
            Authentication authentication,
            @PathVariable("challengeId") Long challengeId) {

        AttemptListResponse response = attemptService.getAttempts(authentication.getName(), challengeId);

        return ResponseEntity.ok(ApiResponse.success("시도 목록 조회가 완료되었습니다.", response));
    }
}
