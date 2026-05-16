package com.tienlen.be.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "nft_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NftItem {
    @Id
    private Long id; // Khớp với ID trong Smart Contract
    private String name;
    private String description;

    @Column(name = "price_matic", precision = 38, scale = 18)
    private BigDecimal priceMatic;
    private String promoteImageUrl;
    private String sourceUrl;
    private boolean active;
    private boolean defaultItem;
}
