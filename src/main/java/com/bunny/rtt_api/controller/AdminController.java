package com.bunny.rtt_api.controller;

import com.bunny.rtt_api.dto.request.CredentialIssueRequest;
import com.bunny.rtt_api.dto.request.CredentialUpdateRequest;
import com.bunny.rtt_api.dto.response.CredentialIssuedResponse;
import com.bunny.rtt_api.dto.response.CredentialRowResponse;
import com.bunny.rtt_api.dto.response.ReseedResponse;
import com.bunny.rtt_api.service.admin.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "Credential management and demo workspace administration")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @PostMapping("/credentials")
    @Operation(summary = "Issue new demo credential pair with readable password")
    public ResponseEntity<CredentialIssuedResponse> issueCredential(
            @RequestBody(required = false) CredentialIssueRequest request
    ) {
        CredentialIssuedResponse response = adminService.issueCredential(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/credentials")
    @Operation(summary = "List all issued demo credentials (excluding super admin)")
    public ResponseEntity<List<CredentialRowResponse>> listCredentials() {
        List<CredentialRowResponse> credentials = adminService.listCredentials();
        return ResponseEntity.ok(credentials);
    }

    @PatchMapping("/credentials/{id}")
    @Operation(summary = "Update credential status: extend expiry by 24h or toggle disabled")
    public ResponseEntity<CredentialRowResponse> updateCredential(
            @PathVariable UUID id,
            @RequestBody(required = false) CredentialUpdateRequest request
    ) {
        CredentialRowResponse response = adminService.updateCredential(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/credentials/{id}")
    @Operation(summary = "Revoke credential account (workspace data remains intact)")
    public ResponseEntity<Void> deleteCredential(@PathVariable UUID id) {
        adminService.deleteCredential(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reseed")
    @Operation(summary = "Idempotently seed demo data without deleting existing content")
    public ResponseEntity<ReseedResponse> reseed() {
        ReseedResponse response = adminService.reseed();
        return ResponseEntity.ok(response);
    }
}
