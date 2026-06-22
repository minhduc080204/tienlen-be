package com.tienlen.be.dto.request;

import lombok.Data;

@Data
public class AvatarVerifyRequest {
    private String txHash;
    private Long itemId;
    private String walletAddress;
}
