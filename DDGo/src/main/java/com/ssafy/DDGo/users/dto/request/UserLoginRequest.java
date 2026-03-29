package com.ssafy.DDGo.users.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserLoginRequest {

    @Schema(description = "아이디 (이메일 형식)", example = "user@example.com", maxLength = 255)
    @NotBlank(message = "아이디를 입력해주세요.")
    @Email(message = "아이디는 이메일 형식으로 입력해주세요.")
    @Size(max = 255, message = "아이디는 255자 이하로 입력해주세요.")
    private String username;

    @Schema(description = "비밀번호", example = "password123!")
    @NotBlank(message = "비밀번호를 입력해주세요.")
    private String password;
}
