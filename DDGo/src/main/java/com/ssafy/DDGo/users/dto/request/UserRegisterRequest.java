package com.ssafy.DDGo.users.dto.request;

import com.ssafy.DDGo.users.domain.PasswordPolicy;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserRegisterRequest {

    @Schema(description = "아이디(이메일 형식)", example = "user@example.com", maxLength = 255)
    @NotBlank(message = "아이디를 입력해 주세요.")
    @Email(message = "아이디는 이메일 형식으로 입력해 주세요.")
    @Size(max = 255, message = "아이디는 255자 이하로 입력해 주세요.")
    private String username;

    @Schema(
            description = "비밀번호 (8~64자, 영문/숫자/특수문자 중 2종 이상, 공백 불가)",
            example = "password123!")
    @NotBlank(message = "비밀번호를 입력해 주세요.")
    @Pattern(regexp = PasswordPolicy.PASSWORD_PATTERN, message = PasswordPolicy.POLICY_MESSAGE)
    private String password;

    @Schema(
            description = "하위 호환용 필드입니다. 회원가입 시 닉네임은 서버가 자동 생성하며 이 값은 사용하지 않습니다.",
            example = "맑은하늘",
            maxLength = 20,
            nullable = true)
    private String nickname;
}
