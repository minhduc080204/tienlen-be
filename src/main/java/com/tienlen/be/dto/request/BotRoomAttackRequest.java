package com.tienlen.be.dto.request;

import lombok.Data;

import java.util.List;

@Data
public class BotRoomAttackRequest {
    private List<Integer> cards;
}

