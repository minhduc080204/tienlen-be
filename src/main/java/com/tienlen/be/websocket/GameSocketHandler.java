package com.tienlen.be.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienlen.be.dto.request.ChatMessageRequest;
import com.tienlen.be.dto.request.SocketAction;
import com.tienlen.be.dto.response.ChatMessageResponse;
import com.tienlen.be.dto.response.UserResponse;
import com.tienlen.be.model.Player;
import com.tienlen.be.model.Room;
import com.tienlen.be.service.GameService;
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

    private final GameService gameService;
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        gameService.joinRoom(session);
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

            case CHAT -> gameService.chat(room, player, request.getContent());

//            case READY -> handleReady(room, player);

//            case PLAY_CARDS -> handlePlayCards(room, player, request);

            default -> log.warn("Unknown action {}", request.getAction());
        }
    }
}
