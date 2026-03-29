package com.ssafy.DDGo.users.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "토큰 재발급 요청")
public class TokenRefreshRequest {

    @NotBlank(message = "리프레시 토큰은 필수입니다.")
    @Schema(description = "재발급에 사용할 DDGo 리프레시 토큰", example = "eyJhbGciOiJIUzI1NiJ9.refresh.payload")
    private String refreshToken;
}
