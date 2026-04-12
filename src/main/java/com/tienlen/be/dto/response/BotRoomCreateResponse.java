package com.tienlen.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BotRoomCreateResponse {
    private long betToken;
    private String botLevel;
}

