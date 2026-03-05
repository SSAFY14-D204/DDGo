package com.ssafy.DDGo.attempts.api;

import com.ssafy.DDGo.attempts.application.AttemptService;
import com.ssafy.DDGo.attempts.dto.request.AttemptEndRequest;
import com.ssafy.DDGo.attempts.dto.response.AttemptDetailResponse;
import com.ssafy.DDGo.attempts.dto.response.AttemptFullResponse;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Attempts", description = "등반 시도(Attempt) 관련 API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/v1")
@RequiredArgsConstructor
public class AttemptController {

    private final AttemptService attemptService;

    @Operation(summary = "시도(Attempt) 생성", description = "특정 챌린지 내에 새로운 시도를 생성하고, 시도 번호(Attempt No)를 발급받습니다.")
    @PostMapping("/challenges/{challengeId}/attempts")
    public ResponseEntity<ApiResponse<AttemptStartResponse>> startAttempt(
            Authentication authentication,
            @PathVariable("challengeId") Long challengeId) {

        AttemptStartResponse response = attemptService.startAttempt(authentication.getName(), challengeId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("새로운 시도가 생성되었습니다.", response));
    }

    @Operation(summary = "시도 목록 조회", description = "특정 챌린지의 모든 시도(Attempt) 목록을 조회합니다.")
    @GetMapping("/challenges/{challengeId}/attempts")
    public ResponseEntity<ApiResponse<AttemptListResponse>> getAttempts(
            Authentication authentication,
            @PathVariable("challengeId") Long challengeId) {

        AttemptListResponse response = attemptService.getAttempts(authentication.getName(), challengeId);

        return ResponseEntity.ok(ApiResponse.success("시도 목록 조회가 완료되었습니다.", response));
    }

    @Operation(summary = "시도 상세 조회", description = "단일 시도(Attempt)의 상세 정보를 조회합니다. (영상 재생용 프론트엔드 URL 포함)")
    @GetMapping("/challenges/{challengeId}/attempts/{attemptId}")
    public ResponseEntity<ApiResponse<AttemptFullResponse>> getAttemptDetail(
            Authentication authentication,
            @PathVariable("challengeId") Long challengeId,
            @PathVariable("attemptId") Long attemptId) {

        AttemptFullResponse response = attemptService.getAttemptDetail(authentication.getName(), challengeId,
                attemptId);

        return ResponseEntity.ok(ApiResponse.success("시도 상세 정보 조회가 완료되었습니다.", response));
    }

    @Operation(summary = "시도 종료 처리", description = "프론트엔드에서 측정한 결과를 바탕으로 해당 시도의 상태를 DONE으로 변경하고 결과를 저장합니다.")
    @PatchMapping("/challenges/{challengeId}/attempts/{attemptId}")
    public ResponseEntity<ApiResponse<AttemptDetailResponse>> endAttempt(
            Authentication authentication,
            @PathVariable("challengeId") Long challengeId,
            @PathVariable("attemptId") Long attemptId,
            @RequestBody AttemptEndRequest request) {

        AttemptDetailResponse response = attemptService.endAttempt(authentication.getName(), challengeId, attemptId,
                request);

        return ResponseEntity.ok(ApiResponse.success("시도 종료 처리가 완료되었습니다.", response));
    }
}
