package com.tienlen.be.dto.response.admin;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class AdminTransactionDTO {
    private String id;
    private String txHash;
    private String walletAddress;
    private double amount;
    private String type;
    private String status;
    private String userName;
    private LocalDateTime createdAt;
}
