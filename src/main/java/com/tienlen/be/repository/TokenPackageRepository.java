package com.tienlen.be.repository;

import com.tienlen.be.entity.TokenPackage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TokenPackageRepository extends JpaRepository<TokenPackage, Long> {
    List<TokenPackage> findByActiveTrueOrderByPriceMaticAsc();
}
