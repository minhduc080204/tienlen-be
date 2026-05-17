package com.tienlen.be.dto.response.admin;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class AdminMatchDTO {
    private String id;
    private String roomName;
    private String mode;
    private long betAmount;
    private String status;
    private int playersCount;
    private int maxPlayers;
    private String winnerName;
    private LocalDateTime createdAt;
}
