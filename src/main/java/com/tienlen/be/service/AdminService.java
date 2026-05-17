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
}
