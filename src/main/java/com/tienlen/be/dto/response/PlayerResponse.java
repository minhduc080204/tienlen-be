package com.tienlen.be.dto.response;

import com.tienlen.be.model.Card;
import com.tienlen.be.model.Player;
import lombok.Data;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Data
public class PlayerResponse {
    private int playerIndex;
    private UserResponse user;
    private boolean isBot;
    private boolean isReady;
    private int handSize;
    private List<Card> handCards = new CopyOnWriteArrayList<>();

    public static PlayerResponse from(Player player, boolean isMe) {
        PlayerResponse pl = new PlayerResponse();
        pl.playerIndex = player.getSeatIndex();
        pl.user = player.getUser();
        pl.isBot = player.isBot();
        pl.isReady = player.isReady();
        pl.handSize = player.getHandCards().size();

        if(isMe){
            pl.handCards = player.getHandCards();
        }else {
            pl.handCards = null;
        }
        return pl;
    }
}
