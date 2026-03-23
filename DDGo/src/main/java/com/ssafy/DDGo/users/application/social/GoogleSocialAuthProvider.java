package com.ssafy.DDGo.users.application.social;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import com.ssafy.DDGo.users.domain.SocialProvider;
import com.ssafy.DDGo.users.dto.request.SocialLoginRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class GoogleSocialAuthProvider implements SocialAuthProvider {

    private static final String GOOGLE_TOKEN_INFO_URL =
            "https://oauth2.googleapis.com/tokeninfo?id_token={idToken}";

    private final RestClient socialRestClient;
    private final String googleClientId;

    public GoogleSocialAuthProvider(
            @Qualifier("socialRestClient") RestClient socialRestClient,
            @Value("${social.google.client-id:}") String googleClientId) {
        this.socialRestClient = socialRestClient;
        this.googleClientId = googleClientId;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.GOOGLE;
    }

    @Override
    public SocialUserProfile getUserProfile(SocialLoginRequest request) {
        if (!StringUtils.hasText(request.getIdToken())) {
            throw new CustomException(ErrorCode.SOCIAL_TOKEN_INVALID, "구글 ID 토큰이 필요합니다.");
        }

        try {
            GoogleTokenInfoResponse response = socialRestClient.get()
                    .uri(GOOGLE_TOKEN_INFO_URL, request.getIdToken().trim())
                    .retrieve()
                    .body(GoogleTokenInfoResponse.class);

            if (response == null || !StringUtils.hasText(response.sub())) {
                throw new CustomException(ErrorCode.SOCIAL_TOKEN_INVALID, "구글 사용자 정보를 조회할 수 없습니다.");
            }

            if (StringUtils.hasText(googleClientId) && !googleClientId.equals(response.aud())) {
                throw new CustomException(ErrorCode.SOCIAL_TOKEN_INVALID, "구글 토큰의 audience가 일치하지 않습니다.");
            }

            return new SocialUserProfile(
                    SocialProvider.GOOGLE,
                    response.sub(),
                    response.email(),
                    Boolean.parseBoolean(response.emailVerified()),
                    response.name());
        } catch (RestClientException e) {
            throw new CustomException(ErrorCode.SOCIAL_TOKEN_INVALID, "유효하지 않은 구글 ID 토큰입니다.");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GoogleTokenInfoResponse(
            String aud,
            String sub,
            String email,
            @JsonProperty("email_verified") String emailVerified,
            String name) {
    }
}
