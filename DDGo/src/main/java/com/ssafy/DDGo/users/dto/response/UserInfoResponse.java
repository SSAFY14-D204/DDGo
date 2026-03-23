package com.ssafy.DDGo.users.dto.response;

import com.ssafy.DDGo.users.domain.User;
import com.ssafy.DDGo.users.domain.UserProfile;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "내 정보 조회 응답")
public class UserInfoResponse {

    @Schema(description = "사용자 ID", example = "1")
    private Long id;

    @Schema(description = "사용자 식별자", example = "kakao_123456789")
    private String username;

    @Schema(description = "사용자 이메일", example = "user@example.com")
    private String email;

    @Schema(description = "닉네임", example = "디디고")
    private String nickname;

    @Schema(description = "성별", example = "M", nullable = true)
    private String sex;

    @Schema(description = "키(cm)", example = "170", nullable = true)
    private Integer heightCm;

    @Schema(description = "몸무게(kg)", example = "60", nullable = true)
    private Integer weightKg;

    @Schema(description = "윙스팬(cm)", example = "172", nullable = true)
    private Integer wingspanCm;

    public static UserInfoResponse from(User user, UserProfile userProfile) {
        UserInfoResponseBuilder builder = UserInfoResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
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
