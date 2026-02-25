package com.tienlen.be.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienlen.be.dto.request.SocketAction;
import com.tienlen.be.dto.response.SocketResponse;
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
    private long betToken;
    private RoomStatus status;

    public Room (long betToken) {
        this.roomId = ROOM_ID_GENERATOR.getAndIncrement();
        this.status = RoomStatus.WAITING;
        this.currentTurn=0;
        this.betToken = betToken;
    }

    public void addPlayer(Player player, WebSocketSession session) {
        if(isFull()){
            throw new RuntimeException("Room full");
        }
        players.put(player.getUser().getId(), player);
        sessions.put(player.getUser().getId(), session);
    }

    public void removePlayerByUserId(Long userId) {

        Player removedPlayer = players.remove(userId);

        if (removedPlayer == null) {
            return;
        }

        // remove session
        WebSocketSession session = sessions.remove(userId);

        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (Exception e) {
                log.warn("Error closing session for user {}", userId);
            }
        }

        log.info("User {} left room {}", userId, roomId);

        // 🔥 broadcast cho người còn lại
        broadcastEvent(
                SocketAction.LEFT_ROOM,
                Map.of("userId", userId)
        );

        if (players.isEmpty()) {
            log.info("Room {} is empty", roomId);
            // cleanup nếu cần
        }
    }

    public int getSeatIndex(Player player) {
        return player.getSeatIndex();
    }

    public boolean isFull (){
        return players.size()>=maxPlayers;
    }

    public boolean isEnoughToken (long token){
        return token>=this.betToken;
    }

    public int getNumberPlayer (){
        return players.size();
    }

    public Collection<WebSocketSession> getSessions() {
        return sessions.values();
    }

    private String toJson(Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON parse error", e);
        }
    }

    private void send(WebSocketSession session, String message) {
        try {
            if (session == null || !session.isOpen()) {
                return;
            }

            session.sendMessage(new TextMessage(message));

        } catch (Exception e) {
            log.warn("Send failed, removing closed session");
        }
    }

    public void broadcastEvent(SocketAction action, Object payload) {

        SocketResponse<Object> response =
                new SocketResponse<>(
                        action,
                        payload,
                        System.currentTimeMillis()
                );

        String message = toJson(response);

        for (WebSocketSession session : sessions.values()) {
            send(session, message);
        }
    }

    /*
     * Send to single player
     */
    public void sendSnapshotTo(Player player,
                               SocketAction action,
                               Object payload) {

        WebSocketSession session = sessions.get(player.getUser().getId());

        if (session == null || !session.isOpen()) {
            return;
        }

        SocketResponse<Object> response =
                new SocketResponse<>(
                        action,
                        payload,
                        System.currentTimeMillis()
                );

        send(session, toJson(response));
    }


}
