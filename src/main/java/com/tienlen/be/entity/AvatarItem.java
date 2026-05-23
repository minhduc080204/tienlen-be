package com.tienlen.be.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "avatar_items")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvatarItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(nullable = false, length = 1024)
    private String srcUrl;

    @Column(name = "price_matic", precision = 38, scale = 18)
    private BigDecimal priceMatic;

    @Column(name = "price_tokens")
    private Long priceTokens;

    @Column(nullable = false)
    private String style; // e.g. adventurer, bottts, avataaars

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Builder.Default
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
