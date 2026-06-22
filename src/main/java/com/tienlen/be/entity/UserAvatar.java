package com.tienlen.be.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_avatars")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAvatar {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(name = "avatar_item_id", nullable = false)
    private Long avatarItemId;

    @Column(nullable = false)
    private String style;

    private String walletAddress;

    @Column(unique = true)
    private String txHash;

    @Builder.Default
    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
