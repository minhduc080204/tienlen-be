package com.tienlen.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvatarItemResponse {
    private Long id;
    private String name;
    private String srcUrl;
    private BigDecimal priceMatic;
    private Long priceTokens;
    private String style;
    private boolean owned;
    private boolean active;
}
