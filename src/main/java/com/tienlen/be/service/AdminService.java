package com.tienlen.be.service;

import com.tienlen.be.dto.response.admin.DashboardStatsDTO;
import com.tienlen.be.repository.MatchHistoryRepository;
import com.tienlen.be.repository.NftItemRepository;
import com.tienlen.be.repository.TransactionRepository;
import com.tienlen.be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final NftItemRepository nftItemRepository;
    private final TransactionRepository transactionRepository;
    private final MatchHistoryRepository matchHistoryRepository;
    private final RoomService roomService;

    public DashboardStatsDTO getDashboardStats() {
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByStatus("ACTIVE");
        long totalNFTs = nftItemRepository.count();
        
        Long volumeMatic = transactionRepository.sumAmountByTypeInAndStatus(
                Arrays.asList("PURCHASE_NFT", "DEPOSIT"), "SUCCESS");
        double totalVolumeMatic = volumeMatic != null ? volumeMatic.doubleValue() : 0.0;

        long totalMatchesPlayed = matchHistoryRepository.count();
        long activeMatches = roomService.getRoomSize();

        return DashboardStatsDTO.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .totalNFTs(totalNFTs)
                .totalVolumeMatic(totalVolumeMatic)
                .totalMatchesPlayed(totalMatchesPlayed)
                .activeMatches(activeMatches)
                .build();
    }

    public java.util.List<com.tienlen.be.dto.response.admin.AdminUserDTO> getAllUsers() {
        return userRepository.findAll().stream().map(user -> 
            com.tienlen.be.dto.response.admin.AdminUserDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .tokenBalance(user.getTokenBalance())
                .role(user.getRole())
                .status(user.getStatus())
                .email(user.getAccount())
                .createdAt(user.getCreatedAt())
                .build()
        ).collect(java.util.stream.Collectors.toList());
    }

    public com.tienlen.be.dto.response.admin.AdminUserDTO updateUser(Long id, com.tienlen.be.dto.response.admin.AdminUserDTO dto) {
        com.tienlen.be.entity.User user = userRepository.findById(id).orElseThrow();
        if (dto.getRole() != null) user.setRole(dto.getRole());
        if (dto.getStatus() != null) user.setStatus(dto.getStatus());
        user.setTokenBalance(dto.getTokenBalance());
        userRepository.save(user);
        return dto;
    }

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    public java.util.List<com.tienlen.be.dto.response.admin.AdminNftDTO> getAllNfts() {
        return nftItemRepository.findAll().stream().map(this::mapToAdminNftDTO).collect(java.util.stream.Collectors.toList());
    }

    private com.tienlen.be.dto.response.admin.AdminNftDTO mapToAdminNftDTO(com.tienlen.be.entity.NftItem nft) {
        return com.tienlen.be.dto.response.admin.AdminNftDTO.builder()
                .id(nft.getId())
                .name(nft.getName())
                .priceMatic(nft.getPriceMatic() != null ? nft.getPriceMatic().toString() : "0.0")
                .sourceKey(nft.getId() != null ? "buckets/nft-items/objects/" + nft.getId() + "/back_card.svg" : "")
                .type(nft.getType())
                .description(nft.getDescription())
                .isDefault(nft.isDefaultItem())
                .createdAt(nft.getCreatedAt())
                .build();
    }

    public com.tienlen.be.dto.response.admin.AdminNftDTO addNft(com.tienlen.be.dto.response.admin.AdminNftDTO dto) {
        com.tienlen.be.entity.NftItem nft = new com.tienlen.be.entity.NftItem();
        nft.setId(dto.getId());
        nft.setName(dto.getName());
        nft.setPriceMatic(dto.getPriceMatic() != null ? new java.math.BigDecimal(dto.getPriceMatic()) : java.math.BigDecimal.ZERO);
        nft.setType(dto.getType() != null ? dto.getType() : "STANDARD");
        nft.setDescription(dto.getDescription());
        nft.setDefaultItem(dto.isDefault());
        nft.setActive(true);
        nft.setCreatedAt(java.time.LocalDateTime.now());
        nftItemRepository.save(nft);
        return mapToAdminNftDTO(nft);
    }

    public com.tienlen.be.dto.response.admin.AdminNftDTO updateNft(Long id, com.tienlen.be.dto.response.admin.AdminNftDTO dto) {
        com.tienlen.be.entity.NftItem nft = nftItemRepository.findById(id).orElseThrow();
        if (dto.getName() != null) nft.setName(dto.getName());
        if (dto.getPriceMatic() != null) nft.setPriceMatic(new java.math.BigDecimal(dto.getPriceMatic()));
        if (dto.getType() != null) nft.setType(dto.getType());
        if (dto.getDescription() != null) nft.setDescription(dto.getDescription());
        nft.setDefaultItem(dto.isDefault());
        nftItemRepository.save(nft);
        return mapToAdminNftDTO(nft);
    }

    public void deleteNft(Long id) {
        nftItemRepository.deleteById(id);
    }
}
