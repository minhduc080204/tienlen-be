package com.tienlen.be.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class QuickJoinResponse {
    private int roomId;
    private String wsUrl;
}