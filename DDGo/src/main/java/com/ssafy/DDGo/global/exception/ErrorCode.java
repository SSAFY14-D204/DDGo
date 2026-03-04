package com.ssafy.DDGo.global.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ErrorCode {

    // Common
    INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C001", "Invalid Input Value"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C002", "Method Not Allowed"),
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C003", "Server Error"),
    INVALID_TYPE_VALUE(HttpStatus.BAD_REQUEST, "C004", "Invalid Type Value"),

    // User
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "User Not Found"),
    USER_ALREADY_EXISTS(HttpStatus.CONFLICT, "U002", "User Already Exists"),
    INVALID_PASSWORD(HttpStatus.UNAUTHORIZED, "U003", "Invalid Password"),

    // Auth
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "Unauthorized"),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "Invalid Token"),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "Expired Token"),

    // Challenge
    CHALLENGE_NOT_FOUND(HttpStatus.NOT_FOUND, "CH001", "Challenge Not Found"),
    CHALLENGE_ALREADY_CLOSED(HttpStatus.BAD_REQUEST, "CH002", "Challenge Already Closed"),
    CHALLENGE_ACCESS_DENIED(HttpStatus.FORBIDDEN, "CH003", "Challenge Access Denied");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
