package com.tienlen.be.dto.response.admin;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardStatsDTO {
    private long totalUsers;
    private long activeUsers;
    private long totalNFTs;
    private double totalVolumeMatic;
    private long totalMatchesPlayed;
    private long activeMatches;
}
