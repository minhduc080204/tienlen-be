package com.tienlen.be.controller;

import com.tienlen.be.dto.request.UserSettingsRequest;
import com.tienlen.be.dto.response.UserResponse;
import com.tienlen.be.dto.response.UserSettingsResponse;
import com.tienlen.be.security.CurrentUser;
import com.tienlen.be.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMe(@CurrentUser UserResponse user) {
        return ResponseEntity.ok(new UserResponse(userService.getByUserId(user.getId())));
    }

    @GetMapping("/matches")
    public ResponseEntity<?> getMatchHistory(@CurrentUser UserResponse user) {
        return ResponseEntity.ok(userService.getMatchHistory(user.getId()));
    }

    @GetMapping("/transactions")
    public ResponseEntity<?> getTransactionHistory(@CurrentUser UserResponse user) {
        return ResponseEntity.ok(userService.getTransactionHistory(user.getId()));
    }

    @GetMapping("/settings")
    public ResponseEntity<UserSettingsResponse> getSettings(@CurrentUser UserResponse user) {
        return ResponseEntity.ok(userService.getSettings(user.getId()));
    }

    @PatchMapping("/settings")
    public ResponseEntity<UserSettingsResponse> saveSettings(
            @CurrentUser UserResponse user,
            @RequestBody UserSettingsRequest request) {
        return ResponseEntity.ok(userService.saveSettings(user.getId(), request));
    }
}
