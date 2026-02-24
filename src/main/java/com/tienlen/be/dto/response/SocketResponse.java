package com.tienlen.be.dto.response;

import com.tienlen.be.dto.request.SocketAction;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SocketResponse<T> {

    private SocketAction action;
    private T data;
    private long timestamp;

}