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

import java.util.List;
import java.util.Map;
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

    private UserResponse getUserFromToken(WebSocketSession session) {
        String token = getToken(session);

        if (!jwtService.validate(token)) {
            throw new RuntimeException("Invalid JWT token");
        }
        return jwtService.parse(token);
    }

    public void handleJoinRoom(WebSocketSession session, Room room){
        UserResponse u = getUserFromToken(session);
        UserResponse user = new UserResponse(userService.getByUserId(u.getId()));

        Player player = new Player(user, room.getNewSeatIndex());

        room.addPlayer(player, session);

        session.getAttributes().put("player", player);
        session.getAttributes().put("user", user);

        RoomStateResponse snapshot = RoomStateResponse.from(room, PlayerResponse.from(player, true));

        room.sendSnapshotTo(player, SocketAction.SYNC_DATA, snapshot);

        room.broadcastEvent(
                SocketAction.JOIN_ROOM,
                PlayerResponse.from(player, false)
        );
        log.info("User {} joined room {}", user.getId(), room.getRoomId());
    }

    public void handleLeftRoom(Room room, UserResponse user){
        if(room.removePlayerByUserId(user.getId())){
            roomService.deleteRoom(room.getRoomId());
        }
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
            Map.of("userId", player.getUser().getId())
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
                Map.of("userId", player.getUser().getId())
        );

    }

    public void handleAttack(Room room, Player player, List<String> cardIds){
        room.playerAttack(player, cardIds);
        room.broadcastEvent(
            SocketAction.ATTACK,
            Map.of("table", room.getTable())
        );

    }

    public void handlePass(Room room, Player player) {
        player.setReady(false);
        room.setStatus(RoomStatus.WAITING);

        room.broadcastEvent(
                SocketAction.PASS,
                Map.of("userId", player.getUser().getId())
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