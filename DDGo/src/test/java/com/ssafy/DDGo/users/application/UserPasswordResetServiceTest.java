package com.ssafy.DDGo.users.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.DDGo.global.config.PasswordResetProperties;
import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import com.ssafy.DDGo.users.dao.UserRepository;
import com.ssafy.DDGo.users.domain.User;
import com.ssafy.DDGo.users.dto.request.PasswordResetConfirmRequest;
import com.ssafy.DDGo.users.dto.request.PasswordResetMailRequest;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
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
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserPasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private PasswordResetMailService passwordResetMailService;

    @Mock
    private PasswordResetProperties passwordResetProperties;

    @InjectMocks
    private UserPasswordResetService userPasswordResetService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(passwordResetProperties.getTokenTtlSeconds()).thenReturn(900L);
        when(passwordResetProperties.getRequestCooldownSeconds()).thenReturn(60L);
    }

    @Test
    @DisplayName("로컬 계정은 비밀번호 재설정 토큰을 저장하고 메일을 보낸다")
    void requestPasswordReset_localUser_storesTokenAndSendsMail() {
        PasswordResetMailRequest request = new PasswordResetMailRequest();
        ReflectionTestUtils.setField(request, "email", "local@example.com");

        User user = User.builder()
                .username("local@example.com")
                .email("local@example.com")
                .password("encoded-password")
                .nickname("local")
                .build();

        when(valueOperations.get("PW_RESET_REQ:local@example.com")).thenReturn(null);
        when(userRepository.findByEmail("local@example.com")).thenReturn(Optional.of(user));

        userPasswordResetService.requestPasswordReset(request);

        verify(valueOperations).set("PW_RESET_REQ:local@example.com", "1", 60L, TimeUnit.SECONDS);
        verify(valueOperations).set(org.mockito.ArgumentMatchers.startsWith("PW_RESET:"), org.mockito.ArgumentMatchers.eq("local@example.com"), org.mockito.ArgumentMatchers.eq(900L), org.mockito.ArgumentMatchers.eq(TimeUnit.SECONDS));
        verify(passwordResetMailService).sendPasswordResetMail(anyString(), anyString());
    }

    @Test
    @DisplayName("소셜 전용 계정은 비밀번호 재설정 메일을 보내지 않는다")
    void requestPasswordReset_socialOnlyUser_ignoresRequest() {
        PasswordResetMailRequest request = new PasswordResetMailRequest();
        ReflectionTestUtils.setField(request, "email", "social@example.com");

        User socialUser = User.builder()
                .username("kakao_123")
                .email("social@example.com")
                .password(null)
                .nickname("social")
                .build();

        when(valueOperations.get("PW_RESET_REQ:social@example.com")).thenReturn(null);
        when(userRepository.findByEmail("social@example.com")).thenReturn(Optional.of(socialUser));

        userPasswordResetService.requestPasswordReset(request);

        verify(passwordResetMailService, never()).sendPasswordResetMail(anyString(), anyString());
    }

    @Test
    @DisplayName("유효한 토큰이면 비밀번호를 재설정하고 기존 리프레시 토큰을 무효화한다")
    void confirmPasswordReset_validToken_updatesPassword() {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest();
        ReflectionTestUtils.setField(request, "token", "reset-token");
        ReflectionTestUtils.setField(request, "newPassword", "NewPassword123!");

        User user = User.builder()
                .username("local@example.com")
                .email("local@example.com")
                .password("encoded-password")
                .nickname("local")
                .build();

        when(valueOperations.get(anyString())).thenReturn("local@example.com");
        when(userRepository.findByUsername("local@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("NewPassword123!", "encoded-password")).thenReturn(false);
        when(passwordEncoder.encode("NewPassword123!")).thenReturn("new-encoded-password");

        userPasswordResetService.confirmPasswordReset(request);

        ArgumentCaptor<String> tokenKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).get(tokenKeyCaptor.capture());
        verify(redisTemplate).delete("RT:local@example.com");
        verify(redisTemplate).delete(tokenKeyCaptor.getValue());
    }

    @Test
    @DisplayName("만료된 토큰이면 비밀번호 재설정에 실패한다")
    void confirmPasswordReset_expiredToken_throwsException() {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest();
        ReflectionTestUtils.setField(request, "token", "expired-token");
        ReflectionTestUtils.setField(request, "newPassword", "NewPassword123!");

        when(valueOperations.get(anyString())).thenReturn(null);

        assertThatThrownBy(() -> userPasswordResetService.confirmPasswordReset(request))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_TOKEN);
    }
}
