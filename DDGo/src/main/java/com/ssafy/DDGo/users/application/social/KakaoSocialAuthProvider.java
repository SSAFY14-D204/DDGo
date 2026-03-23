package com.ssafy.DDGo.users.application.social;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import com.ssafy.DDGo.users.domain.SocialProvider;
import com.ssafy.DDGo.users.dto.request.SocialLoginRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KakaoSocialAuthProvider implements SocialAuthProvider {

    private static final String KAKAO_USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

    private final RestClient socialRestClient;

    public KakaoSocialAuthProvider(@Qualifier("socialRestClient") RestClient socialRestClient) {
        this.socialRestClient = socialRestClient;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.KAKAO;
    }

    @Override
    public SocialUserProfile getUserProfile(SocialLoginRequest request) {
        if (!StringUtils.hasText(request.getAccessToken())) {
            throw new CustomException(ErrorCode.SOCIAL_TOKEN_INVALID, "카카오 액세스 토큰이 필요합니다.");
        }

        try {
            KakaoUserInfoResponse response = socialRestClient.get()
                    .uri(KAKAO_USER_INFO_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + request.getAccessToken().trim())
                    .retrieve()
                    .body(KakaoUserInfoResponse.class);

            if (response == null || response.id() == null) {
                throw new CustomException(ErrorCode.SOCIAL_TOKEN_INVALID, "카카오 사용자 정보를 조회할 수 없습니다.");
            }

            KakaoAccount kakaoAccount = response.kakaoAccount();
            KakaoProfile profile = kakaoAccount != null ? kakaoAccount.profile() : null;

            return new SocialUserProfile(
                    SocialProvider.KAKAO,
                    String.valueOf(response.id()),
                    kakaoAccount != null ? kakaoAccount.email() : null,
                    kakaoAccount != null && Boolean.TRUE.equals(kakaoAccount.isEmailVerified()),
                    profile != null ? profile.nickname() : null);
        } catch (RestClientException e) {
            throw new CustomException(ErrorCode.SOCIAL_TOKEN_INVALID, "유효하지 않은 카카오 액세스 토큰입니다.");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoUserInfoResponse(
            Long id,
            @JsonProperty("kakao_account") KakaoAccount kakaoAccount) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoAccount(
            String email,
            @JsonProperty("is_email_verified") Boolean isEmailVerified,
            KakaoProfile profile) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record KakaoProfile(String nickname) {
    }
}
