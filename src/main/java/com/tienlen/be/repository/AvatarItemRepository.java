package com.tienlen.be.repository;

import com.tienlen.be.entity.AvatarItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AvatarItemRepository extends JpaRepository<AvatarItem, Long> {
    List<AvatarItem> findByActiveTrue();
}
