package com.ssafy.DDGo.users.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "토큰 재발급 응답")
public class TokenRefreshResponse {

    @Schema(description = "새로 발급된 DDGo 액세스 토큰", example = "eyJhbGciOiJIUzI1NiJ9.access.payload")
    private String accessToken;

    @Schema(description = "새로 발급된 DDGo 리프레시 토큰", example = "eyJhbGciOiJIUzI1NiJ9.refresh.payload")
    private String refreshToken;
}
