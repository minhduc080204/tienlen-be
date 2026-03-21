package com.tienlen.be.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienlen.be.dto.payload.ChatPayload;
import com.tienlen.be.dto.payload.GameMessagePayload;
import com.tienlen.be.dto.request.GameMessageType;
import com.tienlen.be.dto.request.SocketAction;
import com.tienlen.be.dto.response.PlayerResponse;
import com.tienlen.be.dto.response.RoomStateResponse;
import com.tienlen.be.dto.response.SocketResponse;
import com.tienlen.be.dto.response.UserResponse;
import com.tienlen.be.model.Player;
import com.tienlen.be.model.Room;
import com.tienlen.be.model.RoomStatus;
import com.tienlen.be.model.Card;
import com.tienlen.be.model.MoveType;
import com.tienlen.be.model.RuleValidator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GameService {
    private final RoomService roomService;
    private final UserService userService;
    private final JwtService jwtService;
    private final ObjectMapper mapper;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    private String getToken(WebSocketSession session) {
        String query = Objects.requireNonNull(session.getUri()).getQuery();
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

    public void handleJoinRoom(WebSocketSession session, Room room) {
        UserResponse u = getUserFromToken(session);
        UserResponse user = new UserResponse(userService.getByUserId(u.getId()));

        Player player = new Player(user, room.getNewSeatIndex());

        room.addPlayer(player, session);

        session.getAttributes().put("player", player);
        session.getAttributes().put("user", user);

        RoomStateResponse snapshot = RoomStateResponse.from(room, PlayerResponse.from(player, true));

        sendSnapshotTo(room, player, SocketAction.SYNC_DATA, snapshot);

        broadcastEvent(room, SocketAction.JOIN_ROOM, PlayerResponse.from(player, false));
        log.info("User {} joined room {}", user.getId(), room.getRoomId());
    }

    public void handleLeftRoom(Room room, UserResponse user) {
        if (room == null || user == null)
            return;

        Player leavingPlayer = room.getPlayers().get(user.getId());
        int leavingSeat = leavingPlayer != null ? leavingPlayer.getSeatIndex() : -1;

        boolean isEmpty = room.removePlayerByUserId(user.getId());
        broadcastEvent(room, SocketAction.LEFT_ROOM, Map.of("userId", user.getId()));

        if (isEmpty) {
            roomService.deleteRoom(room.getRoomId());
            return;
        }

        if (room.getStatus() == RoomStatus.PLAYING) {
            if (room.getPlayers().size() < 2) {
                room.setStatus(RoomStatus.WAITING);
                room.cancelTurnTimer();
                room.getTable().clear();
                for (Player p : room.getPlayers().values()) {
                    p.setReady(false);
                    p.getHandCards().clear();
                    p.setPassed(false);
                }
                broadcastEvent(room, SocketAction.GAME_FINISHED, Map.of("reason", "Not enough players"));
            } else if (leavingSeat != -1 && room.getCurrentTurn() == leavingSeat) {
                // The player who left was the current turn
                room.cancelTurnTimer();
                synchronized (room) {
                    long unpassedCount = room.getPlayers().values().stream()
                            .filter(p -> !p.isPassed())
                            .count();

                    if (unpassedCount <= 1) {
                        Player trickWinner = room.getPlayers().values().stream()
                                .filter(p -> !p.isPassed())
                                .findFirst()
                                .orElse(room.getPlayers().values().iterator().next());

                        room.getTable().clear();
                        for (Player p : room.getPlayers().values()) {
                            p.setPassed(false);
                        }

                        room.setCurrentTurn(trickWinner.getSeatIndex());
                        if (trickWinner.getHandCards().isEmpty()) {
                            room.setCurrentTurn(room.findNextTurn());
                        }
                        broadcastEvent(room, SocketAction.ATTACK, Map.of("table", room.getTable()));
                    } else {
                        room.setCurrentTurn(room.findNextTurn());
                    }
                }
                startTurn(room);
            }
        }
    }

    public void handleChat(Room room, Player player, String content) {
        broadcastEvent(room, SocketAction.CHAT, new ChatPayload(player.getUser(), content));
    }

    public void handleReady(Room room, Player player) {
        if (room.getStatus() != RoomStatus.WAITING) {
            return;
        }

        player.setReady(true);

        broadcastEvent(room, SocketAction.READY, Map.of("userId", player.getUser().getId()));

        if (room.isReadyToStartCountdown()) {
            room.setStatus(RoomStatus.READY);

            broadcastEvent(room, SocketAction.START_COUNTDOWN, 5);

            ScheduledFuture<?> future = scheduler.schedule(() -> {
                if (room.isReadyToStartCountdown()) {
                    startGame(room);
                } else {
                    room.cancelCountdown();
                    room.setStatus(RoomStatus.WAITING);
                }
                room.setCountdownTask(null);

            }, 5, TimeUnit.SECONDS);

            room.setCountdownTask(future);
        }
    }

    public void handleUnReady(Room room, Player player) {
        player.setReady(false);
        room.setStatus(RoomStatus.WAITING);
        broadcastEvent(room, SocketAction.UNREADY, Map.of("userId", player.getUser().getId()));
    }

    private Card parseCard(String idStr) {
        int id = Integer.parseInt(idStr);
        return new Card(id / 10, id % 10);
    }

    private boolean isValidPlay(List<String> tableIds, List<String> playIds) {
        List<Card> playCards = playIds.stream().map(this::parseCard).collect(Collectors.toList());

        MoveType type = RuleValidator.detectMoveType(playCards);
        if (type == null)
            return false;
        if (tableIds == null || tableIds.isEmpty()) {
            return true;
        }

        List<Card> tableCards = tableIds.stream().map(this::parseCard).collect(Collectors.toList());
        return RuleValidator.canBeat(tableCards, playCards);
    }

    public void autoAttack(Room room, Player player) {
        List<Card> handCards = player.getHandCards();
        List<String> playIds = new ArrayList<>();
        playIds.add(String.valueOf(handCards.get(0).getId()));
        handleAttack(room, player, playIds);
    }

    public void handleAttack(Room room, Player player, List<String> cardIds) {
        if (room.getStatus() != RoomStatus.PLAYING)
            return;
        if (room.getCurrentTurn() != player.getSeatIndex()) {
            sendSnapshotTo(
                    room,
                    player,
                    SocketAction.GAME_MESSAGE,
                    new GameMessagePayload(GameMessageType.ERROR, "Không phải lượt của bạn"));
            return;
        }

        if (!isValidPlay(room.getTable(), cardIds)) {
            sendSnapshotTo(
                    room,
                    player,
                    SocketAction.GAME_MESSAGE,
                    new GameMessagePayload(GameMessageType.ERROR, "Không hợp lệ"));
            return;
        }

        List<Card> playerHand = player.getHandCards();
        List<String> handIds = playerHand.stream()
                .map(c -> String.valueOf(c.getId()))
                .collect(java.util.stream.Collectors.toList());

        for (String playedId : cardIds) {
            handIds.remove(playedId);
        }

        List<Card> newHand = handIds.stream().map(this::parseCard).collect(java.util.stream.Collectors.toList());
        player.setHandCards(newHand);

        room.setTable(cardIds);

        broadcastEvent(room, SocketAction.ATTACK, Map.of(
                "userId", player.getUser().getId(),
                "table", room.getTable(),
                "remainingCards", newHand.size()));

        RoomStateResponse snapshot = RoomStateResponse.from(room, PlayerResponse.from(player, true));
        sendSnapshotTo(room, player, SocketAction.SYNC_DATA, snapshot);

        if (newHand.isEmpty()) {
            if (!room.getWinners().contains(player.getUser().getId())) {
                room.getWinners().add(player.getUser().getId());
            }

            long remainingPlayers = room.getPlayers().values().stream()
                    .filter(p -> p.getHandCards() != null && !p.getHandCards().isEmpty())
                    .count();

            if (remainingPlayers <= 1) {
                Player lastPlayer = room.getPlayers().values().stream()
                        .filter(p -> p.getHandCards() != null && !p.getHandCards().isEmpty())
                        .findFirst()
                        .orElse(null);

                if (lastPlayer != null && !room.getWinners().contains(lastPlayer.getUser().getId())) {
                    room.getWinners().add(lastPlayer.getUser().getId());
                }

                List<Long> finalWinners = new java.util.ArrayList<>(room.getWinners());

                broadcastEvent(room, SocketAction.GAME_FINISHED, Map.of("winners", finalWinners));

                room.resetGame();

                for (Player p : room.getPlayers().values()) {
                    RoomStateResponse snap = RoomStateResponse.from(room, PlayerResponse.from(p, true));
                    sendSnapshotTo(room, p, SocketAction.SYNC_DATA, snap);
                }
                return;
            }
        }

        synchronized (room) {
            room.setCurrentTurn(room.findNextTurn());
        }

        startTurn(room);
    }

    public void handlePass(Room room, Player player) {
        if (room.getStatus() != RoomStatus.PLAYING) {
            return;
        }

        if (player == null || room.getCurrentTurn() != player.getSeatIndex()) {
            return;
        }

        player.setPassed(true);
        broadcastEvent(room, SocketAction.PASS, Map.of("userId", player.getUser().getId()));

        synchronized (room) {
            long unpassedCount = room.getPlayers().values().stream()
                    .filter(p -> !p.isPassed())
                    .count();

            if (unpassedCount <= 1) {
                Player trickWinner = room.getPlayers().values().stream()
                        .filter(p -> !p.isPassed())
                        .findFirst()
                        .orElse(player);

                room.getTable().clear();
                for (Player p : room.getPlayers().values()) {
                    p.setPassed(false);
                }

                room.setCurrentTurn(trickWinner.getSeatIndex());
                if (trickWinner.getHandCards().isEmpty()) {
                    room.setCurrentTurn(room.findNextTurn());
                }

                broadcastEvent(room, SocketAction.ATTACK, Map.of("table", room.getTable()));
            } else {
                room.setCurrentTurn(room.findNextTurn());
            }
        }

        startTurn(room);
    }

    private synchronized void startGame(Room room) {
        if (room.getStatus() != RoomStatus.READY) {
            return;
        }

        room.prepareGame();

        for (Player p : room.getPlayers().values()) {
            RoomStateResponse snapshot = RoomStateResponse.from(room, PlayerResponse.from(p, true));
            sendSnapshotTo(room, p, SocketAction.SYNC_DATA, snapshot);
        }

        broadcastEvent(room, SocketAction.START_GAME, null);

        startTurn(room);
    }

    private void startTurn(Room room) {
        room.cancelTurnTimer();
        log.info("CURRENT TURN IS: {}", room.getCurrentTurn());

        broadcastEvent(room, SocketAction.NEXT_TURN, Map.of(
                "currentTurn", room.getCurrentTurn(),
                "duration", Room.TIME_OF_TURN));

        scheduleNextTurn(room);
    }

    private void scheduleNextTurn(Room room) {
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            if (room.getStatus() != RoomStatus.PLAYING)
                return;
            try {
                synchronized (room) {
                    if (room.getTable().isEmpty()) {
                        autoAttack(room, room.getPlayerBySeatIndex(room.getCurrentTurn()));
                    } else {
                        handlePass(room, room.getPlayerBySeatIndex(room.getCurrentTurn()));
                    }
                }
            } catch (Exception e) {
                log.error("Turn timer crashed", e);
            }
        }, Room.TIME_OF_TURN, TimeUnit.SECONDS);
        room.setTurnTimerTask(future);
    }

    public void broadcastEvent(Room room, SocketAction action, Object payload) {
        SocketResponse<Object> response = new SocketResponse<>(
                action, payload, System.currentTimeMillis());
        String message = toJson(response);

        for (WebSocketSession session : room.getSessions().values()) {
            send(session, message);
        }
    }

    public void sendSnapshotTo(Room room, Player player, SocketAction action, Object payload) {
        WebSocketSession session = room.getSessions().get(player.getUser().getId());
        if (session == null || !session.isOpen())
            return;

        SocketResponse<Object> response = new SocketResponse<>(
                action, payload, System.currentTimeMillis());
        send(session, toJson(response));
    }

    private void send(WebSocketSession session, String message) {
        try {
            if (session != null && session.isOpen()) {
                session.sendMessage(new TextMessage(message));
            }
        } catch (Exception e) {
            log.warn("Send failed, removing closed session");
        }
    }

    private String toJson(Object object) {
        try {
            return mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("JSON parse error", e);
        }
    }
}