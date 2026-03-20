package com.ssafy.DDGo.users.dto.request;

import com.ssafy.DDGo.users.domain.PasswordPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserPasswordUpdateRequest {

    @Schema(description = "기존 비밀번호", example = "oldPassword123!")
    @NotBlank(message = "기존 비밀번호를 입력해주세요.")
    private String oldPassword;

    @Schema(description = "새 비밀번호 (8~64자, 영문/숫자/특수문자 중 2종 이상, 공백 불가)", example = "newPassword!@#")
    @NotBlank(message = "새로운 비밀번호를 입력해주세요.")
    @Pattern(regexp = PasswordPolicy.PASSWORD_PATTERN, message = PasswordPolicy.POLICY_MESSAGE)
    private String newPassword;
}
