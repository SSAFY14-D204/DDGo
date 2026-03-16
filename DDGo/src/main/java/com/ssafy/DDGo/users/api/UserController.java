package com.ssafy.DDGo.users.api;

import com.ssafy.DDGo.global.common.ApiResponse;
import com.ssafy.DDGo.users.application.UserService;
import com.ssafy.DDGo.users.dto.request.UserLoginRequest;
import com.ssafy.DDGo.users.dto.response.UserLoginResponse;
import com.ssafy.DDGo.users.dto.request.UserRegisterRequest;
import com.ssafy.DDGo.users.dto.request.UserOnboardRequest;
import com.ssafy.DDGo.users.dto.response.UserInfoResponse;
import com.ssafy.DDGo.users.dto.request.UserNicknameUpdateRequest;
import com.ssafy.DDGo.users.dto.request.UserPasswordUpdateRequest;
import com.ssafy.DDGo.users.dto.request.TokenRefreshRequest;
import com.ssafy.DDGo.users.dto.response.TokenRefreshResponse;
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

@RestController
@RequestMapping("/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> registerUser(@RequestBody @Valid UserRegisterRequest request) {
        userService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("회원가입이 완료되었습니다.", null));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<UserLoginResponse>> login(@RequestBody @Valid UserLoginRequest request) {
        UserLoginResponse tokenResponse = userService.login(request);
        return ResponseEntity.ok(ApiResponse.success("로그인 성공", tokenResponse));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserInfoResponse>> getMyInfo(Authentication authentication) {
        UserInfoResponse userInfo = userService.getUserInfo(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("내 정보 조회 성공", userInfo));
    }

    @PatchMapping("/nickname")
    public ResponseEntity<ApiResponse<Void>> updateNickname(
            Authentication authentication,
            @RequestBody @Valid UserNicknameUpdateRequest request) {
        userService.updateNickname(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("닉네임 변경 성공", null));
    }

    @PatchMapping("/onboard")
    public ResponseEntity<ApiResponse<Void>> updateOnboardInfo(
            Authentication authentication,
            @RequestBody @Valid UserOnboardRequest request) {
        userService.updateOnboardInfo(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("신체 정보 등록 성공", null));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> closeAccount(Authentication authentication) {
        userService.deleteUser(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success("회원 탈퇴 성공", null));
    }

    @PatchMapping("/password")
    public ResponseEntity<ApiResponse<Void>> updatePassword(
            Authentication authentication,
            @RequestBody @Valid UserPasswordUpdateRequest request) {
        userService.updatePassword(authentication.getName(), request);
        return ResponseEntity.ok(ApiResponse.success("비밀번호 변경 성공", null));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenRefreshResponse>> reissueToken(
            @RequestBody @Valid TokenRefreshRequest request) {
        TokenRefreshResponse response = userService.reissueToken(request);
        return ResponseEntity.ok(ApiResponse.success("토큰 재발급 성공", response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(jakarta.servlet.http.HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            userService.logout(bearerToken.substring(7));
        }
        return ResponseEntity.ok(ApiResponse.success("로그아웃 성공", null));
    }
}
