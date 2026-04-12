package com.tienlen.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class BotRoomAttackResponse {
    private List<Integer> botPlayedCards;
    private int botRemainingCards;
    private int userRemainingCards;
    private String currentTurn;
    private List<Integer> table;
    private boolean finished;
    private List<String> winners;
}

