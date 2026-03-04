package com.ssafy.DDGo.users.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class TokenRefreshRequest {
    @NotBlank(message = "Refresh Token은 필수입니다.")
    private String refreshToken;
}
