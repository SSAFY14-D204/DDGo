package com.ssafy.DDGo.users.dto.request;

import com.ssafy.DDGo.users.domain.PasswordPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserRegisterRequest {

    @Schema(description = "아이디 (이메일 형식)", example = "user@example.com", maxLength = 255)
    @NotBlank(message = "아이디를 입력해주세요.")
    @Email(message = "아이디는 이메일 형식으로 입력해주세요.")
    @Size(max = 255, message = "아이디는 255자 이하로 입력해주세요.")
    private String username;

    @Schema(description = "비밀번호 (8~64자, 영문/숫자/특수문자 중 2종 이상, 공백 불가)", example = "password123!")
    @NotBlank(message = "비밀번호를 입력해주세요.")
    @Pattern(regexp = PasswordPolicy.PASSWORD_PATTERN, message = PasswordPolicy.POLICY_MESSAGE)
    private String password;

    @Schema(description = "사용자 닉네임", example = "DDGoUser", maxLength = 20)
    @NotBlank(message = "닉네임을 입력해주세요.")
    @Size(max = 20, message = "닉네임은 20자 이하로 입력해주세요.")
    private String nickname;
}
