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
public class AdminAvatarDTO {
    private Long id;
    private String name;
    private String srcUrl;
    private String priceMatic;
    private Long priceTokens;
    private String style;
    private boolean active;
    private LocalDateTime createdAt;
}
