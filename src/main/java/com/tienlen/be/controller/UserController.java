package com.tienlen.be.controller;

import com.tienlen.be.dto.response.UserResponse;
import com.tienlen.be.security.CurrentUser;
import com.tienlen.be.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
