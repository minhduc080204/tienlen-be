package com.tienlen.be.dto.response;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class TransactionResponse {
    private Long id;
    private Long userId;
    private Long amount;
    private String type;
    private String description;
    private LocalDateTime createdAt;
    private Long referenceId;
}
