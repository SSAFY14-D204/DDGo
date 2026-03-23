package com.ssafy.DDGo.users.application;

import com.ssafy.DDGo.global.config.PasswordResetProperties;
import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import com.ssafy.DDGo.users.dao.UserRepository;
import com.ssafy.DDGo.users.domain.PasswordPolicy;
import com.ssafy.DDGo.users.domain.User;
import com.ssafy.DDGo.users.dto.request.PasswordResetConfirmRequest;
import com.ssafy.DDGo.users.dto.request.PasswordResetMailRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserPasswordResetService {

    private static final String PASSWORD_RESET_TOKEN_PREFIX = "PW_RESET:";
    private static final String PASSWORD_RESET_REQUEST_PREFIX = "PW_RESET_REQ:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final PasswordResetMailService passwordResetMailService;
    private final PasswordResetProperties passwordResetProperties;

    @Transactional
    public void requestPasswordReset(PasswordResetMailRequest request) {
        String normalizedEmail = normalizeEmail(request.getEmail());

        if (isRequestCoolingDown(normalizedEmail)) {
            return;
        }

        markRequestCooldown(normalizedEmail);

        User user = userRepository.findByEmail(normalizedEmail).orElse(null);
        if (user == null || user.getPassword() == null) {
            return;
        }

        String token = generateResetToken();
        String tokenKey = PASSWORD_RESET_TOKEN_PREFIX + hashToken(token);

        redisTemplate.opsForValue().set(
                tokenKey,
                user.getUsername(),
                passwordResetProperties.getTokenTtlSeconds(),
                TimeUnit.SECONDS);

        passwordResetMailService.sendPasswordResetMail(normalizedEmail, token);
    }

    @Transactional
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        String tokenKey = PASSWORD_RESET_TOKEN_PREFIX + hashToken(request.getToken().trim());
        String username = redisTemplate.opsForValue().get(tokenKey);

        if (!StringUtils.hasText(username)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN, "유효하지 않거나 만료된 비밀번호 재설정 토큰입니다.");
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.INVALID_TOKEN, "유효하지 않은 비밀번호 재설정 요청입니다."));

        if (user.getPassword() == null) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "소셜 로그인 전용 계정은 비밀번호를 재설정할 수 없습니다.");
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, PasswordPolicy.SAME_AS_OLD_MESSAGE);
        }

        PasswordPolicy.validatePasswordRules(
                user.getEmail() != null ? user.getEmail() : user.getUsername(),
                user.getNickname(),
                request.getNewPassword());

        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));
        redisTemplate.delete("RT:" + username);
        redisTemplate.delete(tokenKey);
    }

    private boolean isRequestCoolingDown(String normalizedEmail) {
        return StringUtils.hasText(redisTemplate.opsForValue().get(PASSWORD_RESET_REQUEST_PREFIX + normalizedEmail));
    }

    private void markRequestCooldown(String normalizedEmail) {
        redisTemplate.opsForValue().set(
                PASSWORD_RESET_REQUEST_PREFIX + normalizedEmail,
                "1",
                passwordResetProperties.getRequestCooldownSeconds(),
                TimeUnit.SECONDS);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String generateResetToken() {
        byte[] buffer = new byte[32];
        SECURE_RANDOM.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] digest = messageDigest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new CustomException(ErrorCode.INTERNAL_SERVER_ERROR, "비밀번호 재설정 토큰을 처리할 수 없습니다.");
        }
    }
}
