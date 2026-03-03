package com.ssafy.DDGo.users.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class UserLoginResponse {
    private String accessToken;
    private String refreshToken;
}
