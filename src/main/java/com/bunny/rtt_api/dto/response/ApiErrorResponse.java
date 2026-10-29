package com.bunny.rtt_api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(ErrorDetail error) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorDetail(
            String code,
            String message,
            Object details
    ) {}

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(new ErrorDetail(code, message, null));
    }

    public static ApiErrorResponse of(String code, String message, Object details) {
        return new ApiErrorResponse(new ErrorDetail(code, message, details));
    }
}
