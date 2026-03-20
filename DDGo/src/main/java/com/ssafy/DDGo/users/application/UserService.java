package com.ssafy.DDGo.users.application;

import com.ssafy.DDGo.global.auth.JwtTokenProvider;
import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import com.ssafy.DDGo.users.dao.UserRepository;
import com.ssafy.DDGo.users.dao.UserProfileRepository;
import com.ssafy.DDGo.users.domain.PasswordPolicy;
import com.ssafy.DDGo.users.domain.User;
import com.ssafy.DDGo.users.domain.UserProfile;
import com.ssafy.DDGo.users.dto.request.UserLoginRequest;
import com.ssafy.DDGo.users.dto.response.UserLoginResponse;
import com.ssafy.DDGo.users.dto.request.UserRegisterRequest;
import com.ssafy.DDGo.users.dto.request.UserProfileUpdateRequest;
import com.ssafy.DDGo.users.dto.request.UserNicknameUpdateRequest;
import com.ssafy.DDGo.users.dto.request.UserPasswordUpdateRequest;
import com.ssafy.DDGo.users.dto.response.UserInfoResponse;
import com.ssafy.DDGo.users.dto.request.TokenRefreshRequest;
import com.ssafy.DDGo.users.dto.response.TokenRefreshResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StringRedisTemplate redisTemplate;

    @Transactional
    public void registerUser(UserRegisterRequest request) {
        String normalizedUsername = request.getUsername().trim().toLowerCase(java.util.Locale.ROOT);

        if (userRepository.countByUsernameIncludingDeleted(normalizedUsername) > 0) {
            if (userRepository.existsByUsername(normalizedUsername)) {
                throw new CustomException(ErrorCode.USER_ALREADY_EXISTS, "이미 존재하는 아이디입니다.");
            } else {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "탈퇴한 회원의 아이디는 재사용이 불가능합니다.");
            }
        }

        if (userRepository.countByNicknameIncludingDeleted(request.getNickname()) > 0) {
            if (userRepository.existsByNickname(request.getNickname())) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "이미 존재하는 닉네임입니다.");
            } else {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "탈퇴한 회원의 닉네임은 재사용이 불가능합니다.");
            }
        }

        PasswordPolicy.validatePasswordRules(normalizedUsername, request.getNickname(), request.getPassword());

        User user = User.builder()
                .username(normalizedUsername)
                .password(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .build();

        userRepository.save(user);
    }

    @Transactional
    public UserLoginResponse login(UserLoginRequest request, String clientIp) {
        String normalizedUsername = request.getUsername().trim().toLowerCase(java.util.Locale.ROOT);
        checkLoginLimit(normalizedUsername, clientIp);

        User user = userRepository.findByUsername(normalizedUsername).orElse(null);

        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            handleLoginFailure(normalizedUsername, clientIp);
            throw new CustomException(ErrorCode.INVALID_PASSWORD, "아이디 또는 비밀번호가 올바르지 않습니다.");
        }

        redisTemplate.delete("LOGIN_FAIL:" + normalizedUsername + ":" + clientIp);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getUsername(), "", Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        String accessToken = jwtTokenProvider.createAccessToken(authentication);
        String refreshToken = jwtTokenProvider.createRefreshToken(authentication);

        // Redis에 Refresh Token 저장 (TTL 설정)
        redisTemplate.opsForValue().set(
                "RT:" + user.getUsername(),
                refreshToken,
                jwtTokenProvider.getExpiration(refreshToken),
                TimeUnit.MILLISECONDS);

        return UserLoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    public UserInfoResponse getUserInfo(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "가입되지 않은 회원입니다."));

        UserProfile userProfile = userProfileRepository.findByUserId(user.getId()).orElse(null);

        return UserInfoResponse.from(user, userProfile);
    }

    @Transactional
    public void updateUserProfile(String username, UserProfileUpdateRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "가입되지 않은 회원입니다."));

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
        if (userRepository.countByNicknameIncludingDeleted(request.getNickname()) > 0) {
            if (userRepository.existsByNickname(request.getNickname())) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "이미 존재하는 닉네임입니다.");
            } else {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "탈퇴한 회원의 닉네임은 재사용이 불가능합니다.");
            }
        }

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "가입되지 않은 회원입니다."));

        user.updateNickname(request.getNickname());
    }

    @Transactional
    public void deleteUser(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "가입되지 않은 회원입니다."));

        userRepository.delete(user);

        // 회원 탈퇴 시 Redis에 남은 Refresh Token(쓰레기 데이터)도 함께 삭제
        redisTemplate.delete("RT:" + username);
    }

    @Transactional
    public void updatePassword(String username, UserPasswordUpdateRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "가입되지 않은 회원입니다."));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new CustomException(ErrorCode.INVALID_PASSWORD, "기존 비밀번호가 일치하지 않습니다.");
        }

        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, PasswordPolicy.SAME_AS_OLD_MESSAGE);
        }

        PasswordPolicy.validatePasswordRules(user.getUsername(), user.getNickname(), request.getNewPassword());

        user.updatePassword(passwordEncoder.encode(request.getNewPassword()));

        // 기존 모든 기기에서의 세션(Refresh Token) 무효화
        redisTemplate.delete("RT:" + username);
    }

    @Transactional
    public TokenRefreshResponse reissueToken(TokenRefreshRequest request) {
        String refreshToken = request.getRefreshToken();

        // 1. Refresh Token 검증
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN, "유효하지 않은 Refresh Token 입니다.");
        }

        // 2. 토큰에서 사용자 정보 꺼내기
        String username = jwtTokenProvider.getUsernameFromToken(refreshToken);

        // 3. Redis에 저장된 Refresh Token과 비교
        String storedRefreshToken = redisTemplate.opsForValue().get("RT:" + username);
        if (storedRefreshToken == null || !storedRefreshToken.equals(refreshToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN, "유효하지 않거나 로그아웃된 Refresh Token 입니다.");
        }

        // 4. 실존하는 사용자인지 확인
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND, "가입되지 않은 회원입니다."));

        // 5. 새로운 Authentication 객체 생성
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                user.getUsername(), "", Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER")));

        // 6. 새로운 토큰 발급 및 Redis 갱신
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
        // 1. 토큰 유효성 검사
        if (!jwtTokenProvider.validateToken(accessToken)) {
            throw new CustomException(ErrorCode.INVALID_TOKEN, "유효하지 않은 Access Token 입니다.");
        }

        // 2. Access Token에서 username 추출
        String username = jwtTokenProvider.getUsernameFromToken(accessToken);

        // 3. Redis에서 해당 User의 Refresh Token 삭제
        if (redisTemplate.opsForValue().get("RT:" + username) != null) {
            redisTemplate.delete("RT:" + username);
        }

        // 4. 해당 Access Token을 블락리스트(Blacklist)로 등록
        Long expiration = jwtTokenProvider.getExpiration(accessToken);
        redisTemplate.opsForValue().set(
                "AT:" + accessToken,
                "logout",
                expiration,
                TimeUnit.MILLISECONDS);
    }

    private void handleLoginFailure(String username, String clientIp) {
        String userIpKey = "LOGIN_FAIL:" + username + ":" + clientIp;
        String ipKey = "LOGIN_FAIL_IP:" + clientIp;

        incrementFailCount(userIpKey, 15, TimeUnit.MINUTES);
        incrementFailCount(ipKey, 15, TimeUnit.MINUTES);
    }

    private void incrementFailCount(String key, long timeout, TimeUnit unit) {
        String val = redisTemplate.opsForValue().get(key);
        int count = val == null ? 1 : Integer.parseInt(val) + 1;
        redisTemplate.opsForValue().set(key, String.valueOf(count), timeout, unit);
    }

    private void checkLoginLimit(String username, String clientIp) {
        String ipKey = "LOGIN_FAIL_IP:" + clientIp;
        String ipVal = redisTemplate.opsForValue().get(ipKey);
        if (ipVal != null && Integer.parseInt(ipVal) >= 20) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "비정상적인 로그인 요청이 감지되어 IP 접속이 15분간 제한됩니다.");
        }

        String userIpKey = "LOGIN_FAIL:" + username + ":" + clientIp;
        String userIpVal = redisTemplate.opsForValue().get(userIpKey);
        if (userIpVal != null && Integer.parseInt(userIpVal) >= 5) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "5회 이상 로그인에 실패하여 임시 잠금 처리되었습니다. 15분 후 다시 시도해주세요.");
        }
    }
}
