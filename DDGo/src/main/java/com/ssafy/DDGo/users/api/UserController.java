package com.ssafy.DDGo.users.api;

import com.ssafy.DDGo.global.common.ApiResponse;
import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import com.ssafy.DDGo.users.application.UserService;
import com.ssafy.DDGo.users.dto.request.UserLoginRequest;
import com.ssafy.DDGo.users.dto.response.UserLoginResponse;
import com.ssafy.DDGo.users.dto.request.UserRegisterRequest;
import com.ssafy.DDGo.users.dto.request.UserOnboardRequest;
import com.ssafy.DDGo.users.dto.request.UserProfileUpdateRequest;
import com.ssafy.DDGo.users.dto.response.UserInfoResponse;
import com.ssafy.DDGo.users.dto.request.UserNicknameUpdateRequest;
import com.ssafy.DDGo.users.dto.request.UserPasswordUpdateRequest;
import com.ssafy.DDGo.users.dto.request.TokenRefreshRequest;
import com.ssafy.DDGo.users.dto.response.TokenRefreshResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.core.Authentication;

@Tag(name = "Users", description = "회원 관련 API")
@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "회원가입", description = "아이디, 비밀번호, 닉네임으로 새 계정을 생성합니다.")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> registerUser(@RequestBody @Valid UserRegisterRequest request) {
        userService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("회원가입이 완료되었습니다.", null));
    }

    @Operation(summary = "로그인", description = "아이디와 비밀번호로 로그인하여 Access Token과 Refresh Token을 발급받습니다.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserLoginResponse>> login(@RequestBody @Valid UserLoginRequest request) {
        UserLoginResponse tokenResponse = userService.login(request);
        return ResponseEntity.ok(ApiResponse.success("로그인 성공", tokenResponse));
    }

    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자의 정보를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserInfoResponse>> getMyInfo(Authentication authentication) {
        UserInfoResponse userInfo = userService.getUserInfo(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("내 정보 조회 성공", userInfo));
    }

    @Operation(summary = "닉네임 변경", description = "로그인한 사용자의 닉네임을 변경합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/nickname")
    public ResponseEntity<ApiResponse<Void>> updateNickname(
            Authentication authentication,
            @RequestBody @Valid UserNicknameUpdateRequest request) {
        userService.updateNickname(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("닉네임 변경 성공", null));
    }

    @Operation(summary = "온보딩 신체 정보 등록", description = "회원가입 직후 온보딩 단계에서 신체 정보(성별, 키, 몸무게, 팔 길이)를 등록합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/onboard")
    public ResponseEntity<ApiResponse<Void>> updateOnboardInfo(
            Authentication authentication,
            @RequestBody @Valid UserOnboardRequest request) {
        userService.updateUserProfile(authentication.getName(),
                UserProfileUpdateRequest.builder()
                        .sex(request.getSex())
                        .heightCm(request.getHeightCm())
                        .weightKg(request.getWeightKg())
                        .wingspanCm(request.getWingspanCm())
                        .build());
        return ResponseEntity.ok(ApiResponse.success("신체 정보 등록 성공", null));
    }

    @Operation(summary = "신체 정보 수정", description = "로그인한 사용자의 신체 정보를 수정합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/profile")
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            Authentication authentication,
            @RequestBody @Valid UserProfileUpdateRequest request) {
        userService.updateUserProfile(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("신체 정보 수정 성공", null));
    }

    @Operation(summary = "회원 탈퇴", description = "로그인한 사용자의 계정을 삭제(소프트 삭제)합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> closeAccount(Authentication authentication) {
        userService.deleteUser(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("회원 탈퇴 성공", null));
    }

    @Operation(summary = "비밀번호 변경", description = "기존 비밀번호를 확인한 뒤 새 비밀번호로 변경합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/password")
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            Authentication authentication,
            @RequestBody @Valid UserPasswordUpdateRequest request) {
        userService.updatePassword(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("비밀번호 변경 성공", null));
    }

    @Operation(summary = "토큰 재발급", description = "Refresh Token으로 새로운 Access Token과 Refresh Token을 재발급받습니다.")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> reissueToken(
            @RequestBody @Valid TokenRefreshRequest request) {
        TokenRefreshResponse response = userService.reissueToken(request);
        return ResponseEntity.ok(ApiResponse.success("토큰 재발급 성공", response));
    }

    @Operation(summary = "로그아웃", description = "현재 Access Token을 블랙리스트에 등록하고 Refresh Token을 삭제합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(jakarta.servlet.http.HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            throw new CustomException(ErrorCode.INVALID_TOKEN, "Access Token이 필요합니다.");
        }
        userService.logout(bearerToken.substring(7));
        return ResponseEntity.ok(ApiResponse.success("로그아웃 성공", null));
    }
}

