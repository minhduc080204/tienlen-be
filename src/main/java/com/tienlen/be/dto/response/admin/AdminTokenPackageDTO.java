package com.tienlen.be.dto.response.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTokenPackageDTO {
    private Long id;
    private String name;
    private String description;
    private String priceMatic;
    private Long tokenAmount;
    private boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
