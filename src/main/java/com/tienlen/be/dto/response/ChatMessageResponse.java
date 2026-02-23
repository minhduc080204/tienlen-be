package com.tienlen.be.dto.response;

import com.tienlen.be.dto.request.SocketAction;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatMessageResponse {
    private SocketAction action;   // CHAT
    private UserResponse user;
    private String content;
    private long timestamp;
}