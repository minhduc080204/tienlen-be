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
import com.tienlen.be.entity.User;
import com.tienlen.be.exception.BadRequestException;
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
import java.util.HashMap;
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
        boolean wasPlaying = room.getStatus() == RoomStatus.PLAYING;

        boolean isEmpty = room.removePlayerByUserId(user.getId());
        broadcastEvent(room, SocketAction.LEFT_ROOM, Map.of("userId", user.getId()));

        if (wasPlaying) {
            if (room.getRoundParticipantIds().contains(user.getId())
                    && !room.getWinners().contains(user.getId())
                    && !room.getRoundLeaverIds().contains(user.getId())) {
                room.getRoundLeaverIds().add(user.getId());
            }

            if (room.getPlayers().size() < 2) {
                finishGame(room);
            } else if (leavingSeat != -1 && room.getCurrentTurn() == leavingSeat) {
                // The player who left was the current turn
                room.cancelTurnTimer();
                synchronized (room) {
                    if (room.getActivePlayersInRoundCount() <= 1) {
                        int winnerSeat = room.getLastAttackerSeatIndex();
                        if (winnerSeat == -1 || winnerSeat == leavingSeat) {
                            // If leaving player was the last attacker, find someone else who hasn't out_of_round
                            winnerSeat = room.getPlayers().values().stream()
                                    .filter(p -> !p.isOutOfRound())
                                    .map(Player::getSeatIndex)
                                    .findFirst()
                                    .orElse(room.getPlayers().values().iterator().next().getSeatIndex());
                        }
                        performRoundReset(room, winnerSeat);
                    } else {
                        room.setCurrentTurn(room.findNextTurn());
                    }
                }
                startTurn(room);
            }
        }

        if (isEmpty) {
            roomService.deleteRoom(room.getRoomId());
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
        room.setLastAttackerSeatIndex(player.getSeatIndex());

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
                finishGame(room, finalWinners);
                return;
            }
        }

        synchronized (room) {
            if (room.getActivePlayersInRoundCount() <= 1) {
                performRoundReset(room, player.getSeatIndex());
            } else {
                room.setCurrentTurn(room.findNextTurn());
            }
        }

        startTurn(room);
    }

    private void performRoundReset(Room room, int trickWinnerSeatIndex) {
        room.resetRound();
        room.setCurrentTurn(trickWinnerSeatIndex);

        Player trickWinner = room.getPlayerBySeatIndex(trickWinnerSeatIndex);
        if (trickWinner == null || trickWinner.getHandCards().isEmpty()) {
            room.setCurrentTurn(room.findNextTurn());
        }

        broadcastEvent(room, SocketAction.ATTACK, Map.of("table", room.getTable()));
    }

    public void handlePass(Room room, Player player) {
        if (room.getStatus() != RoomStatus.PLAYING) {
            return;
        }

        if (player == null || room.getCurrentTurn() != player.getSeatIndex()) {
            return;
        }

        if (room.getTable().isEmpty()) {
            sendSnapshotTo(
                    room,
                    player,
                    SocketAction.GAME_MESSAGE,
                    new GameMessagePayload(GameMessageType.ERROR, "Bạn không thể bỏ lượt khi bắt đầu vòng mới"));
            return;
        }

        player.setOutOfRound(true);
        broadcastEvent(room, SocketAction.PASS, Map.of("userId", player.getUser().getId()));

        synchronized (room) {
            if (room.getActivePlayersInRoundCount() <= 1) {
                int trickWinnerSeat = room.getLastAttackerSeatIndex();
                if (trickWinnerSeat == -1) {
                    // This happens if someone passes on an empty table (shouldn't happen in rules)
                    // or if the first player passes.
                    trickWinnerSeat = room.findNextTurn();
                }
                performRoundReset(room, trickWinnerSeat);
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

        if (!collectBetForRound(room)) {
            room.setStatus(RoomStatus.WAITING);
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

    boolean collectBetForRound(Room room) {
        long betToken = room.getBetToken();
        List<Player> participants = new ArrayList<>(room.getPlayers().values());
        if (participants.size() < 2) {
            return false;
        }

        List<User> users = new ArrayList<>();
        Map<Long, User> userById = new HashMap<>();
        List<Long> participantIds = new ArrayList<>();

        for (Player player : participants) {
            Long userId = player.getUser().getId();
            User user = userService.getByUserId(userId);
            if (user.getTokenBalance() < betToken) {
                sendSnapshotTo(
                        room,
                        player,
                        SocketAction.GAME_MESSAGE,
                        new GameMessagePayload(GameMessageType.ERROR, "Không đủ token để bắt đầu ván"));
                return false;
            }
            users.add(user);
            userById.put(userId, user);
            participantIds.add(userId);
        }

        for (User user : users) {
            user.setTokenBalance(user.getTokenBalance() - betToken);
        }
        userService.saveAll(users);

        for (Player player : participants) {
            User saved = userById.get(player.getUser().getId());
            player.getUser().setTokenBalance(saved.getTokenBalance());
        }

        room.setRoundParticipantIds(participantIds);
        room.getRoundLeaverIds().clear();
        room.setCurrentPot(betToken * participantIds.size());
        return true;
    }

    void settleRoundPot(Room room, List<Long> finalWinners) {
        if (room.getCurrentPot() <= 0 || finalWinners == null || finalWinners.isEmpty()) {
            return;
        }

        int participantCount = room.getRoundParticipantIds().size();
        if (participantCount < 2) {
            return;
        }

        List<Integer> percentages = getPayoutPercentages(participantCount);
        List<Long> rankedWinnerIds = finalWinners.stream()
                .filter(room.getRoundParticipantIds()::contains)
                .distinct()
                .toList();
        if (rankedWinnerIds.isEmpty()) {
            return;
        }

        int rankSize = Math.min(percentages.size(), rankedWinnerIds.size());
        long pot = room.getCurrentPot();
        long distributed = 0;
        Map<Long, Long> payoutByUserId = new HashMap<>();

        for (int i = 0; i < rankSize; i++) {
            long amount = (pot * percentages.get(i)) / 100;
            if (amount <= 0) {
                continue;
            }
            distributed += amount;
            payoutByUserId.merge(rankedWinnerIds.get(i), amount, Long::sum);
        }

        long remainder = pot - distributed;
        if (remainder > 0) {
            payoutByUserId.merge(rankedWinnerIds.get(0), remainder, Long::sum);
        }

        List<User> usersToSave = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : payoutByUserId.entrySet()) {
            User user = userService.getByUserId(entry.getKey());
            user.setTokenBalance(user.getTokenBalance() + entry.getValue());
            usersToSave.add(user);

            Player player = room.getPlayers().get(entry.getKey());
            if (player != null) {
                player.getUser().setTokenBalance(user.getTokenBalance());
            }
        }

        if (!usersToSave.isEmpty()) {
            userService.saveAll(usersToSave);
        }
    }

    private List<Integer> getPayoutPercentages(int participantCount) {
        return switch (participantCount) {
            case 2 -> List.of(100, 0);
            case 3 -> List.of(70, 30, 0);
            case 4 -> List.of(60, 30, 10, 0);
            default -> throw new BadRequestException("Số lượng người chơi không hợp lệ để chia thưởng");
        };
    }

    private void kickPlayersWithInsufficientToken(Room room) {
        List<Player> kickedPlayers = room.getPlayers().values().stream()
                .filter(player -> player.getUser().getTokenBalance() < room.getBetToken())
                .toList();

        for (Player kickedPlayer : kickedPlayers) {
            sendSnapshotTo(room, kickedPlayer, SocketAction.KICKED, new GameMessagePayload(
                GameMessageType.ERROR, "Bạn đã bị đá khỏi phòng vì không đủ token"
            ));
            handleLeftRoom(room, kickedPlayer.getUser());
        }
    }

    private void finishGame(Room room) {
        finishGame(room, buildFinalRanking(room));
    }

    private void finishGame(Room room, List<Long> winnersRanking) {
        List<Long> finalRanking = winnersRanking == null ? buildFinalRanking(room) : winnersRanking;
        if (finalRanking.size() < room.getRoundParticipantIds().size()) {
            finalRanking = buildFinalRanking(room);
        }

        settleRoundPot(room, finalRanking);
        broadcastEvent(room, SocketAction.GAME_FINISHED, Map.of("winners", finalRanking));

        room.resetGame();
        kickPlayersWithInsufficientToken(room);

        for (Player p : room.getPlayers().values()) {
            RoomStateResponse snap = RoomStateResponse.from(room, PlayerResponse.from(p, true));
            sendSnapshotTo(room, p, SocketAction.SYNC_DATA, snap);
        }
    }

    List<Long> buildFinalRanking(Room room) {
        List<Long> ranking = new ArrayList<>();

        for (Long winnerId : room.getWinners()) {
            if (!ranking.contains(winnerId)) {
                ranking.add(winnerId);
            }
        }

        for (Player player : room.getPlayers().values()) {
            Long userId = player.getUser().getId();
            if (room.getRoundParticipantIds().contains(userId) && !ranking.contains(userId)) {
                ranking.add(userId);
            }
        }

        for (Long leaverId : room.getRoundLeaverIds()) {
            if (!ranking.contains(leaverId)) {
                ranking.add(leaverId);
            }
        }

        for (Long participantId : room.getRoundParticipantIds()) {
            if (!ranking.contains(participantId)) {
                ranking.add(participantId);
            }
        }

        return ranking;
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
