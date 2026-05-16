package com.tienlen.be.dto.response;

import com.tienlen.be.entity.User;
import lombok.Data;

@Data
public class UserSettingsResponse {
    private boolean musicEnabled;
    private boolean effectEnabled;
    private Long selectedCardSkinId;

    public UserSettingsResponse(User user) {
        this.musicEnabled = user.isMusicEnabled();
        this.effectEnabled = user.isEffectEnabled();
        this.selectedCardSkinId = user.getSelectedCardSkinId();
    }
}
