package com.tienlen.be.dto.response.admin;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class AdminUserDTO {
    private Long id;
    private String name;
    private String avatarUrl;
    private long tokenBalance;
    private String role;
    private String status;
    private String email;
    private LocalDateTime createdAt;
}
