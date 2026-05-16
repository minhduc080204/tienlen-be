package com.tienlen.be.dto.request;

import lombok.Data;

@Data
public class UserSettingsRequest {
    private Boolean musicEnabled;
    private Boolean effectEnabled;
    private Long selectedCardSkinId;
}
