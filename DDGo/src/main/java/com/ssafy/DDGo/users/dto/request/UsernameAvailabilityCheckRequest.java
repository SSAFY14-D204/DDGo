package com.ssafy.DDGo.users.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "아이디(이메일 형식) 중복 확인 요청")
public class UsernameAvailabilityCheckRequest {

    @Schema(description = "중복 확인할 아이디(이메일 형식)", example = "user@example.com", maxLength = 255)
    @NotBlank(message = "아이디를 입력해 주세요.")
    @Email(message = "아이디는 이메일 형식이어야 합니다.")
    @Size(max = 255, message = "아이디는 255자 이하로 입력해 주세요.")
    private String username;
}
