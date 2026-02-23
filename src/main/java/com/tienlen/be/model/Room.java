package com.tienlen.be.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Setter
public class Room {
    public static int maxPlayers = 4;
    private static final AtomicInteger ROOM_ID_GENERATOR =
            new AtomicInteger(10000);
    private Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private Map<Long, Player> players = new ConcurrentHashMap<>();

    private int roomId;
    private int currentTurn;
    private RoomStatus status;

    public Room () {
        this.roomId = ROOM_ID_GENERATOR.getAndIncrement();
        this.status = RoomStatus.WAITING;
    }

    public void addPlayer(Player player, WebSocketSession session) {
        if(isFull()){
            throw new RuntimeException("Room full");
        }
        players.put(player.getUser().getId(), player);
        sessions.put(player.getUser().getId(), session);
    }

    public boolean isFull (){
        return players.size()>=maxPlayers;
    }

    public int getNumberPlayer (){
        return players.size();
    }

    public Collection<WebSocketSession> getSessions() {
        return sessions.values();
    }
}
