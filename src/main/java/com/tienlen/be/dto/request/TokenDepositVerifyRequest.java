package com.tienlen.be.dto.request;

import lombok.Data;

@Data
public class TokenDepositVerifyRequest {
    private String txHash;
    private Long packageId;
    private String walletAddress;
}
