package com.ssafy.DDGo.challenges.api;

import com.ssafy.DDGo.challenges.application.ChallengeService;
import com.ssafy.DDGo.challenges.dto.request.ChallengeCloseRequest;
import com.ssafy.DDGo.challenges.dto.request.ChallengeCreateRequest;
import com.ssafy.DDGo.challenges.dto.request.HoldSaveRequest;
import com.ssafy.DDGo.challenges.dto.response.ChallengeCloseResponse;
import com.ssafy.DDGo.challenges.dto.response.ChallengeCreateResponse;
import com.ssafy.DDGo.challenges.dto.response.ChallengeSummaryResponse;
import com.ssafy.DDGo.challenges.dto.response.HoldSaveResponse;
import com.ssafy.DDGo.challenges.dto.response.ChallengeListResponse;
import com.ssafy.DDGo.challenges.dto.response.ChallengeStatusResponse;
import com.ssafy.DDGo.global.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

import java.util.List;
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

    @Operation(summary = "챌린지 상태 조회", description = "특정 챌린지의 현재 상태(ACTIVE / CLOSED)를 조회합니다.")
    @GetMapping("/{challengeId}/status")
    public ResponseEntity<ApiResponse<ChallengeStatusResponse>> getChallengeStatus(
            Authentication authentication,
            @PathVariable Long challengeId) {
        ChallengeStatusResponse response = challengeService.getChallengeStatus(authentication.getName(), challengeId);
        return ResponseEntity.ok(ApiResponse.success("챌린지 상태 조회 성공", response));
    }

    @Operation(summary = "내 챌린지 목록 조회", description = "로그인한 사용자의 챌린지 목록을 최신순으로 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<List<ChallengeListResponse>>> getChallenges(
            Authentication authentication) {
        List<ChallengeListResponse> response = challengeService.getChallenges(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("챌린지 목록 조회 성공", response));
    }

    @Operation(summary = "홀드 좌표 저장", description = "챌린지에 홀드 좌표 목록을 저장합니다. 기존 홀드 데이터가 있으면 덮어씁니다.")
    @PatchMapping("/{challengeId}/holds")
    public ResponseEntity<ApiResponse<HoldSaveResponse>> saveHolds(
            Authentication authentication,
            @PathVariable Long challengeId,
            @RequestBody @Valid HoldSaveRequest request) {
        HoldSaveResponse response = challengeService.saveHolds(authentication.getName(), challengeId, request);
        return ResponseEntity.ok(ApiResponse.success("홀드 좌표가 저장되었습니다.", response));
    }

    @Operation(summary = "챌린지 종합 분석 조회", description = "챌린지 종료 후 생성된 종합 분석 결과를 조회합니다. 종료 전 호출 시 404를 반환합니다.")
    @GetMapping("/{challengeId}/summary")
    public ResponseEntity<ApiResponse<ChallengeSummaryResponse>> getChallengeSummary(
            Authentication authentication,
            @PathVariable Long challengeId) {
        ChallengeSummaryResponse response = challengeService.getChallengeSummary(authentication.getName(), challengeId);
        return ResponseEntity.ok(ApiResponse.success("챌린지 종합 분석 조회 성공", response));
    }

    @Operation(summary = "챌린지 종료", description = "챌린지(문제 세션)를 종료합니다. 결과(SUCCESS/FAIL/UNKNOWN)를 선택할 수 있으며, 미입력 시 UNKNOWN으로 처리됩니다.")
    @PatchMapping("/{challengeId}/close")
    public ResponseEntity<ApiResponse<ChallengeCloseResponse>> closeChallenge(
            Authentication authentication,
            @PathVariable Long challengeId,
            @RequestBody(required = false) ChallengeCloseRequest request) {
        ChallengeCloseResponse response = challengeService.closeChallenge(
                authentication.getName(),
                challengeId,
                request != null ? request : new ChallengeCloseRequest()
        );
        return ResponseEntity.ok(ApiResponse.success("챌린지가 종료되었습니다.", response));
    }
}
