package com.tienlen.be.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_nfts")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class UserNft {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private Long nftItemId;
    private String walletAddress;
    @Column(unique = true)
    private String txHash;
    private LocalDateTime createdAt;
}
