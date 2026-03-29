package com.ssafy.DDGo.users.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
@Schema(description = "DDGo 로그인 토큰 응답")
public class UserLoginResponse {

    @Schema(description = "DDGo 액세스 토큰", example = "eyJhbGciOiJIUzI1NiJ9.access.payload")
    private String accessToken;

    @Schema(description = "DDGo 리프레시 토큰", example = "eyJhbGciOiJIUzI1NiJ9.refresh.payload")
    private String refreshToken;

    @Schema(description = "소셜 계정 기준 첫 로그인 여부", example = "true")
    private Boolean isNewUser;

    @Schema(description = "온보딩 또는 프로필 설정이 추가로 필요한지 여부", example = "true")
    private Boolean needsOnboarding;
}
