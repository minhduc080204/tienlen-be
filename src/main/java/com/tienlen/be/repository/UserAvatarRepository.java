package com.tienlen.be.repository;

import com.tienlen.be.entity.UserAvatar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface UserAvatarRepository extends JpaRepository<UserAvatar, Long> {
    List<UserAvatar> findByUserId(Long userId);
    
    boolean existsByUserIdAndStyle(Long userId, String style);
    
    boolean existsByTxHash(String txHash);
}
