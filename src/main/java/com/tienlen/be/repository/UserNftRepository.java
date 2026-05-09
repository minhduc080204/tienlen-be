package com.tienlen.be.repository;

import com.tienlen.be.entity.UserNft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserNftRepository extends JpaRepository<UserNft, Long> {
}
