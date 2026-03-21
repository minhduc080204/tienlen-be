package com.tienlen.be.dto.payload;

import com.tienlen.be.dto.request.GameMessageType;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class GameMessagePayload {
    private GameMessageType type;
    private String message;
}
