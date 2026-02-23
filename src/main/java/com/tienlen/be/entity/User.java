package com.tienlen.be.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.UniqueElements;

@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    @UniqueElements
    private String account;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String password;

    private Integer currentRoom;
}
