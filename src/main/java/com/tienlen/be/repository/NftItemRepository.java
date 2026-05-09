package com.tienlen.be.repository;

import com.tienlen.be.entity.NftItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NftItemRepository extends JpaRepository<NftItem, Long> {
    List<NftItem> findByActiveTrue();
}
