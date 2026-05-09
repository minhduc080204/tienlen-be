package com.tienlen.be.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "nft_items")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class NftItem {
    @Id
    private Long id; // Khớp với ID trong Smart Contract
    private String name;
    private String description;
    private BigDecimal priceMatic;
    private String imageUrl;
    private boolean active;
}
