package com.ssafy.DDGo.users.application;

import com.ssafy.DDGo.global.auth.JwtTokenProvider;
import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import com.ssafy.DDGo.users.application.social.SocialAuthProviderRegistry;
import com.ssafy.DDGo.users.application.social.SocialUserProfile;
import com.ssafy.DDGo.users.dao.UserProfileRepository;
import com.ssafy.DDGo.users.dao.UserRepository;
import com.ssafy.DDGo.users.dao.UserSocialAccountRepository;
import com.ssafy.DDGo.users.domain.PasswordPolicy;
import com.ssafy.DDGo.users.domain.User;
import com.ssafy.DDGo.users.domain.UserProfile;
import com.ssafy.DDGo.users.domain.UserSocialAccount;
import com.ssafy.DDGo.users.dto.request.SocialLoginRequest;
import com.ssafy.DDGo.users.dto.request.TokenRefreshRequest;
import com.ssafy.DDGo.users.dto.request.UserLoginRequest;
import com.ssafy.DDGo.users.dto.request.UserNicknameUpdateRequest;
import com.ssafy.DDGo.users.dto.request.UserPasswordUpdateRequest;
import com.ssafy.DDGo.users.dto.request.UserProfileUpdateRequest;
import com.ssafy.DDGo.users.dto.request.UserRegisterRequest;
import com.ssafy.DDGo.users.dto.response.TokenRefreshResponse;
import com.ssafy.DDGo.users.dto.response.UserInfoResponse;
import com.ssafy.DDGo.users.dto.response.UserLoginResponse;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private static final String ROLE_USER = "ROLE_USER";
    private static final int MAX_NICKNAME_LENGTH = 20;

    private final UserRepository userRepository;
    private final UserSocialAccountRepository userSocialAccountRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;
    private final SocialAuthProviderRegistry socialAuthProviderRegistry;

    @Transactional
    public void registerUser(UserRegisterRequest request) {
        String normalizedEmail = normalizeEmail(request.getUsername());
        validateLocalEmailAvailability(normalizedEmail);
        validateNicknameAvailability(request.getNickname());

        PasswordPolicy.validatePasswordRules(normalizedEmail, request.getNickname(), request.getPassword());

        User user = User.builder()
                .username(normalizedEmail)
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .build();

        userRepository.save(user);
    }

    @Transactional
    public UserLoginResponse login(UserLoginRequest request, String clientIp) {
        String normalizedEmail = normalizeEmail(request.getUsername());
        checkLoginLimit(normalizedEmail, clientIp);

        User user = userRepository.findByEmail(normalizedEmail)
                .or(() -> userRepository.findByUsername(normalizedEmail))
                .orElse(null);

        if (user == null) {
            handleLoginFailure(normalizedEmail, clientIp);
            throw new CustomException(ErrorCode.INVALID_PASSWORD, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        if (user.getPassword() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "이 계정은 소셜 로그인을 통해 로그인해야 합니다.");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            handleLoginFailure(normalizedEmail, clientIp);
            throw new CustomException(ErrorCode.INVALID_PASSWORD, "이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        redisTemplate.delete("LOGIN_FAIL:" + normalizedEmail + ":" + clientIp);
        return issueTokens(user, false);
    }

    @Transactional
    public UserLoginResponse socialLogin(SocialLoginRequest request) {
        SocialUserProfile socialUser = resolveSocialUser(request);
        return loginOrRegisterSocialUser(socialUser);
    }

    public UserInfoResponse getUserInfo(String username) {
        User user = getUserByUsername(username);
        UserProfile userProfile = userProfileRepository.findByUserId(user.getId()).orElse(null);
        return UserInfoResponse.from(user, userProfile);
    }

    @Transactional
    public void updateUserProfile(String username, UserProfileUpdateRequest request) {
        User user = getUserByUsername(username);

        UserProfile userProfile = userProfileRepository.findByUserId(user.getId())
                .orElse(UserProfile.builder()
                        .userId(user.getId())
                        .build());

        userProfile.updateProfile(request.getSex(), request.getHeightCm(), request.getWeightKg(),
                request.getWingspanCm());

        userProfileRepository.save(userProfile);
    }

    @Transactional
    public void updateNickname(String username, UserNicknameUpdateRequest request) {
        validateNicknameAvailability(request.getNickname());
        getUserByUsername(username).updateNickname(request.getNickname());
    }

    @Transactional
    public void linkSocialAccount(String username, SocialLoginRequest request) {
        User user = getUserByUsername(username);
        SocialUserProfile socialUser = resolveSocialUser(request);
        linkSocialAccount(user, socialUser);
    }

    @Transactional
    public void deleteUser(String username) {
        User user = getUserByUsername(username);
        userSocialAccountRepository.findAllByUserId(user.getId()).forEach(userSocialAccountRepository::delete);
        userRepository.delete(user);
        redisTemplate.delete("RT:" + username);
    }

    @Transactional
    public void updatePassword(String username, UserPasswordUpdateRequest request) {
        User user = getUserByUsername(username);

        if (user.getPassword() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "이 소셜 계정은 비밀번호 변경을 지원하지 않습니다.");
        }

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD, "현재 비밀번호가 일치하지 않습니다.");
        }

        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, PasswordPolicy.SAME_AS_OLD_MESSAGE);
        }

        PasswordPolicy.validatePasswordRules(
                user.getEmail() != null ? user.getEmail() : user.getUsername(),
                user.getNickname(),
                request.getNewPassword());

        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
        redisTemplate.delete("RT:" + username);
    }

    @Transactional
    public TokenRefreshResponse reissueToken(TokenRefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN, "유효하지 않은 리프레시 토큰입니다.");
        }

        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);
        String storedRefreshToken = redisTemplate.opsForValue().get("RT:" + username);
        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN, "리프레시 토큰이 없거나 이미 로그아웃되었습니다.");
        }

        User user = getUserByUsername(username);
        Authentication authentication = buildAuthentication(user);
        String newAccessToken = jwtTokenProvider.createAccessToken(authentication);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(authentication);

        redisTemplate.opsForValue().set(
                "RT:" + username,
                newRefreshToken,
                jwtTokenProvider.getExpiration(newRefreshToken),
                TimeUnit.MILLISECONDS);

        return TokenRefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    @Transactional
    public void logout(String accessToken) {
        if (!jwtTokenProvider.validateToken(accessToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN, "유효하지 않은 액세스 토큰입니다.");
        }

        String username = jwtTokenProvider.getUsernameFromToken(accessToken);

        if (redisTemplate.opsForValue().get("RT:" + username) != null) {
            redisTemplate.delete("RT:" + username);
        }

        Long expiration = jwtTokenProvider.getExpiration(accessToken);
        redisTemplate.opsForValue().set(
                "AT:" + accessToken,
                "logout",
                expiration,
                TimeUnit.MILLISECONDS);
    }

    private SocialUserProfile resolveSocialUser(SocialLoginRequest request) {
        if (request.getProvider() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "소셜 제공자는 필수입니다.");
        }

        SocialUserProfile socialUser = socialAuthProviderRegistry.get(request.getProvider()).getUserProfile(request);
        return new SocialUserProfile(
                socialUser.provider(),
                socialUser.providerUserId(),
                normalizeNullableEmail(socialUser.email()),
                socialUser.emailVerified(),
                socialUser.nickname());
    }

    private UserLoginResponse loginOrRegisterSocialUser(SocialUserProfile socialUser) {
        UserSocialAccount linkedAccount = userSocialAccountRepository
                .findByProviderAndProviderUserId(socialUser.provider(), socialUser.providerUserId())
                .orElse(null);

        if (linkedAccount != null) {
            syncSocialAccount(linkedAccount, socialUser);
            return issueTokens(linkedAccount.getUser(), false);
        }

        if (userSocialAccountRepository.countByProviderAndProviderUserIdIncludingDeleted(
                socialUser.provider().name(), socialUser.providerUserId()) > 0) {
            throw new CustomException(ErrorCode.SOCIAL_ACCOUNT_WITHDRAWN,
                    "탈퇴한 사용자의 소셜 계정이라 사용할 수 없습니다.");
        }

        validateSocialEmailAvailability(socialUser.email());

        User user = userRepository.save(User.builder()
                .username(generateSocialUsername(socialUser))
                .email(socialUser.email())
                .password(null)
                .nickname(generateAvailableNickname(socialUser.nickname()))
                .build());

        userSocialAccountRepository.save(UserSocialAccount.builder()
                .user(user)
                .provider(socialUser.provider())
                .providerUserId(socialUser.providerUserId())
                .providerEmail(socialUser.email())
                .emailVerified(socialUser.emailVerified())
                .build());

        return issueTokens(user, true);
    }

    private void linkSocialAccount(User user, SocialUserProfile socialUser) {
        UserSocialAccount linkedAccount = userSocialAccountRepository
                .findByProviderAndProviderUserId(socialUser.provider(), socialUser.providerUserId())
                .orElse(null);

        if (linkedAccount != null) {
            if (!linkedAccount.getUser().getId().equals(user.getId())) {
                throw new CustomException(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED,
                        "이미 다른 DDGo 사용자에 연동된 소셜 계정입니다.");
            }

            syncSocialAccount(linkedAccount, socialUser);
            return;
        }

        if (userSocialAccountRepository.countByProviderAndProviderUserIdIncludingDeleted(
                socialUser.provider().name(), socialUser.providerUserId()) > 0) {
            throw new CustomException(ErrorCode.SOCIAL_ACCOUNT_WITHDRAWN,
                    "탈퇴한 사용자의 소셜 계정이라 연동할 수 없습니다.");
        }

        UserSocialAccount existingProviderLink = userSocialAccountRepository
                .findByUserIdAndProvider(user.getId(), socialUser.provider())
                .orElse(null);

        if (existingProviderLink != null) {
            if (!existingProviderLink.getProviderUserId().equals(socialUser.providerUserId())) {
                throw new CustomException(ErrorCode.SOCIAL_ACCOUNT_ALREADY_LINKED,
                        "이미 동일한 제공자의 소셜 계정이 연동되어 있습니다.");
            }

            syncSocialAccount(existingProviderLink, socialUser);
            return;
        }

        userSocialAccountRepository.save(UserSocialAccount.builder()
                .user(user)
                .provider(socialUser.provider())
                .providerUserId(socialUser.providerUserId())
                .providerEmail(socialUser.email())
                .emailVerified(socialUser.emailVerified())
                .build());

        syncUserEmailIfPossible(user, socialUser.email());
    }

    private void syncSocialAccount(UserSocialAccount linkedAccount, SocialUserProfile socialUser) {
        linkedAccount.syncProfile(socialUser.email(), socialUser.emailVerified());
        linkedAccount.markLoggedIn();
        syncUserEmailIfPossible(linkedAccount.getUser(), socialUser.email());
    }

    private UserLoginResponse issueTokens(User user, boolean isNewUser) {
        Authentication authentication = buildAuthentication(user);
        String accessToken = jwtTokenProvider.createAccessToken(authentication);
        String refreshToken = jwtTokenProvider.createRefreshToken(authentication);

        redisTemplate.opsForValue().set(
                "RT:" + user.getUsername(),
                refreshToken,
                jwtTokenProvider.getExpiration(refreshToken),
                TimeUnit.MILLISECONDS);

        boolean needsOnboarding = userProfileRepository.findByUserId(user.getId()).isEmpty();

        return UserLoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .isNewUser(isNewUser)
                .needsOnboarding(needsOnboarding)
                .build();
    }

    private Authentication buildAuthentication(User user) {
        return new UsernamePasswordAuthenticationToken(
                user.getUsername(),
                "",
                Collections.singletonList(new SimpleGrantedAuthority(ROLE_USER)));
    }

    private User getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "사용자를 찾을 수 없습니다."));
    }

    private void validateLocalEmailAvailability(String email) {
        if (userRepository.countByEmailIncludingDeleted(email) == 0) {
            return;
        }

        if (userRepository.existsByEmail(email)) {
            throw new CustomException(ErrorCode.USER_ALREADY_EXISTS, "이미 사용 중인 이메일입니다.");
        }

        throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "탈퇴한 계정의 이메일이라 사용할 수 없습니다.");
    }

    private void validateSocialEmailAvailability(String email) {
        if (!StringUtils.hasText(email) || userRepository.countByEmailIncludingDeleted(email) == 0) {
            return;
        }

        if (userRepository.existsByEmail(email)) {
            throw new CustomException(ErrorCode.SOCIAL_ACCOUNT_LINK_REQUIRED,
                    "기존 DDGo 계정이 존재합니다. 먼저 로그인한 뒤 소셜 계정을 연동해 주세요.");
        }

        throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "탈퇴한 계정의 이메일이라 사용할 수 없습니다.");
    }

    private void validateNicknameAvailability(String nickname) {
        if (userRepository.countByNicknameIncludingDeleted(nickname) == 0) {
            return;
        }

        if (userRepository.existsByNickname(nickname)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "이미 사용 중인 닉네임입니다.");
        }

        throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "탈퇴한 계정의 닉네임이라 사용할 수 없습니다.");
    }

    private void syncUserEmailIfPossible(User user, String socialEmail) {
        if (!StringUtils.hasText(socialEmail) || StringUtils.hasText(user.getEmail())) {
            return;
        }

        if (userRepository.countByEmailIncludingDeleted(socialEmail) == 0) {
            user.updateEmail(socialEmail);
        }
    }

    private String generateSocialUsername(SocialUserProfile socialUser) {
        String candidate = socialUser.provider().name().toLowerCase(Locale.ROOT) + "_" + socialUser.providerUserId();
        if (userRepository.countByUsernameIncludingDeleted(candidate) > 0) {
            throw new CustomException(ErrorCode.SOCIAL_ACCOUNT_WITHDRAWN,
                    "탈퇴한 사용자의 소셜 계정이라 사용할 수 없습니다.");
        }
        return candidate;
    }

    private String generateAvailableNickname(String nicknameSeed) {
        String base = StringUtils.hasText(nicknameSeed) ? nicknameSeed.trim() : "DDGoUser";
        if (base.length() > MAX_NICKNAME_LENGTH) {
            base = base.substring(0, MAX_NICKNAME_LENGTH);
        }

        String candidate = base;
        int suffix = 1;
        while (userRepository.countByNicknameIncludingDeleted(candidate) > 0) {
            String suffixText = String.valueOf(suffix++);
            int maxBaseLength = Math.max(1, MAX_NICKNAME_LENGTH - suffixText.length());
            String trimmedBase = base.length() > maxBaseLength ? base.substring(0, maxBaseLength) : base;
            candidate = trimmedBase + suffixText;
        }
        return candidate;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNullableEmail(String email) {
        if (!StringUtils.hasText(email)) {
            return null;
        }
        return normalizeEmail(email);
    }

    private void handleLoginFailure(String username, String clientIp) {
        String userIpKey = "LOGIN_FAIL:" + username + ":" + clientIp;
        String ipKey = "LOGIN_FAIL_IP:" + clientIp;

        incrementFailCount(userIpKey, 15, TimeUnit.MINUTES);
        incrementFailCount(ipKey, 15, TimeUnit.MINUTES);
    }

    private void incrementFailCount(String key, long timeout, TimeUnit unit) {
        String value = redisTemplate.opsForValue().get(key);
        int count = value == null ? 1 : Integer.parseInt(value) + 1;
        redisTemplate.opsForValue().set(key, String.valueOf(count), timeout, unit);
    }

    private void checkLoginLimit(String username, String clientIp) {
        String ipKey = "LOGIN_FAIL_IP:" + clientIp;
        String ipValue = redisTemplate.opsForValue().get(ipKey);
        if (ipValue != null && Integer.parseInt(ipValue) >= 20) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "이 IP에서 로그인 실패가 너무 많이 발생했습니다. 15분 후 다시 시도해 주세요.");
        }

        String userIpKey = "LOGIN_FAIL:" + username + ":" + clientIp;
        String userIpValue = redisTemplate.opsForValue().get(userIpKey);
        if (userIpValue != null && Integer.parseInt(userIpValue) >= 5) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE,
                    "이 계정의 로그인 실패 횟수가 많아 15분 후 다시 시도할 수 있습니다.");
        }
    }
}
