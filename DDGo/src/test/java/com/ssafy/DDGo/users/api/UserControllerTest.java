package com.ssafy.DDGo.users.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.DDGo.global.auth.JwtTokenProvider;
import com.ssafy.DDGo.global.config.SecurityConfig;
import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import com.ssafy.DDGo.global.exception.GlobalExceptionHandler;
import com.ssafy.DDGo.users.application.UserService;
import com.ssafy.DDGo.users.domain.SocialProvider;
import com.ssafy.DDGo.users.dto.request.SocialLoginRequest;
import com.ssafy.DDGo.users.dto.response.UserLoginResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.mapping.JpaMetamodelMappingContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class})
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private StringRedisTemplate redisTemplate;

    @MockBean
    private JpaMetamodelMappingContext jpaMetamodelMappingContext;

    @Test
    @DisplayName("소셜 로그인 요청 시 DDGo 토큰을 반환한다")
    void socialLogin_returnsTokens() throws Exception {
        SocialLoginRequest request = socialRequest(SocialProvider.KAKAO, "kakao-access-token", null);
        UserLoginResponse response = UserLoginResponse.builder()
                .accessToken("ddgo-access")
                .refreshToken("ddgo-refresh")
                .isNewUser(true)
                .needsOnboarding(true)
                .build();

        when(userService.socialLogin(any(SocialLoginRequest.class))).thenReturn(response);

        mockMvc.perform(post("/v1/users/social/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("소셜 로그인에 성공했습니다."))
                .andExpect(jsonPath("$.data.accessToken").value("ddgo-access"))
                .andExpect(jsonPath("$.data.refreshToken").value("ddgo-refresh"))
                .andExpect(jsonPath("$.data.isNewUser").value(true))
                .andExpect(jsonPath("$.data.needsOnboarding").value(true));
    }

    @Test
    @DisplayName("소셜 로그인 실패 시 소셜 에러 코드를 반환한다")
    void socialLogin_returnsSocialErrorCode() throws Exception {
        SocialLoginRequest request = socialRequest(SocialProvider.KAKAO, "kakao-access-token", null);

        when(userService.socialLogin(any(SocialLoginRequest.class)))
                .thenThrow(new CustomException(ErrorCode.SOCIAL_ACCOUNT_LINK_REQUIRED, "계정 연동이 필요합니다."));

        mockMvc.perform(post("/v1/users/social/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("A006"))
                .andExpect(jsonPath("$.message").value("계정 연동이 필요합니다."));
    }

    @Test
    @DisplayName("소셜 연동 요청은 인증이 필요하다")
    void socialLink_requiresAuthentication() throws Exception {
        SocialLoginRequest request = socialRequest(SocialProvider.KAKAO, "kakao-access-token", null);

        mockMvc.perform(post("/v1/users/social/link")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("소셜 연동 요청 시 인증된 사용자 식별자를 사용한다")
    void socialLink_usesAuthenticatedUsername() throws Exception {
        SocialLoginRequest request = socialRequest(SocialProvider.GOOGLE, null, "google-id-token");

        mockMvc.perform(post("/v1/users/social/link")
                        .with(user("local@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("소셜 계정 연동이 완료되었습니다."));

        verify(userService).linkSocialAccount(eq("local@example.com"), any(SocialLoginRequest.class));
    }

    private SocialLoginRequest socialRequest(SocialProvider provider, String accessToken, String idToken) {
        SocialLoginRequest request = new SocialLoginRequest();
        ReflectionTestUtils.setField(request, "provider", provider);
        ReflectionTestUtils.setField(request, "accessToken", accessToken);
        ReflectionTestUtils.setField(request, "idToken", idToken);
        return request;
    }
}
