package com.tienlen.be.dto.response.admin;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class AdminNftDTO {
    private Long id;
    private String name;
    private String priceMatic;
    private String sourceKey;
    private String type;
    private String description;
    private boolean isDefault;
    private LocalDateTime createdAt;
}
