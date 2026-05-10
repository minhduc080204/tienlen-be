package com.tienlen.be.repository;

import com.tienlen.be.entity.UserNft;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserNftRepository extends JpaRepository<UserNft, Long> {
    boolean existsByTxHash(String txHash);
    List<UserNft> findByUserId(Long userId);
}
