package com.ssafy.DDGo.users.api;

import com.ssafy.DDGo.global.common.ApiResponse;
import com.ssafy.DDGo.users.application.UserService;
import com.ssafy.DDGo.users.dto.request.UserLoginRequest;
import com.ssafy.DDGo.users.dto.response.UserLoginResponse;
import com.ssafy.DDGo.users.dto.request.UserRegisterRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
