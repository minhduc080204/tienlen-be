package com.tienlen.be.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PlayerService {
    private final Map<Long, Integer> playerMap = new ConcurrentHashMap<>();

    public Integer getCurrentRoom(Long userId) {
        return playerMap.get(userId);
    }

    public void joinRoom(Long userId, int roomId) {
        playerMap.put(userId, roomId);
    }

    public void leaveRoom(Long userId) {
        playerMap.remove(userId);
    }
}
