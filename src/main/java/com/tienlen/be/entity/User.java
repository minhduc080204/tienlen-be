package com.tienlen.be.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String account;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, columnDefinition = "VARCHAR(50) DEFAULT 'USER'")
    private String role = "USER";

    @Column(nullable = false, columnDefinition = "VARCHAR(50) DEFAULT 'ACTIVE'")
    private String status = "ACTIVE";

    @Column(nullable = false, updatable = false, columnDefinition = "DATETIME DEFAULT CURRENT_TIMESTAMP")
    private java.time.LocalDateTime createdAt = java.time.LocalDateTime.now();

    @Column(nullable = true)
    private String avatarUrl;

    @Column(nullable = false, columnDefinition = "BIGINT DEFAULT 1000")
    private long tokenBalance;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean musicEnabled = true;

    @Column(nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean effectEnabled = true;

    @Column(nullable = true, columnDefinition = "BIGINT DEFAULT 1")
    private Long selectedCardSkinId = 1L;

    @Transient
    private Integer currentRoom;
}
