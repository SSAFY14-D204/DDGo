package com.ssafy.DDGo.users.dto.request;

import com.ssafy.DDGo.users.domain.PasswordPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Schema(description = "비밀번호 재설정 완료 요청")
public class PasswordResetConfirmRequest {

    @NotBlank(message = "비밀번호 재설정 토큰은 필수입니다.")
    @Schema(description = "이메일로 전달받은 비밀번호 재설정 토큰", example = "reset-token")
    private String token;

    @NotBlank(message = "새 비밀번호는 필수입니다.")
    @Pattern(regexp = PasswordPolicy.PASSWORD_PATTERN, message = PasswordPolicy.POLICY_MESSAGE)
    @Schema(description = "새 비밀번호", example = "NewPassword123!")
    private String newPassword;
}
