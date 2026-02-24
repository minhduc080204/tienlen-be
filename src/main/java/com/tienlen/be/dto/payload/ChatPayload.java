package com.tienlen.be.dto.payload;

import com.tienlen.be.dto.response.UserResponse;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ChatPayload {
    private UserResponse user;
    private String content;
}