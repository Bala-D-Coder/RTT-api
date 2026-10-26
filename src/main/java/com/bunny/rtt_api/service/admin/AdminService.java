package com.bunny.rtt_api.service.admin;

import com.bunny.rtt_api.dto.request.CredentialIssueRequest;
import com.bunny.rtt_api.dto.request.CredentialUpdateRequest;
import com.bunny.rtt_api.dto.response.CredentialIssuedResponse;
import com.bunny.rtt_api.dto.response.CredentialRowResponse;
import com.bunny.rtt_api.dto.response.ReseedResponse;

import java.util.List;
import java.util.UUID;

public interface AdminService {

    CredentialIssuedResponse issueCredential(CredentialIssueRequest request);

    List<CredentialRowResponse> listCredentials();

    CredentialRowResponse updateCredential(UUID id, CredentialUpdateRequest request);

    void deleteCredential(UUID id);

    ReseedResponse reseed();
}
