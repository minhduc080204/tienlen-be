package com.tienlen.be.dto.request;

import lombok.Data;

@Data
public class RegisterRequest {
    private String account;
    private String name;
    private String password;
    private String rePassword;
}
