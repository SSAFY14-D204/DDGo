package com.ssafy.DDGo.users.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Schema(description = "닉네임 중복 확인 요청")
public class NicknameAvailabilityCheckRequest {

    @Schema(description = "중복 확인할 닉네임", example = "맑은하늘", maxLength = 20)
    @NotBlank(message = "닉네임을 입력해 주세요.")
    @Size(max = 20, message = "닉네임은 20자 이하로 입력해 주세요.")
    private String nickname;
}
