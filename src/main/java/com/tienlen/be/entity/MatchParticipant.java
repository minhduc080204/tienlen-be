package com.tienlen.be.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "match_participants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private MatchHistory match;

    @Column(nullable = false)
    private Long userId;

    @Column
    private String name;

    @Column(name = "`rank`")
    private Integer rank;

    @Column
    private Long tokenChange;
}
