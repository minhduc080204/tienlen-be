package com.tienlen.be.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long amount; // positive for add, negative for subtract

    @Column(nullable = false)
    private String type; // GAME_BET, GAME_WIN, DEPOSIT, etc.

    @Column
    private String description;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private Long referenceId; // e.g., match_id
}
