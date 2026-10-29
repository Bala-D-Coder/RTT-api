package com.bunny.rtt_api.exception;

public enum ErrorCode {
    UNAUTHORIZED("unauthorized"),
    FORBIDDEN("forbidden"),
    CREDENTIALS_EXPIRED("credentials_expired"),
    CREDENTIALS_DISABLED("credentials_disabled"),
    INVALID_CREDENTIALS("invalid_credentials"),
    ACCOUNT_LOCKED("account_locked"),
    ACCOUNT_DISABLED("account_disabled"),
    RATE_LIMITED("rate_limited"),
    NOT_FOUND("not_found"),
    USER_NOT_FOUND("user_not_found"),
    NOTE_NOT_FOUND("note_not_found"),
    BLOCK_NOT_FOUND("block_not_found"),
    TAG_NOT_FOUND("tag_not_found"),
    REMINDER_NOT_FOUND("reminder_not_found"),
    NOTE_NOT_IN_BIN("note_not_in_bin"),
    VALIDATION_ERROR("validation_error"),
    BAD_FRAME("bad_frame"),
    OVERLOADED("overloaded"),
    INTERNAL_ERROR("internal_error");

    private final String code;

    ErrorCode(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String toString() {
        return code;
    }
}
