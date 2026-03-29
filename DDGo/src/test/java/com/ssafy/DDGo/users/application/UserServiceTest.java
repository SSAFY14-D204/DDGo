package com.ssafy.DDGo.users.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.DDGo.global.auth.JwtTokenProvider;
import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import com.ssafy.DDGo.users.application.social.SocialAuthProvider;
import com.ssafy.DDGo.users.application.social.SocialAuthProviderRegistry;
import com.ssafy.DDGo.users.application.social.SocialUserProfile;
import com.ssafy.DDGo.users.dao.UserProfileRepository;
import com.ssafy.DDGo.users.dao.UserRepository;
import com.ssafy.DDGo.users.dao.UserSocialAccountRepository;
import com.ssafy.DDGo.users.domain.SocialProvider;
import com.ssafy.DDGo.users.domain.User;
import com.ssafy.DDGo.users.domain.UserProfile;
import com.ssafy.DDGo.users.domain.UserSocialAccount;
import com.ssafy.DDGo.users.dto.request.SocialLoginRequest;
import com.ssafy.DDGo.users.dto.request.TokenRefreshRequest;
import com.ssafy.DDGo.users.dto.request.UserNicknameUpdateRequest;
import com.ssafy.DDGo.users.dto.request.UserRegisterRequest;
import com.ssafy.DDGo.users.dto.response.DuplicateCheckResponse;
import com.ssafy.DDGo.users.dto.response.TokenRefreshResponse;
import com.ssafy.DDGo.users.dto.response.UserLoginResponse;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSocialAccountRepository userSocialAccountRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SocialAuthProviderRegistry socialAuthProviderRegistry;

    @Mock
    private SocialAuthProvider kakaoProvider;

    @Mock
    private SocialAuthProvider googleProvider;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("username available check returns true when not duplicated")
    void checkUsernameAvailability_whenAvailable_returnsTrue() {
        when(userRepository.countByEmailIncludingDeleted("user@example.com")).thenReturn(0L);
        when(userRepository.countByUsernameIncludingDeleted("user@example.com")).thenReturn(0L);

        DuplicateCheckResponse response = userService.checkUsernameAvailability("user@example.com");

        assertThat(response.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("username available check returns false when duplicated")
    void checkUsernameAvailability_whenDuplicated_returnsFalse() {
        when(userRepository.countByEmailIncludingDeleted("user@example.com")).thenReturn(1L);

        DuplicateCheckResponse response = userService.checkUsernameAvailability("user@example.com");

        assertThat(response.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("nickname available check returns true when not duplicated")
    void checkNicknameAvailability_whenAvailable_returnsTrue() {
        when(userRepository.countByNicknameIncludingDeleted("고운바람")).thenReturn(0L);

        DuplicateCheckResponse response = userService.checkNicknameAvailability("고운바람");

        assertThat(response.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("nickname available check returns false when duplicated")
    void checkNicknameAvailability_whenDuplicated_returnsFalse() {
        when(userRepository.countByNicknameIncludingDeleted("고운바람")).thenReturn(1L);

        DuplicateCheckResponse response = userService.checkNicknameAvailability("고운바람");

        assertThat(response.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("register without nickname generates Korean auto nickname")
    void registerUser_withoutNickname_generatesAutoNickname() {
        UserRegisterRequest request = registerRequest("user@example.com", "Secret12!", null);

        when(userRepository.countByEmailIncludingDeleted("user@example.com")).thenReturn(0L);
        when(passwordEncoder.encode("Secret12!")).thenReturn("encoded-password");
        when(userRepository.countByNicknameIncludingDeleted(anyString())).thenReturn(0L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.registerUser(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo("user@example.com");
        assertThat(savedUser.getEmail()).isEqualTo("user@example.com");
        assertThat(savedUser.getPassword()).isEqualTo("encoded-password");
        assertThat(savedUser.getNickname()).matches("^[가-힣]+$");
        assertThat(savedUser.getNickname()).hasSizeLessThanOrEqualTo(20);
    }

    @Test
    @DisplayName("register ignores provided nickname and still generates auto nickname")
    void registerUser_withExplicitNickname_ignoresInputAndGeneratesAutoNickname() {
        UserRegisterRequest request = registerRequest("user@example.com", "Secret12!", "직접고른별명");

        when(userRepository.countByEmailIncludingDeleted("user@example.com")).thenReturn(0L);
        when(passwordEncoder.encode("Secret12!")).thenReturn("encoded-password");
        when(userRepository.countByNicknameIncludingDeleted(anyString())).thenReturn(0L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.registerUser(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getNickname()).matches("^[가-힣]+$");
        assertThat(savedUser.getNickname()).isNotEqualTo("직접고른별명");
    }

    @Test
    @DisplayName("register retries auto nickname when first candidate collides")
    void registerUser_autoNicknameCollision_retriesUntilAvailable() {
        UserRegisterRequest request = registerRequest("user@example.com", "Secret12!", null);
        AtomicInteger nicknameChecks = new AtomicInteger();

        when(userRepository.countByEmailIncludingDeleted("user@example.com")).thenReturn(0L);
        when(passwordEncoder.encode("Secret12!")).thenReturn("encoded-password");
        when(userRepository.countByNicknameIncludingDeleted(anyString())).thenAnswer(invocation ->
                nicknameChecks.getAndIncrement() == 0 ? 1L : 0L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userService.registerUser(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getNickname()).matches("^[가-힣]+$");
        assertThat(nicknameChecks.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("manual nickname update changes nickname")
    void updateNickname_success_updatesNickname() {
        User user = user(1L, "user@example.com", "user@example.com", "encoded", "맑은하늘");
        UserNicknameUpdateRequest request = nicknameUpdateRequest("직접고른별명");

        when(userRepository.findByUsername("user@example.com")).thenReturn(Optional.of(user));
        when(userRepository.countByNicknameIncludingDeleted("직접고른별명")).thenReturn(0L);

        userService.updateNickname("user@example.com", request);

        assertThat(user.getNickname()).isEqualTo("직접고른별명");
    }

    @Test
    @DisplayName("Kakao social login creates new user with auto nickname")
    void socialLogin_kakaoNewUser_createsAccountAndIssuesTokens() {
        SocialLoginRequest request = socialRequest(SocialProvider.KAKAO, "kakao-access-token", null);
        SocialUserProfile profile = new SocialUserProfile(
                SocialProvider.KAKAO, "123", "kakao@example.com", true, "라이언");

        when(socialAuthProviderRegistry.get(SocialProvider.KAKAO)).thenReturn(kakaoProvider);
        when(kakaoProvider.getUserProfile(request)).thenReturn(profile);
        when(userSocialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "123"))
                .thenReturn(Optional.empty());
        when(userSocialAccountRepository.countByProviderAndProviderUserIdIncludingDeleted("KAKAO", "123"))
                .thenReturn(0L);
        when(userRepository.countByEmailIncludingDeleted("kakao@example.com")).thenReturn(0L);
        when(userRepository.countByUsernameIncludingDeleted("kakao_123")).thenReturn(0L);
        when(userRepository.countByNicknameIncludingDeleted(anyString())).thenReturn(0L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });
        when(jwtTokenProvider.createAccessToken(any(Authentication.class))).thenReturn("ddgo-access");
        when(jwtTokenProvider.createRefreshToken(any(Authentication.class))).thenReturn("ddgo-refresh");
        when(jwtTokenProvider.getExpiration("ddgo-refresh")).thenReturn(1000L);
        when(userProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        UserLoginResponse response = userService.socialLogin(request);

        assertThat(response.getAccessToken()).isEqualTo("ddgo-access");
        assertThat(response.getRefreshToken()).isEqualTo("ddgo-refresh");
        assertThat(response.getIsNewUser()).isTrue();
        assertThat(response.getNeedsOnboarding()).isTrue();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo("kakao_123");
        assertThat(savedUser.getEmail()).isEqualTo("kakao@example.com");
        assertThat(savedUser.getPassword()).isNull();
        assertThat(savedUser.getNickname()).matches("^[가-힣]+$");
        assertThat(savedUser.getNickname()).isNotEqualTo("라이언");

        ArgumentCaptor<UserSocialAccount> socialCaptor = ArgumentCaptor.forClass(UserSocialAccount.class);
        verify(userSocialAccountRepository).save(socialCaptor.capture());
        assertThat(socialCaptor.getValue().getProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(socialCaptor.getValue().getProviderUserId()).isEqualTo("123");

        verify(valueOperations).set("RT:kakao_123", "ddgo-refresh", 1000L, TimeUnit.MILLISECONDS);
    }

    @Test
    @DisplayName("Kakao social login falls back to auto nickname when provider nickname is missing")
    void socialLogin_missingProviderNickname_generatesAutoNickname() {
        SocialLoginRequest request = socialRequest(SocialProvider.KAKAO, "kakao-access-token", null);
        SocialUserProfile profile = new SocialUserProfile(
                SocialProvider.KAKAO, "123", "kakao@example.com", true, null);

        when(socialAuthProviderRegistry.get(SocialProvider.KAKAO)).thenReturn(kakaoProvider);
        when(kakaoProvider.getUserProfile(request)).thenReturn(profile);
        when(userSocialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "123"))
                .thenReturn(Optional.empty());
        when(userSocialAccountRepository.countByProviderAndProviderUserIdIncludingDeleted("KAKAO", "123"))
                .thenReturn(0L);
        when(userRepository.countByEmailIncludingDeleted("kakao@example.com")).thenReturn(0L);
        when(userRepository.countByUsernameIncludingDeleted("kakao_123")).thenReturn(0L);
        when(userRepository.countByNicknameIncludingDeleted(anyString())).thenReturn(0L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });
        when(jwtTokenProvider.createAccessToken(any(Authentication.class))).thenReturn("ddgo-access");
        when(jwtTokenProvider.createRefreshToken(any(Authentication.class))).thenReturn("ddgo-refresh");
        when(jwtTokenProvider.getExpiration("ddgo-refresh")).thenReturn(1000L);
        when(userProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        userService.socialLogin(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getNickname()).matches("^[가-힣]+$");
        assertThat(userCaptor.getValue().getNickname()).hasSizeLessThanOrEqualTo(20);
    }

    @Test
    @DisplayName("Kakao social login ignores provider nickname and still auto generates nickname")
    void socialLogin_providerNickname_isIgnoredAndAutoNicknameIsGenerated() {
        SocialLoginRequest request = socialRequest(SocialProvider.KAKAO, "kakao-access-token", null);
        SocialUserProfile profile = new SocialUserProfile(
                SocialProvider.KAKAO, "123", "kakao@example.com", true, "라이언");

        when(socialAuthProviderRegistry.get(SocialProvider.KAKAO)).thenReturn(kakaoProvider);
        when(kakaoProvider.getUserProfile(request)).thenReturn(profile);
        when(userSocialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "123"))
                .thenReturn(Optional.empty());
        when(userSocialAccountRepository.countByProviderAndProviderUserIdIncludingDeleted("KAKAO", "123"))
                .thenReturn(0L);
        when(userRepository.countByEmailIncludingDeleted("kakao@example.com")).thenReturn(0L);
        when(userRepository.countByUsernameIncludingDeleted("kakao_123")).thenReturn(0L);
        when(userRepository.countByNicknameIncludingDeleted(anyString())).thenReturn(0L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });
        when(jwtTokenProvider.createAccessToken(any(Authentication.class))).thenReturn("ddgo-access");
        when(jwtTokenProvider.createRefreshToken(any(Authentication.class))).thenReturn("ddgo-refresh");
        when(jwtTokenProvider.getExpiration("ddgo-refresh")).thenReturn(1000L);
        when(userProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        userService.socialLogin(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getNickname()).matches("^[가-힣]+$");
        assertThat(userCaptor.getValue().getNickname()).isNotEqualTo("라이언");
    }

    @Test
    @DisplayName("Kakao social login still auto generates nickname even with long provider nickname")
    void socialLogin_tooLongProviderNickname_stillGeneratesAutoNickname() {
        SocialLoginRequest request = socialRequest(SocialProvider.KAKAO, "kakao-access-token", null);
        SocialUserProfile profile = new SocialUserProfile(
                SocialProvider.KAKAO,
                "123",
                "kakao@example.com",
                true,
                "가나다라마바사아자차카타파하가나다라마바사아");

        when(socialAuthProviderRegistry.get(SocialProvider.KAKAO)).thenReturn(kakaoProvider);
        when(kakaoProvider.getUserProfile(request)).thenReturn(profile);
        when(userSocialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "123"))
                .thenReturn(Optional.empty());
        when(userSocialAccountRepository.countByProviderAndProviderUserIdIncludingDeleted("KAKAO", "123"))
                .thenReturn(0L);
        when(userRepository.countByEmailIncludingDeleted("kakao@example.com")).thenReturn(0L);
        when(userRepository.countByUsernameIncludingDeleted("kakao_123")).thenReturn(0L);
        when(userRepository.countByNicknameIncludingDeleted(anyString())).thenReturn(0L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 1L);
            return user;
        });
        when(jwtTokenProvider.createAccessToken(any(Authentication.class))).thenReturn("ddgo-access");
        when(jwtTokenProvider.createRefreshToken(any(Authentication.class))).thenReturn("ddgo-refresh");
        when(jwtTokenProvider.getExpiration("ddgo-refresh")).thenReturn(1000L);
        when(userProfileRepository.findByUserId(1L)).thenReturn(Optional.empty());

        userService.socialLogin(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getNickname()).matches("^[가-힣]+$");
        assertThat(userCaptor.getValue().getNickname()).hasSizeLessThanOrEqualTo(20);
        assertThat(userCaptor.getValue().getNickname()).isNotEqualTo("가나다라마바사아자차카타파하가나다라마바사아");
    }

    @Test
    @DisplayName("Kakao social login reuses existing account")
    void socialLogin_kakaoExistingUser_updatesSocialProfileAndIssuesTokens() {
        SocialLoginRequest request = socialRequest(SocialProvider.KAKAO, "kakao-access-token", null);
        SocialUserProfile profile = new SocialUserProfile(
                SocialProvider.KAKAO, "123", "kakao@example.com", true, "라이언");
        User user = user(1L, "kakao_123", null, null, "라이언");
        UserSocialAccount socialAccount = UserSocialAccount.builder()
                .user(user)
                .provider(SocialProvider.KAKAO)
                .providerUserId("123")
                .providerEmail(null)
                .emailVerified(false)
                .build();

        when(socialAuthProviderRegistry.get(SocialProvider.KAKAO)).thenReturn(kakaoProvider);
        when(kakaoProvider.getUserProfile(request)).thenReturn(profile);
        when(userSocialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "123"))
                .thenReturn(Optional.of(socialAccount));
        when(userRepository.countByEmailIncludingDeleted("kakao@example.com")).thenReturn(0L);
        when(jwtTokenProvider.createAccessToken(any(Authentication.class))).thenReturn("ddgo-access");
        when(jwtTokenProvider.createRefreshToken(any(Authentication.class))).thenReturn("ddgo-refresh");
        when(jwtTokenProvider.getExpiration("ddgo-refresh")).thenReturn(1000L);
        when(userProfileRepository.findByUserId(1L)).thenReturn(Optional.of(existingProfile(1L)));

        UserLoginResponse response = userService.socialLogin(request);

        assertThat(response.getIsNewUser()).isFalse();
        assertThat(response.getNeedsOnboarding()).isFalse();
        assertThat(socialAccount.getProviderEmail()).isEqualTo("kakao@example.com");
        assertThat(socialAccount.isEmailVerified()).isTrue();
        assertThat(user.getEmail()).isEqualTo("kakao@example.com");
    }

    @Test
    @DisplayName("Withdrawn social account is blocked")
    void socialLogin_withdrawnSocialAccount_throwsException() {
        SocialLoginRequest request = socialRequest(SocialProvider.KAKAO, "kakao-access-token", null);
        SocialUserProfile profile = new SocialUserProfile(
                SocialProvider.KAKAO, "123", "kakao@example.com", true, "라이언");

        when(socialAuthProviderRegistry.get(SocialProvider.KAKAO)).thenReturn(kakaoProvider);
        when(kakaoProvider.getUserProfile(request)).thenReturn(profile);
        when(userSocialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "123"))
                .thenReturn(Optional.empty());
        when(userSocialAccountRepository.countByProviderAndProviderUserIdIncludingDeleted("KAKAO", "123"))
                .thenReturn(1L);

        assertThatThrownBy(() -> userService.socialLogin(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_WITHDRAWN);
    }

    @Test
    @DisplayName("Existing email account requires linking")
    void socialLogin_existingEmailAccount_requiresLinking() {
        SocialLoginRequest request = socialRequest(SocialProvider.KAKAO, "kakao-access-token", null);
        SocialUserProfile profile = new SocialUserProfile(
                SocialProvider.KAKAO, "123", "local@example.com", true, "라이언");

        when(socialAuthProviderRegistry.get(SocialProvider.KAKAO)).thenReturn(kakaoProvider);
        when(kakaoProvider.getUserProfile(request)).thenReturn(profile);
        when(userSocialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "123"))
                .thenReturn(Optional.empty());
        when(userSocialAccountRepository.countByProviderAndProviderUserIdIncludingDeleted("KAKAO", "123"))
                .thenReturn(0L);
        when(userRepository.countByEmailIncludingDeleted("local@example.com")).thenReturn(1L);
        when(userRepository.existsByEmail("local@example.com")).thenReturn(true);

        assertThatThrownBy(() -> userService.socialLogin(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.SOCIAL_ACCOUNT_LINK_REQUIRED);
    }

    @Test
    @DisplayName("Google social login creates new user")
    void socialLogin_googleNewUser_createsAccountAndIssuesTokens() {
        SocialLoginRequest request = socialRequest(SocialProvider.GOOGLE, null, "google-id-token");
        SocialUserProfile profile = new SocialUserProfile(
                SocialProvider.GOOGLE, "google-sub", "google@example.com", true, "구글사용자");

        when(socialAuthProviderRegistry.get(SocialProvider.GOOGLE)).thenReturn(googleProvider);
        when(googleProvider.getUserProfile(request)).thenReturn(profile);
        when(userSocialAccountRepository.findByProviderAndProviderUserId(SocialProvider.GOOGLE, "google-sub"))
                .thenReturn(Optional.empty());
        when(userSocialAccountRepository.countByProviderAndProviderUserIdIncludingDeleted("GOOGLE", "google-sub"))
                .thenReturn(0L);
        when(userRepository.countByEmailIncludingDeleted("google@example.com")).thenReturn(0L);
        when(userRepository.countByUsernameIncludingDeleted("google_google-sub")).thenReturn(0L);
        when(userRepository.countByNicknameIncludingDeleted(anyString())).thenReturn(0L);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            ReflectionTestUtils.setField(user, "id", 2L);
            return user;
        });
        when(jwtTokenProvider.createAccessToken(any(Authentication.class))).thenReturn("google-access");
        when(jwtTokenProvider.createRefreshToken(any(Authentication.class))).thenReturn("google-refresh");
        when(jwtTokenProvider.getExpiration("google-refresh")).thenReturn(1500L);
        when(userProfileRepository.findByUserId(2L)).thenReturn(Optional.empty());

        UserLoginResponse response = userService.socialLogin(request);

        assertThat(response.getAccessToken()).isEqualTo("google-access");
        assertThat(response.getRefreshToken()).isEqualTo("google-refresh");
        assertThat(response.getIsNewUser()).isTrue();

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getNickname()).matches("^[가-힣]+$");
        assertThat(userCaptor.getValue().getNickname()).isNotEqualTo("구글사용자");
    }

    @Test
    @DisplayName("Authenticated user can link social account")
    void linkSocialAccount_linksProviderToCurrentUser() {
        SocialLoginRequest request = socialRequest(SocialProvider.KAKAO, "kakao-access-token", null);
        SocialUserProfile profile = new SocialUserProfile(
                SocialProvider.KAKAO, "123", "local@example.com", true, "로컬유저");
        User user = user(10L, "local@example.com", "local@example.com", "encoded-password", "로컬유저");

        when(userRepository.findByUsername("local@example.com")).thenReturn(Optional.of(user));
        when(socialAuthProviderRegistry.get(SocialProvider.KAKAO)).thenReturn(kakaoProvider);
        when(kakaoProvider.getUserProfile(request)).thenReturn(profile);
        when(userSocialAccountRepository.findByProviderAndProviderUserId(SocialProvider.KAKAO, "123"))
                .thenReturn(Optional.empty());
        when(userSocialAccountRepository.countByProviderAndProviderUserIdIncludingDeleted("KAKAO", "123"))
                .thenReturn(0L);
        when(userSocialAccountRepository.findByUserIdAndProvider(10L, SocialProvider.KAKAO))
                .thenReturn(Optional.empty());

        userService.linkSocialAccount("local@example.com", request);

        ArgumentCaptor<UserSocialAccount> socialCaptor = ArgumentCaptor.forClass(UserSocialAccount.class);
        verify(userSocialAccountRepository).save(socialCaptor.capture());
        assertThat(socialCaptor.getValue().getUser()).isEqualTo(user);
        assertThat(socialCaptor.getValue().getProvider()).isEqualTo(SocialProvider.KAKAO);
        assertThat(socialCaptor.getValue().getProviderUserId()).isEqualTo("123");
    }

    @Test
    @DisplayName("Social account can refresh tokens")
    void reissueToken_socialAccount_reissuesTokens() {
        TokenRefreshRequest request = refreshRequest("refresh-token");
        User user = user(1L, "kakao_123", "kakao@example.com", null, "라이언");

        when(jwtTokenProvider.validateToken("refresh-token")).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken("refresh-token")).thenReturn("kakao_123");
        when(valueOperations.get("RT:kakao_123")).thenReturn("refresh-token");
        when(userRepository.findByUsername("kakao_123")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.createAccessToken(any(Authentication.class))).thenReturn("new-access");
        when(jwtTokenProvider.createRefreshToken(any(Authentication.class))).thenReturn("new-refresh");
        when(jwtTokenProvider.getExpiration("new-refresh")).thenReturn(2000L);

        TokenRefreshResponse response = userService.reissueToken(request);

        assertThat(response.getAccessToken()).isEqualTo("new-access");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh");
        verify(valueOperations).set("RT:kakao_123", "new-refresh", 2000L, TimeUnit.MILLISECONDS);
    }

    @Test
    @DisplayName("Social account uses same logout flow")
    void logout_socialAccount_blacklistsAccessToken() {
        when(jwtTokenProvider.validateToken("access-token")).thenReturn(true);
        when(jwtTokenProvider.getUsernameFromToken("access-token")).thenReturn("kakao_123");
        when(valueOperations.get("RT:kakao_123")).thenReturn("refresh-token");
        when(jwtTokenProvider.getExpiration("access-token")).thenReturn(3000L);

        userService.logout("access-token");

        verify(redisTemplate).delete("RT:kakao_123");
        verify(valueOperations).set("AT:access-token", "logout", 3000L, TimeUnit.MILLISECONDS);
    }

    private User user(Long id, String username, String email, String password, String nickname) {
        User user = User.builder()
                .username(username)
                .email(email)
                .password(password)
                .nickname(nickname)
                .build();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private UserProfile existingProfile(Long userId) {
        return UserProfile.builder()
                .userId(userId)
                .heightCm(170)
                .build();
    }

    private SocialLoginRequest socialRequest(SocialProvider provider, String accessToken, String idToken) {
        SocialLoginRequest request = new SocialLoginRequest();
        ReflectionTestUtils.setField(request, "provider", provider);
        ReflectionTestUtils.setField(request, "accessToken", accessToken);
        ReflectionTestUtils.setField(request, "idToken", idToken);
        return request;
    }

    private TokenRefreshRequest refreshRequest(String refreshToken) {
        TokenRefreshRequest request = new TokenRefreshRequest();
        ReflectionTestUtils.setField(request, "refreshToken", refreshToken);
        return request;
    }

    private UserRegisterRequest registerRequest(String username, String password, String nickname) {
        UserRegisterRequest request = new UserRegisterRequest();
        ReflectionTestUtils.setField(request, "username", username);
        ReflectionTestUtils.setField(request, "password", password);
        ReflectionTestUtils.setField(request, "nickname", nickname);
        return request;
    }

    private UserNicknameUpdateRequest nicknameUpdateRequest(String nickname) {
        UserNicknameUpdateRequest request = new UserNicknameUpdateRequest();
        ReflectionTestUtils.setField(request, "nickname", nickname);
        return request;
    }
}
