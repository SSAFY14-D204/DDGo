package com.ssafy.DDGo.users.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class UserNicknameUpdateRequest {

    @NotBlank(message = "변경할 닉네임을 입력해주세요.")
    @Size(max = 20, message = "닉네임은 20자 이하로 입력해주세요.")
    private String nickname;
}
