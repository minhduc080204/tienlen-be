package com.tienlen.be.dto.request;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class ChatMessageRequest {
    private int roomId;
    private SocketAction action;   // CHAT
    private JsonNode data;
}
