package com.tienlen.be.repository;

import com.tienlen.be.entity.NftItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NftItemRepository extends JpaRepository<NftItem, Long> {
    List<NftItem> findByActiveTrue();

    @Query("SELECT n FROM NftItem n WHERE n.active = true AND n.defaultItem = false " +
           "AND NOT EXISTS (SELECT u FROM UserNft u WHERE u.nftItemId = n.id AND u.userId = :userId)")
    List<NftItem> findAvailableForShop(@Param("userId") Long userId);

    @Query("SELECT n FROM NftItem n WHERE n.defaultItem = true " +
           "OR EXISTS (SELECT u FROM UserNft u WHERE u.nftItemId = n.id AND u.userId = :userId)")
    List<NftItem> findOwnedByUser(@Param("userId") Long userId);
}
