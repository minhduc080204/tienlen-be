package com.tienlen.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class BotRoomStartResponse {
    private long userTokenBalance;
    private List<Integer> userCards;
    private int botRemainingCards;
    private String currentTurn;
    private List<Integer> table;
}

