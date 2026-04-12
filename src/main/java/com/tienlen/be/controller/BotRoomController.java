package com.tienlen.be.controller;

import com.tienlen.be.dto.request.BotRoomAttackRequest;
import com.tienlen.be.dto.request.BotRoomCreateRequest;
import com.tienlen.be.dto.response.BotRoomAttackResponse;
import com.tienlen.be.dto.response.BotRoomCreateResponse;
import com.tienlen.be.dto.response.BotRoomStartResponse;
import com.tienlen.be.dto.response.UserResponse;
import com.tienlen.be.security.CurrentUser;
import com.tienlen.be.service.BotRoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/room/bot")
@RequiredArgsConstructor
public class BotRoomController {
    private final BotRoomService botRoomService;

    @PostMapping("/create")
    public BotRoomCreateResponse create(
            @CurrentUser UserResponse user,
            @RequestBody BotRoomCreateRequest request
    ) {
        return botRoomService.create(user, request);
    }

    @PostMapping("/start")
    public BotRoomStartResponse start(@CurrentUser UserResponse user) {
        return botRoomService.start(user);
    }

    @PostMapping("/attack")
    public BotRoomAttackResponse attack(
            @CurrentUser UserResponse user,
            @RequestBody BotRoomAttackRequest request
    ) {
        return botRoomService.attack(user, request);
    }
}

