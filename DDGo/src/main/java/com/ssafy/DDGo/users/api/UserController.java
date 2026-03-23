package com.ssafy.DDGo.users.api;

import com.ssafy.DDGo.global.common.ApiResponse;
import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import com.ssafy.DDGo.users.application.UserService;
import com.ssafy.DDGo.users.dto.request.SocialLoginRequest;
import com.ssafy.DDGo.users.dto.request.TokenRefreshRequest;
import com.ssafy.DDGo.users.dto.request.UserLoginRequest;
import com.ssafy.DDGo.users.dto.request.UserNicknameUpdateRequest;
import com.ssafy.DDGo.users.dto.request.UserOnboardRequest;
import com.ssafy.DDGo.users.dto.request.UserPasswordUpdateRequest;
import com.ssafy.DDGo.users.dto.request.UserProfileUpdateRequest;
import com.ssafy.DDGo.users.dto.request.UserRegisterRequest;
import com.ssafy.DDGo.users.dto.response.TokenRefreshResponse;
import com.ssafy.DDGo.users.dto.response.UserInfoResponse;
import com.ssafy.DDGo.users.dto.response.UserLoginResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "사용자", description = "사용자 관련 API")
@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class UserController {

    private static final String SOCIAL_LOGIN_SUCCESS_EXAMPLE = """
            {
              "success": true,
              "code": null,
              "message": "소셜 로그인에 성공했습니다.",
              "data": {
                "accessToken": "ddgo-access-token",
                "refreshToken": "ddgo-refresh-token",
                "isNewUser": true,
                "needsOnboarding": true
              }
            }
            """;

    private static final String SOCIAL_LINK_SUCCESS_EXAMPLE = """
            {
              "success": true,
              "code": null,
              "message": "소셜 계정 연동이 완료되었습니다.",
              "data": null
            }
            """;

    private static final String SOCIAL_LINK_REQUIRED_EXAMPLE = """
            {
              "success": false,
              "code": "A006",
              "message": "기존 DDGo 계정이 존재합니다. 먼저 로그인한 뒤 소셜 계정을 연동해 주세요.",
              "data": null
            }
            """;

    private static final String SOCIAL_WITHDRAWN_EXAMPLE = """
            {
              "success": false,
              "code": "A007",
              "message": "탈퇴한 사용자의 소셜 계정이라 사용할 수 없습니다.",
              "data": null
            }
            """;

    private static final String SOCIAL_ALREADY_LINKED_EXAMPLE = """
            {
              "success": false,
              "code": "A008",
              "message": "이미 다른 DDGo 사용자에 연동된 소셜 계정입니다.",
              "data": null
            }
            """;

    private static final String SOCIAL_TOKEN_INVALID_EXAMPLE = """
            {
              "success": false,
              "code": "A005",
              "message": "유효하지 않은 소셜 토큰입니다.",
              "data": null
            }
            """;

    private static final String UNAUTHORIZED_EXAMPLE = """
            {
              "success": false,
              "code": "A001",
              "message": "인증이 필요합니다.",
              "data": null
            }
            """;

    private final UserService userService;

    @Operation(summary = "회원가입", description = "이메일, 비밀번호, 닉네임으로 DDGo 계정을 생성합니다.")
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> registerUser(@RequestBody @Valid UserRegisterRequest request) {
        userService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("회원가입이 완료되었습니다.", null));
    }

    @Operation(summary = "로그인", description = "이메일과 비밀번호로 DDGo 액세스 토큰과 리프레시 토큰을 발급합니다.")
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserLoginResponse>> login(
            @RequestBody @Valid UserLoginRequest request,
            HttpServletRequest httpServletRequest) {

        String clientIp = httpServletRequest.getHeader("X-Forwarded-For");
        if (clientIp == null || clientIp.isEmpty() || "unknown".equalsIgnoreCase(clientIp)) {
            clientIp = httpServletRequest.getRemoteAddr();
        } else {
            clientIp = clientIp.split(",")[0].trim();
        }

        UserLoginResponse tokenResponse = userService.login(request, clientIp);
        return ResponseEntity.ok(ApiResponse.success("로그인에 성공했습니다.", tokenResponse));
    }

    @Operation(summary = "소셜 로그인", description = "카카오 액세스 토큰 또는 구글 ID 토큰을 DDGo 토큰으로 교환합니다.")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "소셜 로그인 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(name = "success", value = SOCIAL_LOGIN_SUCCESS_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "유효하지 않은 소셜 토큰",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(name = "invalidSocialToken", value = SOCIAL_TOKEN_INVALID_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "기존 계정 연동 필요 또는 탈퇴한 소셜 계정",
                    content = @Content(mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "linkRequired", value = SOCIAL_LINK_REQUIRED_EXAMPLE),
                                    @ExampleObject(name = "withdrawnSocialAccount", value = SOCIAL_WITHDRAWN_EXAMPLE)
                            }))
    })
    @PostMapping("/social/login")
    public ResponseEntity<ApiResponse<UserLoginResponse>> socialLogin(@RequestBody @Valid SocialLoginRequest request) {
        UserLoginResponse tokenResponse = userService.socialLogin(request);
        return ResponseEntity.ok(ApiResponse.success("소셜 로그인에 성공했습니다.", tokenResponse));
    }

    @Operation(summary = "소셜 계정 연동", description = "현재 로그인한 DDGo 계정에 카카오 또는 구글 계정을 연동합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @io.swagger.v3.oas.annotations.responses.ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "소셜 계정 연동 성공",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(name = "success", value = SOCIAL_LINK_SUCCESS_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "401",
                    description = "인증 필요",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(name = "unauthorized", value = UNAUTHORIZED_EXAMPLE))),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "409",
                    description = "이미 연동된 계정이거나 탈퇴한 소셜 계정",
                    content = @Content(mediaType = "application/json",
                            examples = {
                                    @ExampleObject(name = "alreadyLinked", value = SOCIAL_ALREADY_LINKED_EXAMPLE),
                                    @ExampleObject(name = "withdrawnSocialAccount", value = SOCIAL_WITHDRAWN_EXAMPLE)
                            }))
    })
    @PostMapping("/social/link")
    public ResponseEntity<ApiResponse<Void>> linkSocialAccount(
            Authentication authentication,
            @RequestBody @Valid SocialLoginRequest request) {
        userService.linkSocialAccount(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("소셜 계정 연동이 완료되었습니다.", null));
    }

    @Operation(summary = "내 정보 조회", description = "현재 로그인한 사용자의 프로필 정보를 조회합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserInfoResponse>> getMyInfo(Authentication authentication) {
        UserInfoResponse userInfo = userService.getUserInfo(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("내 정보를 조회했습니다.", userInfo));
    }

    @Operation(summary = "닉네임 변경", description = "현재 로그인한 사용자의 닉네임을 변경합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/nickname")
    public ResponseEntity<ApiResponse<Void>> updateNickname(
            Authentication authentication,
            @RequestBody @Valid UserNicknameUpdateRequest request) {
        userService.updateNickname(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("닉네임이 변경되었습니다.", null));
    }

    @Operation(summary = "온보딩 정보 저장", description = "현재 로그인한 사용자의 온보딩 프로필 정보를 저장합니다.")
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
        return ResponseEntity.ok(ApiResponse.success("온보딩 정보가 저장되었습니다.", null));
    }

    @Operation(summary = "프로필 수정", description = "현재 로그인한 사용자의 신체 프로필 정보를 수정합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/profile")
    public ResponseEntity<ApiResponse<Void>> updateProfile(
            Authentication authentication,
            @RequestBody @Valid UserProfileUpdateRequest request) {
        userService.updateUserProfile(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("프로필이 수정되었습니다.", null));
    }

    @Operation(summary = "회원 탈퇴", description = "현재 로그인한 DDGo 계정을 소프트 삭제합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> closeAccount(Authentication authentication) {
        userService.deleteUser(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("회원 탈퇴가 완료되었습니다.", null));
    }

    @Operation(summary = "비밀번호 변경", description = "로컬 DDGo 계정의 비밀번호를 변경합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PatchMapping("/password")
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            Authentication authentication,
            @RequestBody @Valid UserPasswordUpdateRequest request) {
        userService.updatePassword(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("비밀번호가 변경되었습니다.", null));
    }

    @Operation(summary = "토큰 재발급", description = "DDGo 액세스 토큰과 리프레시 토큰을 재발급합니다.")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> reissueToken(
            @RequestBody @Valid TokenRefreshRequest request) {
        TokenRefreshResponse response = userService.reissueToken(request);
        return ResponseEntity.ok(ApiResponse.success("토큰이 재발급되었습니다.", response));
    }

    @Operation(summary = "로그아웃", description = "현재 액세스 토큰을 블랙리스트 처리하고 리프레시 토큰을 제거합니다.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken == null || !bearerToken.startsWith("Bearer ")) {
            throw new CustomException(ErrorCode.INVALID_TOKEN, "액세스 토큰이 필요합니다.");
        }
        userService.logout(bearerToken.substring(7));
        return ResponseEntity.ok(ApiResponse.success("로그아웃이 완료되었습니다.", null));
    }
}
