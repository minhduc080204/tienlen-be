package com.tienlen.be.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienlen.be.dto.request.ChatMessageRequest;
import com.tienlen.be.dto.request.SocketAction;
import com.tienlen.be.dto.response.ChatMessageResponse;
import com.tienlen.be.dto.response.UserResponse;
import com.tienlen.be.model.Player;
import com.tienlen.be.model.Room;
import com.tienlen.be.service.JwtService;
import com.tienlen.be.service.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameSocketHandler extends TextWebSocketHandler {

    private final JwtService jwtService;
    private final RoomService roomService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UserResponse user = getUserFromToken(session);
        int roomId = getRoomId(session);

        Room room = roomService.getRoom(roomId);
        Player player = new Player(user, room.getNumberPlayer());

        room.addPlayer(player, session);

        session.getAttributes().put("player", player);
        session.getAttributes().put("room", room);
        System.out.println("Room: " + roomId + " | With: " +room.getNumberPlayer());
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message
    ) throws Exception {

        ChatMessageRequest request =
                mapper.readValue(message.getPayload(), ChatMessageRequest.class);

        Player player = (Player) session.getAttributes().get("player");
        Room room = (Room) session.getAttributes().get("room");

        if (player == null || room == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        switch (request.getAction()) {

            case CHAT -> handleChat(room, player, request.getContent());

//            case READY -> handleReady(room, player);

//            case PLAY_CARDS -> handlePlayCards(room, player, request);

            default -> log.warn("Unknown action {}", request.getAction());
        }
    }

    private void broadcast(Room room, Object payload) {
        try {
            String json = mapper.writeValueAsString(payload);

            for (WebSocketSession s : room.getSessions()) {
                if (s.isOpen()) {
                    s.sendMessage(new TextMessage(json));
                }
            }
        } catch (Exception e) {
            System.out.println("Broadcast error "+ e);
        }
    }

    private void handleChat(Room room, Player player, String content) {

        ChatMessageResponse response = new ChatMessageResponse(
                SocketAction.CHAT,
                player.getUser(),
                content,
                System.currentTimeMillis()
        );

        broadcast(room, response);
    }

    private String getToken(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query == null) {
            throw new RuntimeException("Missing query params");
        }

        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                return param.substring("token=".length());
            }
        }

        throw new RuntimeException("token not found");
    }

    private int getRoomId(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query == null) {
            throw new RuntimeException("Missing query params");
        }

        for (String param : query.split("&")) {
            if (param.startsWith("roomId=")) {
                return Integer.parseInt(param.split("=")[1]);
            }
        }

        throw new RuntimeException("roomId not found");
    }


    private UserResponse getUserFromToken(WebSocketSession session) {
        String token = getToken(session);

        if (!jwtService.validate(token)) {
            throw new RuntimeException("Invalid JWT token");
        }

        return jwtService.parse(token); // parse → UserResponse
    }

}
