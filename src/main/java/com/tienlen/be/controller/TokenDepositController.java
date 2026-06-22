package com.tienlen.be.controller;

import com.tienlen.be.dto.request.TokenDepositVerifyRequest;
import com.tienlen.be.dto.response.UserResponse;
import com.tienlen.be.dto.response.admin.AdminTokenPackageDTO;
import com.tienlen.be.security.CurrentUser;
import com.tienlen.be.service.TokenDepositService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/tokens/deposit")
@RequiredArgsConstructor
public class TokenDepositController {

    private final TokenDepositService tokenDepositService;

    /**
     * GET /api/tokens/deposit/packages
     * Trả danh sách gói nạp token đang active (dành cho user).
     */
    @GetMapping("/packages")
    public ResponseEntity<List<AdminTokenPackageDTO>> getActivePackages() {
        return ResponseEntity.ok(tokenDepositService.getActivePackages());
    }

    /**
     * POST /api/tokens/deposit/verify
     * User gửi txHash sau khi chuyển MATIC để nhận token.
     * Body: { txHash, packageId, walletAddress }
     * Return: UserResponse với tokenBalance đã được cập nhật
     */
    @PostMapping("/verify")
    public ResponseEntity<UserResponse> verifyDeposit(
            @CurrentUser UserResponse user,
            @RequestBody TokenDepositVerifyRequest request) throws IOException {
        UserResponse updated = tokenDepositService.verifyAndDepositTokens(user.getId(), request);
        return ResponseEntity.ok(updated);
    }
}
