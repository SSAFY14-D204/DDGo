package com.ssafy.DDGo.users.domain;

import com.ssafy.DDGo.global.exception.CustomException;
import com.ssafy.DDGo.global.exception.ErrorCode;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PasswordPolicy {

    public static final String PASSWORD_PATTERN = "^[\\x21-\\x7E]{8,64}$";
    public static final String POLICY_MESSAGE = "비밀번호는 8~64자이며, 영문/숫자/특수문자 중 2종 이상을 포함해야 합니다.";
    public static final String SAME_AS_OLD_MESSAGE = "새 비밀번호는 기존 비밀번호와 다르게 입력해주세요.";

    private static final List<String> WEAK_PASSWORDS = List.of(
            "12345678", "password", "qwer1234", "abcd1234", "admin123", "qwerty",
            "123456789", "12341234", "password123", "11111111", "00000000",
            "qazwsx", "1234567", "asdfgh", "zxcvbnm", "password!", "admin1234",
            "123123123", "manager", "test1234", "welcome1", "1234567890"
    );

    public static void validatePasswordRules(String username, String nickname, String password) {
        String lowerPwd = password.toLowerCase(java.util.Locale.ROOT);
        String lowerId = username.toLowerCase(java.util.Locale.ROOT);

        // 1. 조합 검사 (최소 2종)
        int typeCount = 0;
        if (password.matches(".*[a-zA-Z].*")) typeCount++;
        if (password.matches(".*[0-9].*")) typeCount++;
        if (password.matches(".*[\\x21-\\x2F\\x3A-\\x40\\x5B-\\x60\\x7B-\\x7E].*")) typeCount++;
        if (typeCount < 2) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, POLICY_MESSAGE);
        }

        // 2. 아이디 및 이메일 앞부분(local-part) 금지
        if (lowerPwd.contains(lowerId)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "아이디를 비밀번호에 포함할 수 없습니다.");
        }
        
        String localPart = lowerId.contains("@") ? lowerId.substring(0, lowerId.indexOf("@")) : lowerId;
        if (localPart.length() >= 3 && lowerPwd.contains(localPart)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "이메일 앞부분을 비밀번호에 포함할 수 없습니다.");
        }

        // 3. 닉네임 금지
        if (nickname != null && !nickname.isBlank()) {
            if (lowerPwd.contains(nickname.toLowerCase(java.util.Locale.ROOT))) {
                throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "닉네임을 비밀번호에 포함할 수 없습니다.");
            }
        }

        // 4. 동일 문자 3회 연속 금지
        if (password.matches(".*(.)\\1{2,}.*")) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "동일한 문자를 3회 이상 연속으로 사용할 수 없습니다.");
        }

        // 5. 연속 패턴 4자 이상 불가 (abcd, 1234, qwer)
        if (hasConsecutiveSequence(lowerPwd)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "연속된 패턴(예: 1234, abcd, qwer)을 4자 이상 사용할 수 없습니다.");
        }

        // 6. 약한 비밀번호 통제
        if (WEAK_PASSWORDS.contains(lowerPwd)) {
            throw new CustomException(ErrorCode.INVALID_INPUT_VALUE, "너무 쉬운 비밀번호(password, 12345678 등)는 사용할 수 없습니다.");
        }
    }

    private static boolean hasConsecutiveSequence(String pwd) {
        if (pwd.contains("qwer") || pwd.contains("rewq")) return true;

        for (int i = 0; i < pwd.length() - 3; i++) {
            char c1 = pwd.charAt(i), c2 = pwd.charAt(i + 1), c3 = pwd.charAt(i + 2), c4 = pwd.charAt(i + 3);
            if (c1 + 1 == c2 && c2 + 1 == c3 && c3 + 1 == c4) {
                if (Character.isLetterOrDigit(c1)) return true;
            }
            if (c1 - 1 == c2 && c2 - 1 == c3 && c3 - 1 == c4) {
                if (Character.isLetterOrDigit(c1)) return true;
            }
        }
        return false;
    }
}
