package com.tienlen.be.dto.response;

import com.tienlen.be.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserResponse {
    private Long id;
    private String name;
    private long tokenBalance;

    public UserResponse(){}

    public UserResponse (User user) {
        this.id = user.getId();
        this.name = user.getName();
        this.tokenBalance = user.getTokenBalance();
    }
}