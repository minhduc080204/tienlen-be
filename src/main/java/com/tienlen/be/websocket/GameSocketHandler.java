package com.tienlen.be.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienlen.be.dto.request.ChatMessageRequest;
import com.tienlen.be.dto.response.UserResponse;
import com.tienlen.be.model.Player;
import com.tienlen.be.model.Room;
import com.tienlen.be.service.GameService;
import com.tienlen.be.service.RoomService;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class GameSocketHandler extends TextWebSocketHandler {

    private final GameService gameService;
    private final RoomService roomService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        Room room = roomService.getRoom(getRoomIdFromSession(session));
        gameService.handleJoinRoom(session, room);
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        Room room = roomService.getRoom(getRoomIdFromSession(session));
        UserResponse user = (UserResponse) session.getAttributes().get("user");
        gameService.handleLeftRoom(room, user);
        log.error("SESSION CLOSED: {}, status: {}", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(
            WebSocketSession session,
            TextMessage message
    ) throws Exception {
        ChatMessageRequest request =
                mapper.readValue(message.getPayload(), ChatMessageRequest.class);

        Player player = (Player) session.getAttributes().get("player");
        Room room = roomService.getRoom(getRoomIdFromSession(session));

        if (player == null || room == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE);
            return;
        }

        switch (request.getAction()) {

            case CHAT -> {
                String content = request.getData().asText();
                gameService.handleChat(room, player, content);
            }

            case READY -> gameService.handleReady(room, player);

            case UNREADY -> gameService.handleUnReady(room, player);

            case ATTACK -> {
                List<String> cardIds =
                    mapper.convertValue(
                            request.getData(),
                            new TypeReference<List<String>>() {}
                    );

                gameService.handleAttack(room, player, cardIds);
            }

            case PASS -> {
                gameService.handlePass(room, player);
            }

            default -> log.warn("Unknown action {}", request.getAction());
        }
    }

    private int getRoomIdFromSession(WebSocketSession session) {
        String query = Objects.requireNonNull(session.getUri()).getQuery();
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
}
