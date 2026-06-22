package com.tienlen.be.controller;

import com.tienlen.be.dto.request.AvatarSelectCustomRequest;
import com.tienlen.be.dto.request.AvatarVerifyRequest;
import com.tienlen.be.dto.response.AvatarItemResponse;
import com.tienlen.be.dto.response.UserResponse;
import com.tienlen.be.security.CurrentUser;
import com.tienlen.be.service.AvatarService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/avatars")
@RequiredArgsConstructor
public class AvatarController {

    private final AvatarService avatarService;

    /**
     * Lấy danh sách avatar khả dụng (gồm cả miễn phí và trả phí, có đánh dấu xem đã sở hữu chưa).
     */
    @GetMapping
    public ResponseEntity<List<AvatarItemResponse>> getAvatars(@CurrentUser UserResponse user) {
        return ResponseEntity.ok(avatarService.getAvatarsForUser(user.getId()));
    }

    /**
     * Mua avatar bằng token balance cục bộ.
     */
    @PostMapping("/buy/{id}")
    public ResponseEntity<?> buyAvatarWithTokens(@CurrentUser UserResponse user, @PathVariable Long id) {
        avatarService.buyAvatarWithTokens(user.getId(), id);
        return ResponseEntity.ok().build();
    }

    /**
     * Xác thực giao dịch blockchain MATIC và mở khóa avatar cho user (tương tự skin lá bài).
     */
    @PostMapping("/verify-transfer")
    public ResponseEntity<?> verifyTransfer(@CurrentUser UserResponse user, @RequestBody AvatarVerifyRequest request)
            throws IOException {
        avatarService.verifyBlockchainTransferAndUnlockAvatar(user.getId(), request);
        return ResponseEntity.ok().build();
    }

    /**
     * Chọn avatar từ danh sách đã sở hữu.
     */
    @PostMapping("/select/{id}")
    public ResponseEntity<?> selectAvatar(@CurrentUser UserResponse user, @PathVariable Long id) {
        avatarService.selectAvatar(user.getId(), id);
        return ResponseEntity.ok().build();
    }

    /**
     * Chọn avatar tùy biến (sinh bằng URL/seed). 
     * Backend sẽ tự động kiểm tra xem style đó có miễn phí hay không, nếu mất phí thì user phải sở hữu ít nhất 1 avatar cùng style.
     */
    @PostMapping("/select-custom")
    public ResponseEntity<?> selectCustomAvatar(@CurrentUser UserResponse user, @RequestBody AvatarSelectCustomRequest request) {
        avatarService.selectCustomAvatar(user.getId(), request.getSrcUrl());
        return ResponseEntity.ok().build();
    }
}
