package com.tienlen.be.dto.payload;

import com.tienlen.be.model.Card;
import com.tienlen.be.model.Player;
import com.tienlen.be.model.RoomStatus;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class GameStatePayload {
    private RoomStatus status;
    private Long currentTurnUserId;
    private List<Player> players;
    private List<Card> myCards;
    private Long winnerUserId;
}