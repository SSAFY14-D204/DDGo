package com.ssafy.DDGo.users.dto.response;

import com.ssafy.DDGo.users.domain.User;
import com.ssafy.DDGo.users.domain.UserProfile;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UserInfoResponse {

    private Long id;
    private String username;
    private String nickname;
    private String sex;
    private Integer heightCm;
    private Integer weightKg;
    private Integer wingspanCm;

    public static UserInfoResponse from(User user, UserProfile userProfile) {
        UserInfoResponseBuilder builder = UserInfoResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname());

        if (userProfile != null) {
            builder.sex(userProfile.getSex())
                    .heightCm(userProfile.getHeightCm())
                    .weightKg(userProfile.getWeightKg())
                    .wingspanCm(userProfile.getWingspanCm());
        }

        return builder.build();
    }
}
