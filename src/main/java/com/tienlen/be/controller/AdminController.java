package com.tienlen.be.controller;

import com.tienlen.be.dto.response.admin.DashboardStatsDTO;
import com.tienlen.be.service.AdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsDTO> getDashboardStats() {
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    // --- USERS API ---
    @GetMapping("/users")
    public ResponseEntity<java.util.List<com.tienlen.be.dto.response.admin.AdminUserDTO>> getAllUsers() {
        return ResponseEntity.ok(adminService.getAllUsers());
    }

    @org.springframework.web.bind.annotation.PutMapping("/users/{id}")
    public ResponseEntity<com.tienlen.be.dto.response.admin.AdminUserDTO> updateUser(
            @org.springframework.web.bind.annotation.PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestBody com.tienlen.be.dto.response.admin.AdminUserDTO dto) {
        return ResponseEntity.ok(adminService.updateUser(id, dto));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/users/{id}")
    public ResponseEntity<Void> deleteUser(@org.springframework.web.bind.annotation.PathVariable Long id) {
        adminService.deleteUser(id);
        return ResponseEntity.ok().build();
    }

    // --- NFTS API ---
    @GetMapping("/nfts")
    public ResponseEntity<java.util.List<com.tienlen.be.dto.response.admin.AdminNftDTO>> getAllNfts() {
        return ResponseEntity.ok(adminService.getAllNfts());
    }

    @org.springframework.web.bind.annotation.PostMapping("/nfts")
    public ResponseEntity<com.tienlen.be.dto.response.admin.AdminNftDTO> addNft(
            @org.springframework.web.bind.annotation.RequestBody com.tienlen.be.dto.response.admin.AdminNftDTO dto) {
        return ResponseEntity.ok(adminService.addNft(dto));
    }

    @org.springframework.web.bind.annotation.PutMapping("/nfts/{id}")
    public ResponseEntity<com.tienlen.be.dto.response.admin.AdminNftDTO> updateNft(
            @org.springframework.web.bind.annotation.PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestBody com.tienlen.be.dto.response.admin.AdminNftDTO dto) {
        return ResponseEntity.ok(adminService.updateNft(id, dto));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/nfts/{id}")
    public ResponseEntity<Void> deleteNft(@org.springframework.web.bind.annotation.PathVariable Long id) {
        adminService.deleteNft(id);
        return ResponseEntity.ok().build();
    }

    // --- MATCHES API ---
    @GetMapping("/matches")
    public ResponseEntity<java.util.List<com.tienlen.be.dto.response.admin.AdminMatchDTO>> getAllMatches() {
        return ResponseEntity.ok(adminService.getAllMatches());
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/matches/{id}")
    public ResponseEntity<Void> deleteMatch(@org.springframework.web.bind.annotation.PathVariable String id) {
        adminService.deleteMatch(id);
        return ResponseEntity.ok().build();
    }

    // --- TRANSACTIONS API ---
    @GetMapping("/transactions")
    public ResponseEntity<java.util.List<com.tienlen.be.dto.response.admin.AdminTransactionDTO>> getAllTransactions() {
        return ResponseEntity.ok(adminService.getAllTransactions());
    }

    @org.springframework.web.bind.annotation.PutMapping("/transactions/{id}")
    public ResponseEntity<com.tienlen.be.dto.response.admin.AdminTransactionDTO> updateTransaction(
            @org.springframework.web.bind.annotation.PathVariable String id,
            @org.springframework.web.bind.annotation.RequestBody com.tienlen.be.dto.response.admin.AdminTransactionDTO dto) {
        return ResponseEntity.ok(adminService.updateTransaction(id, dto));
    }

    @org.springframework.web.bind.annotation.DeleteMapping("/transactions/{id}")
    public ResponseEntity<Void> deleteTransaction(@org.springframework.web.bind.annotation.PathVariable String id) {
        adminService.deleteTransaction(id);
        return ResponseEntity.ok().build();
    }
}
