package com.tienlen.be.model;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.socket.WebSocketSession;

import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Getter
@Setter
@Slf4j
public class Room {
    public static int MAX_PLAYERS = 4;
    public static int TIME_OF_READY = 5;
    public static int TIME_OF_TURN = 15;
    private static final AtomicInteger ROOM_ID_GENERATOR =
            new AtomicInteger(10000);

    private Map<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private Map<Long, Player> players = new ConcurrentHashMap<>();
    private List<String> table = new ArrayList<>();
    
    private ScheduledFuture<?> countdownTask;
    private ScheduledFuture<?> turnTimerTask;

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

    public void shutdown(){
        cancelTurnTimer();
        cancelCountdown();
        sessions.clear();
    }

    public void addPlayer(Player player, WebSocketSession session) {
        if(isFull()){
            throw new RuntimeException("Room full");
        }
        players.put(player.getUser().getId(), player);
        sessions.put(player.getUser().getId(), session);
    }

    public boolean removePlayerByUserId(Long userId) {
        Player removedPlayer = players.remove(userId);
        if (removedPlayer == null) {
            return false;
        }

        WebSocketSession session = sessions.remove(userId);
        if (session != null && session.isOpen()) {
            try {
                session.close();
            } catch (Exception e) {
                log.warn("Error closing session for user {}", userId);
            }
        }

        log.info("User {} left room {}", userId, roomId);

        if (players.isEmpty()) {
            log.info("Room {} is empty", roomId);
            return true;
        }
        return false;
    }

    public boolean isFull (){
        return players.size()>= MAX_PLAYERS;
    }

    public boolean isEnoughToken (long token){
        return token>=this.betToken;
    }

    public int getNewSeatIndex() {
        if (players == null || players.isEmpty()) {
            return 0;
        }

        List<Player> playerList = new ArrayList<>(players.values());
        for (int i = 0; i < 4; i++) {
            boolean taken = false;
            for (Player player : playerList) {
                if (player.getSeatIndex() == i) {
                    taken = true;
                    break;
                }
            }
            if (!taken) {
                return i;
            }
        }
        return 4;
    }

    public void cancelCountdown() {
        if (countdownTask != null && !countdownTask.isDone()) {
            countdownTask.cancel(true);
            countdownTask=null;
        }
    }
    
    public void cancelTurnTimer() {
        if (turnTimerTask != null && !turnTimerTask.isDone()) {
            turnTimerTask.cancel(false);
            turnTimerTask = null;
        }
    }

    public boolean isReadyToStartCountdown() {
        boolean allReady = players.values()
                .stream()
                .allMatch(Player::isReady);

        return allReady && players.size() >= 2;
    }

    private List<Card> createDeck() {
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
        for (Player p:playerList) {
            for (Card card : p.getHandCards()) {
                if (card.getRank() == 3 && card.getSuit() == 2) {
                    return p.getSeatIndex();
                }
            }
        }

        // 2️⃣ Không có 3♣ → tìm lá nhỏ nhất (loại trừ 3♠)
        int firstTurnIndex = 0;
        Card smallestCard = null;

        for (Player p:playerList) {
            for (Card card : p.getHandCards()) {
                // bỏ qua 3♠
                if (card.getRank() == 3 && card.getSuit() == 1) {
                    continue;
                }

                if (smallestCard == null || isSmaller(card, smallestCard)) {
                    smallestCard = card;
                    firstTurnIndex = p.getSeatIndex();
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

    public synchronized void prepareGame() {
        List<Card> deck = createDeck();
        shuffleDeck(deck);
        dealCards(deck);

        for (Player p : players.values()) {
            p.setPassed(false);
        }

        this.currentTurn = findFirstTurn();
        this.status = RoomStatus.PLAYING;
    }

    public int findNextTurn() {
        List<Player> playerList = new ArrayList<>(players.values());

        Set<Integer> activeSeats = playerList.stream()
                .filter(p -> !p.isPassed() && !p.getHandCards().isEmpty())
                .map(Player::getSeatIndex)
                .collect(Collectors.toSet());

        if (activeSeats.isEmpty()) {
            return currentTurn;
        }

        int nextSeat = currentTurn;

        for (int i = 0; i < 4; i++) {
            nextSeat = (nextSeat + 1) % 4;
            if (activeSeats.contains(nextSeat)) {
                return nextSeat;
            }
        }
        return currentTurn;
    }

    public Player getPlayerBySeatIndex(int seatIndex) {
        return players.values()
            .stream()
            .filter(p -> p.getSeatIndex() == seatIndex)
            .findFirst()
            .orElse(null);
    }
}
