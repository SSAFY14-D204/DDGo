package com.ssafy.DDGo.users.dto.response;

import com.ssafy.DDGo.users.domain.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserInfoResponse {

    private Long id;
    private String username;
    private String nickname;

    public static UserInfoResponse from(User user) {
        return UserInfoResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .build();
    }
}
