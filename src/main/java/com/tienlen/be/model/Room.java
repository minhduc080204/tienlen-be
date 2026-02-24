package com.tienlen.be.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Setter
@Slf4j
public class Room {
    public static int maxPlayers = 4;
    private static final AtomicInteger ROOM_ID_GENERATOR =
            new AtomicInteger(10000);

    private Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private Map<Long, Player> players = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

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

    public void broadcast(Object payload) {
        try {
            String json = mapper.writeValueAsString(payload);

            sessions.values().forEach(session -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage(json));
                    }
                } catch (Exception e) {
                    log.error("Send error", e);
                }
            });

        } catch (Exception e) {
            log.error("Broadcast error", e);
        }
    }

    /*
     * Send to single player
     */
    public void sendTo(Long userId, Object payload) {
        try {
            WebSocketSession session = sessions.get(userId);
            if (session == null || !session.isOpen()) return;

            String json = mapper.writeValueAsString(payload);
            session.sendMessage(new TextMessage(json));

        } catch (Exception e) {
            log.error("SendTo error", e);
        }
    }
}
