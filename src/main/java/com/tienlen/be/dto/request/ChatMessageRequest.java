package com.tienlen.be.dto.request;

import lombok.Data;

@Data
public class ChatMessageRequest {
    private int roomId;
    private SocketAction action;   // CHAT
    private String content;
}
