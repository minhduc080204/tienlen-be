package com.tienlen.be.dto.response;

import com.tienlen.be.model.Player;
import lombok.Data;

@Data
public class PlayerResponse {
    private int playerIndex;
    private UserResponse user;
    private boolean isBot;
    private int handSize;

    public static PlayerResponse from(Player player) {
        PlayerResponse pl = new PlayerResponse();
        pl.playerIndex = player.getSeatIndex();
        pl.user = player.getUser();
        pl.isBot = player.isBot();
        pl.handSize = player.getHand().size();
        return pl;
    }
}
