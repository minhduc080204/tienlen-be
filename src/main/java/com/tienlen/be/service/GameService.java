package com.tienlen.be.service;

import com.tienlen.be.dto.payload.ChatPayload;
import com.tienlen.be.dto.payload.ReadyPayload;
import com.tienlen.be.dto.request.SocketAction;
import com.tienlen.be.dto.response.PlayerResponse;
import com.tienlen.be.dto.response.RoomStateResponse;
import com.tienlen.be.dto.response.UserResponse;
import com.tienlen.be.model.Player;
import com.tienlen.be.model.Room;
import com.tienlen.be.model.RoomStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {
    private final RoomService roomService;
    private final UserService userService;
    private final JwtService jwtService;
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

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

        return jwtService.parse(token);
    }

    public void handleJoinRoom(WebSocketSession session){
        UserResponse u = getUserFromToken(session);
        UserResponse user = new UserResponse(userService.getByUserId(u.getId()));
        int roomId = getRoomIdFromSession(session);

        Room room = roomService.getRoom(roomId);
        Player player = new Player(user, room.getNumberPlayer());

        room.addPlayer(player, session);

        session.getAttributes().put("player", player);
        session.getAttributes().put("room", room);

        RoomStateResponse snapshot = RoomStateResponse.from(room, PlayerResponse.from(player, true));

        room.sendSnapshotTo(player, SocketAction.SYNC_DATA, snapshot);

        room.broadcastEvent(
                SocketAction.JOIN_ROOM,
                PlayerResponse.from(player, false)
        );
        log.info("User {} joined room {}", user.getId(), room.getRoomId());
    }

    public void handleLeftRoom(WebSocketSession session){
        UserResponse user = getUserFromToken(session);
        int roomId = getRoomIdFromSession(session);

        Room room = roomService.getRoom(roomId);

        room.removePlayerByUserId(user.getId());

        log.info("Session closed {}", session.getId());
    }
    /*
     * Chat logic
     */
    public void handleChat(
        Room room,
        Player player,
        String content
    ) {
        room.broadcastEvent(
            SocketAction.CHAT,
            new ChatPayload(player.getUser(), content)
        );
    }

    public void handleReady(Room room, Player player) {

        if (room.getStatus() != RoomStatus.WAITING) {
            return;
        }

        player.setReady(true);

        room.broadcastEvent(
                SocketAction.READY,
                new ReadyPayload(player.getUser().getId())
        );

        if (room.isReadyToStartCountdown()) {
            // 👉 Chuyển sang READY
            room.setStatus(RoomStatus.READY);

            // 👉 Thông báo client bắt đầu countdown 5s
            room.broadcastEvent(
                    SocketAction.START_COUNTDOWN,
                    5
            );

            ScheduledFuture<?> future = scheduler.schedule(() -> {

                if (room.isReadyToStartCountdown()) {
                    room.startGame();
                } else {
                    room.cancelCountdown();
                    room.setStatus(RoomStatus.WAITING);
                }

                // 👉 clear task sau khi chạy xong
                room.setCountdownTask(null);

            }, 5, TimeUnit.SECONDS);

            room.setCountdownTask(future);
        }
    }

    public void handleUnReady(Room room, Player player) {
        player.setReady(false);
        room.setStatus(RoomStatus.WAITING);

        room.broadcastEvent(
                SocketAction.UNREADY,
                new ReadyPayload(player.getUser().getId())
        );

    }



    private void startGame(Room room) {

        room.setStatus(RoomStatus.PLAYING);

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
        room.broadcastEvent(
                SocketAction.START_GAME,
                null
        );
    }
}