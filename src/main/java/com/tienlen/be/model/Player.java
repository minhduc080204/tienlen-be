package com.tienlen.be.model;

import com.tienlen.be.dto.response.UserResponse;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Getter
@Setter
public class Player {
    private int seatIndex;
    private UserResponse user;
    private List<Card> hand = new CopyOnWriteArrayList<>();
    private boolean isBot;
    private boolean isReady;

    public Player (UserResponse user, int seatIndex) {
        this.user = user;
        this.isBot= false;
        this.isReady=false;
        this.seatIndex = seatIndex;
    }
}
