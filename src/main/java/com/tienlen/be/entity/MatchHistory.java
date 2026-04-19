package com.tienlen.be.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "match_history")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String roomType; // PVP, PVB

    @Column(nullable = false)
    private Long betToken;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column
    private LocalDateTime endTime;

    @Column(columnDefinition = "TEXT")
    private String winners; // JSON or comma separated IDs
}
