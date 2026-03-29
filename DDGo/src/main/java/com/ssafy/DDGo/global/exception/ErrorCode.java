package com.ssafy.DDGo.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "잘못된 입력값입니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "허용되지 않은 메서드입니다."),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C003", "서버 오류가 발생했습니다."),
    INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "C004", "잘못된 타입의 값입니다."),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다."),
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "U002", "이미 존재하는 사용자입니다."),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "U003", "비밀번호가 올바르지 않습니다."),

    // Auth
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "인증이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "만료된 토큰입니다."),
    SOCIAL_PROVIDER_NOT_SUPPORTED(HttpStatus.BAD_REQUEST, "A004", "지원하지 않는 소셜 제공자입니다."),
    SOCIAL_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "A005", "유효하지 않은 소셜 토큰입니다."),
    SOCIAL_ACCOUNT_LINK_REQUIRED(HttpStatus.CONFLICT, "A006", "기존 계정 연동이 필요합니다."),
    SOCIAL_ACCOUNT_WITHDRAWN(HttpStatus.CONFLICT, "A007", "탈퇴한 소셜 계정입니다."),
    SOCIAL_ACCOUNT_ALREADY_LINKED(HttpStatus.CONFLICT, "A008", "이미 다른 계정에 연동된 소셜 계정입니다."),

    // Challenge
    CHALLENGE_NOT_FOUND(HttpStatus.NOT_FOUND, "CH001", "Challenge Not Found"),
    CHALLENGE_ALREADY_CLOSED(HttpStatus.BAD_REQUEST, "CH002", "Challenge Already Closed"),
    CHALLENGE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CH003", "Challenge Access Denied"),
    CHALLENGE_SUMMARY_NOT_FOUND(HttpStatus.NOT_FOUND, "CH004", "Challenge Summary Not Found"),

    // Attempt
    ATTEMPT_NOT_FOUND(HttpStatus.NOT_FOUND, "AT001", "Attempt Not Found"),
    INVALID_ATTEMPT_STATUS(HttpStatus.BAD_REQUEST, "AT002", "Invalid Attempt Status"),

    // Community
    POST_NOT_FOUND(HttpStatus.NOT_FOUND, "CM001", "Community Post Not Found"),
    POST_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CM002", "Community Post Access Denied"),
    COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "CM003", "Community Comment Not Found"),
    COMMENT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CM004", "Community Comment Access Denied"),
    INVALID_COMMENT_DEPTH(HttpStatus.BAD_REQUEST, "CM005", "Invalid Community Comment Depth"),
    INVALID_COMMUNITY_MEDIA(HttpStatus.BAD_REQUEST, "CM006", "Invalid Community Media");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
