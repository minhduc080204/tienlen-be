package com.tienlen.be.controller;

import com.tienlen.be.dto.request.NftVerifyRequest;
import com.tienlen.be.dto.response.UserResponse;
import com.tienlen.be.entity.NftItem;
import com.tienlen.be.entity.UserNft;
import com.tienlen.be.repository.NftItemRepository;
import com.tienlen.be.repository.UserNftRepository;
import com.tienlen.be.security.CurrentUser;
import com.tienlen.be.service.NftService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/nfts")
@RequiredArgsConstructor
public class NftController {

    private final NftService nftService;
    private final NftItemRepository nftItemRepository;
    private final UserNftRepository userNftRepository;

    @PostMapping("/verify")
    public ResponseEntity<?> verifyNft(@CurrentUser UserResponse user, @RequestBody NftVerifyRequest request)
            throws IOException {
        nftService.verifyAndSaveNft(request, user.getId());
        return ResponseEntity.ok().build();
    }

    /**
     * Verify giao dịch chuyển MATIC từ ví user sang ví admin,
     * sau đó unlock item (lưu vào user_nfts).
     * Body: { txHash, itemId, walletAddress }
     */
    @PostMapping("/verify-transfer")
    public ResponseEntity<?> verifyTransfer(@CurrentUser UserResponse user, @RequestBody NftVerifyRequest request)
            throws IOException {
        nftService.verifyTransferAndUnlockItem(request, user.getId());
        return ResponseEntity.ok().build();
    }

    @GetMapping("")
    public ResponseEntity<List<NftItem>> getAllActiveNfts() {
        return ResponseEntity.ok(nftItemRepository.findByActiveTrue());
    }

    @GetMapping("/my")
    public ResponseEntity<List<UserNft>> getMyNfts(@CurrentUser UserResponse user) {
        return ResponseEntity.ok(userNftRepository.findByUserId(user.getId()));
    }
}
