package com.bunny.rtt_api.dto.request;

public record CredentialIssueRequest(
        String email,
        String password,
        String label,
        Integer daysValid
) {
    public CredentialIssueRequest(String label) {
        this(null, null, label, null);
    }
}
