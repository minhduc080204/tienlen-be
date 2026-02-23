package com.tienlen.be.repository;

import com.tienlen.be.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByAccount(String account);

    boolean existsByAccount(String account);

}
