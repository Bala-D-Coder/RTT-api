package com.bunny.rtt_api.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record HireInvitationSubmitRequest(
        @NotBlank(message = "Company name is required")
        String company,

        @NotBlank(message = "Contact name is required")
        String contactName,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email address format")
        String email,

        @NotBlank(message = "Interview type is required")
        String interviewType,

        String roleTitle,
        String proposedTime,
        String message
) {}
