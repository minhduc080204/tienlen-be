package com.tienlen.be.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienlen.be.dto.request.SocketAction;
import com.tienlen.be.dto.response.PlayerResponse;
import com.tienlen.be.dto.response.RoomStateResponse;
import com.tienlen.be.dto.response.SocketResponse;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Setter
@Slf4j
public class Room {
    public static int MAXPLAYERS = 4;
    public static int TIMEFORREADY = 5;
    public static int TIMEFORTURN = 15;
    private static final AtomicInteger ROOM_ID_GENERATOR =
            new AtomicInteger(10000);

    private Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private Map<Long, Player> players = new ConcurrentHashMap<>();
    private Map<Long, Card> lastPlayedCards = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private ScheduledFuture<?> countdownTask;
    private ScheduledFuture<?> turnTimerTask;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor();

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

    public boolean isFull (){
        return players.size()>= MAXPLAYERS;
    }

    public boolean isEnoughToken (long token){
        return token>=this.betToken;
    }

    public int getNumberPlayer (){
        return players.size();
    }

    public void cancelCountdown() {
        if (countdownTask != null && !countdownTask.isDone()) {
            countdownTask.cancel(true);
            countdownTask=null;
        }
    }

    public boolean isReadyToStartCountdown() {
        boolean allReady = players.values()
                .stream()
                .allMatch(Player::isReady);

        return allReady && players.size() >= 2;
    }

    public List<Card> createDeck() {
        List<Card> deck = new ArrayList<>();

        // rank: 3 → 15 (15 = 2)
        for (int rank = 3; rank <= 15; rank++) {
            for (int suit = 1; suit <= 4; suit++) {
                deck.add(new Card(rank, suit));
            }
        }

        return deck;
    }

    private void shuffleDeck(List<Card> deck) {
        Collections.shuffle(deck, new SecureRandom()); // an toàn hơn
    }

    private void dealCards(List<Card> deck) {

        List<Player> playerList = new ArrayList<>(players.values());

        int playerCount = playerList.size();
        int cardsPerPlayer = 13;

        for (int i = 0; i < playerCount; i++) {

            List<Card> hand = new ArrayList<>();

            for (int j = 0; j < cardsPerPlayer; j++) {
                hand.add(deck.get(i * cardsPerPlayer + j));
            }

            // sort bài trước khi đưa cho player
            hand.sort(Comparator
                    .comparingInt(Card::getRank)
                    .thenComparingInt(Card::getSuit));

            playerList.get(i).setHandCards(hand);
        }
    }

    private int findFirstTurn() {

        List<Player> playerList = new ArrayList<>(players.values());

        // 1️⃣ Ưu tiên người có 3♣
        for (int i = 0; i < playerList.size(); i++) {
            for (Card card : playerList.get(i).getHandCards()) {
                if (card.getRank() == 3 && card.getSuit() == 2) {
                    return i;
                }
            }
        }

        // 2️⃣ Không có 3♣ → tìm lá nhỏ nhất (loại trừ 3♠)
        int firstTurnIndex = 0;
        Card smallestCard = null;

        for (int i = 0; i < playerList.size(); i++) {
            for (Card card : playerList.get(i).getHandCards()) {

                // bỏ qua 3♠
                if (card.getRank() == 3 && card.getSuit() == 1) {
                    continue;
                }

                if (smallestCard == null
                        || isSmaller(card, smallestCard)) {

                    smallestCard = card;
                    firstTurnIndex = i;
                }
            }
        }

        return firstTurnIndex;
    }

    private boolean isSmaller(Card a, Card b) {

        // so rank trước
        if (a.getRank() != b.getRank()) {
            return a.getRank() < b.getRank();
        }

        // cùng rank → so suit
        return a.getSuit() < b.getSuit();
    }

    public synchronized void startGame() {

        if (status != RoomStatus.READY) {
            return;
        }

        List<Card> deck = createDeck();

        shuffleDeck(deck);

        dealCards(deck);

        this.currentTurn = findFirstTurn();
        this.status = RoomStatus.PLAYING;

        // Gửi bài riêng cho từng player
        List<Player> playerList = new ArrayList<>(players.values());

        for (int i = 0; i < playerList.size(); i++) {
            Player p = playerList.get(i);
            RoomStateResponse snapshot = RoomStateResponse.from(this, PlayerResponse.from(p, true));
            sendSnapshotTo(p, SocketAction.SYNC_DATA, snapshot);
        }
        startTurn();
    }

    private void startTurn() {
        cancelTurnTimer(); // đảm bảo không còn timer cũ

        Player currentPlayer = getPlayerBySeat(currentTurn);

//        List<Integer> allowedCardIds =
//                calculateAllowedCards(currentPlayer);

        // Gửi event bắt đầu lượt
        broadcastEvent(
                SocketAction.NEXT_TURN,
                Map.of(
                    "playerId", currentPlayer.getUser().getId(),
//                    "allowedCardIds", allowedCardIds,
                    "duration", TIMEFORTURN
                )
        );

        // Bắt đầu đếm 15s
        turnTimerTask = scheduler.schedule(() -> {
            try {
                synchronized (Room.this) {
                    moveToNextTurn();
                }
            } catch (Exception e) {
                log.error("Turn timer crashed", e);
            }
        }, TIMEFORTURN, TimeUnit.SECONDS);
    }

//    private List<Integer> calculateAllowedCards(Player player) {
//
//        List<Card> hand = player.getHandCards();
//
//        if (lastPlayedCards.isEmpty()) {
//            return hand.stream()
//                .map(Card::getId) // giả sử Card có id
//                .toList();
//        }
//
//        // TODO: validate theo luật Tiến Lên
//        return hand.stream()
//            .filter(card -> isValid(card))
//            .map(Card::getId)
//            .toList();
//    }

    private void autoPass() {

//        broadcastEvent(
//            SocketAction.PASS,
//            Map.of("playerId",
//                new ArrayList<>(players.values())
//                    .get(currentTurn)
//                    .getUser().getId()
//            )
//        );

        moveToNextTurn();
    }

    private void moveToNextTurn() {

        if (players.size() < 2) {
//            endGame();
            return;
        }

        int attempts = 0;

        do {
            currentTurn = (currentTurn + 1) % MAXPLAYERS;
            attempts++;

            if (attempts > MAXPLAYERS) {
                return; // tránh loop vô hạn
            }

        } while (getPlayerBySeat(currentTurn) == null);

        startTurn();
    }

    private Player getPlayerBySeat(int seatIndex) {
        return players.values()
                .stream()
                .filter(p -> p.getSeatIndex() == seatIndex)
                .findFirst()
                .orElse(null);
    }

    private void cancelTurnTimer() {
        if (turnTimerTask != null && !turnTimerTask.isDone()) {
            turnTimerTask.cancel(true);
        }
        turnTimerTask = null;
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
