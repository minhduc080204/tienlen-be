package com.tienlen.be.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienlen.be.dto.payload.ChatPayload;
import com.tienlen.be.dto.payload.ReadyPayload;
import com.tienlen.be.dto.request.SocketAction;
import com.tienlen.be.dto.response.ChatMessageResponse;
import com.tienlen.be.dto.response.SocketResponse;
import com.tienlen.be.dto.response.UserResponse;
import com.tienlen.be.model.Card;
import com.tienlen.be.model.Player;
import com.tienlen.be.model.Room;
import com.tienlen.be.model.RoomStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.message.SimpleMessage;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {
    private final RoomService roomService;
    private final JwtService jwtService;
    private final ObjectMapper mapper = new ObjectMapper();

    /*
     * Khi player connect
     */

    public void joinRoom(WebSocketSession session){
        UserResponse user = getUserFromToken(session);
        int roomId = getRoomIdFromSession(session);

        Room room = roomService.getRoom(roomId);
        Player player = new Player(user, room.getNumberPlayer());

        room.addPlayer(player, session);

        session.getAttributes().put("player", player);
        session.getAttributes().put("room", room);
        log.info("User {} joined room {}", user.getId(), room.getRoomId());
    }

    /*
     * Chat logic
     */
    public void chat(Room room,
                     Player player,
                     String content) {

        SocketResponse<ChatPayload> response =
                new SocketResponse<>(
                        SocketAction.CHAT,
                        new ChatPayload(player.getUser(), content),
                        System.currentTimeMillis()
                );

        room.broadcast(response);
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

    private int getRoomIdFromSession(WebSocketSession session) {
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

    public void ready(Room room, Player player) {

        if (room.getStatus() != RoomStatus.WAITING) {
            return;
        }

        player.setReady(true);



        SocketResponse<ReadyPayload> response =
                new SocketResponse<>(
                        SocketAction.CHAT,
                        new ReadyPayload(player.getUser().getId()),
                        System.currentTimeMillis()
                );

        room.broadcast(response);

        boolean allReady = room.getPlayers().values()
                .stream()
                .allMatch(Player::isReady);

        if (allReady && room.getPlayers().size() >= 2) {
//            startGame(room);
        }
    }

//    private void startGame(Room room) {
//
//        room.setStatus(RoomStatus.PLAYING);
//
//        List<Card> deck = createDeck();
//        dealCards(room, deck);
//
//        List<Long> userIds = new ArrayList<>(room.getPlayerMap().keySet());
//
//        Long firstTurn = userIds.get(new Random().nextInt(userIds.size()));
//
//        room.setCurrentTurnUserId(firstTurn);
//
//        broadcastGameState(room);
//    }
}