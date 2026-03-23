package com.ssafy.DDGo.users.dto.request;

import com.ssafy.DDGo.users.domain.SocialProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SocialLoginRequest {

    @NotNull(message = "소셜 제공자는 필수입니다.")
    @Schema(description = "소셜 로그인 제공자", example = "KAKAO", allowableValues = { "KAKAO", "GOOGLE" })
    private SocialProvider provider;

    @Schema(description = "카카오 액세스 토큰입니다. provider가 KAKAO일 때 필수입니다.",
            example = "kakao-access-token", nullable = true)
    private String accessToken;

    @Schema(description = "구글 ID 토큰입니다. provider가 GOOGLE일 때 필수입니다.",
            example = "google-id-token", nullable = true)
    private String idToken;
}
