package com.tienlen.be.service;

import com.tienlen.be.dto.request.BotRoomAttackRequest;
import com.tienlen.be.dto.request.BotRoomCreateRequest;
import com.tienlen.be.dto.response.BotRoomAttackResponse;
import com.tienlen.be.dto.response.BotRoomCreateResponse;
import com.tienlen.be.dto.response.BotRoomStartResponse;
import com.tienlen.be.dto.response.UserResponse;
import com.tienlen.be.entity.User;
import com.tienlen.be.exception.BadRequestException;
import com.tienlen.be.exception.ConflictException;
import com.tienlen.be.model.BotLevel;
import com.tienlen.be.model.Card;
import com.tienlen.be.model.MoveType;
import com.tienlen.be.model.RuleValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BotRoomService {
    private static final int USER_SEAT = 0;
    private static final int BOT_SEAT = 1;

    private final UserService userService;
    private final RestTemplate restTemplate;
    private final SecureRandom random = new SecureRandom();
    private final Map<Long, BotRoomState> activeRoomsByUserId = new ConcurrentHashMap<>();

    @Value("${bot.external.url:http://127.0.0.1:8000/predict}")
    private String modelUrl;

    public BotRoomCreateResponse create(UserResponse user, BotRoomCreateRequest request) {
        if (request == null || request.getBetToken() == null || request.getBetToken() <= 0) {
            throw new BadRequestException("betToken không hợp lệ");
        }
        BotLevel level;
        try {
            level = BotLevel.from(request.getBotLevel());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("botLevel phải là easy, medium hoặc hard");
        }

        User entity = userService.getByUserId(user.getId());
        if (entity.getTokenBalance() < request.getBetToken()) {
            throw new BadRequestException("Không đủ token");
        }

        BotRoomState room = new BotRoomState();
        room.betToken = request.getBetToken();
        room.botLevel = level;
        room.started = false;
        room.finished = false;
        room.currentTurn = USER_SEAT;
        room.lastAttackerSeat = -1;
        room.userCards = new ArrayList<>();
        room.botCards = new ArrayList<>();
        room.table = new ArrayList<>();
        room.discardPile = new ArrayList<>();
        room.winners = new ArrayList<>();

        activeRoomsByUserId.put(user.getId(), room);
        return new BotRoomCreateResponse(room.betToken, room.botLevel.name().toLowerCase());
    }

    public BotRoomStartResponse start(UserResponse user) {
        BotRoomState room = getRoom(user.getId());
        if (room.started && !room.finished) {
            throw new ConflictException("Ván này đã bắt đầu");
        }

        User entity = userService.getByUserId(user.getId());
        if (entity.getTokenBalance() < room.betToken) {
            throw new BadRequestException("Không đủ token để bắt đầu ván");
        }

        entity.setTokenBalance(entity.getTokenBalance() - room.betToken);
        userService.saveAll(List.of(entity));

        dealCards(room);
        room.started = true;
        room.finished = false;
        room.winners.clear();
        room.table.clear();
        room.discardPile.clear();
        room.lastAttackerSeat = -1;
        room.currentTurn = findFirstTurn(room);

        if (room.currentTurn == BOT_SEAT) {
            botTurn(room);
        }

        return new BotRoomStartResponse(
                entity.getTokenBalance(),
                toCardIds(room.userCards),
                room.botCards.size(),
                turnLabel(room.currentTurn),
                toCardIds(room.table)
        );
    }

    public BotRoomAttackResponse attack(UserResponse user, BotRoomAttackRequest request) {
        BotRoomState room = getRoom(user.getId());
        if (!room.started) {
            throw new BadRequestException("Ván chưa bắt đầu");
        }
        if (room.finished) {
            throw new ConflictException("Ván đã kết thúc");
        }
        if (room.currentTurn != USER_SEAT) {
            throw new BadRequestException("Chưa tới lượt của bạn");
        }

        List<Integer> rawCards = request == null || request.getCards() == null ? List.of() : request.getCards();
        List<Card> playedByUser = rawCards.stream().map(this::parseCard).toList();
        applyMove(room, USER_SEAT, playedByUser);

        List<Card> botPlayed = List.of();
        if (!room.finished && room.currentTurn == BOT_SEAT) {
            botPlayed = botTurn(room);
        }

        return new BotRoomAttackResponse(
                toCardIds(botPlayed),
                room.botCards.size(),
                room.userCards.size(),
                turnLabel(room.currentTurn),
                toCardIds(room.table),
                room.finished,
                winnersLabel(room.winners)
        );
    }

    private List<Card> botTurn(BotRoomState room) {
        if (room.finished) {
            return List.of();
        }
        List<Card> botMove = switch (room.botLevel) {
            case EASY -> chooseEasyMove(room.botCards, room.table);
            case MEDIUM -> chooseMediumMove(room.botCards, room.table, room.discardPile);
            case HARD -> chooseHardMove(room);
        };
        applyMove(room, BOT_SEAT, botMove);
        return botMove;
    }

    private List<Card> chooseHardMove(BotRoomState room) {
        try {
            return callModel(room);
        } catch (RestClientException | IllegalArgumentException ex) {
            return chooseMediumMove(room.botCards, room.table, room.discardPile);
        }
    }

    private List<Card> callModel(BotRoomState room) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("hand", room.botCards.stream().map(c -> Map.of("rank", c.getRank(), "suit", c.getSuit())).toList());
        payload.put("opponent_counts", List.of(room.userCards.size()));
        payload.put("current_trick", room.table.stream().map(c -> Map.of("rank", c.getRank(), "suit", c.getSuit())).toList());
        payload.put("player_id", 1);
        payload.put("num_players", 2);
        payload.put("discard_pile", room.discardPile.stream().map(c -> Map.of("rank", c.getRank(), "suit", c.getSuit())).toList());
        payload.put("inference_mode", "greedy");
        payload.put("temperature", 1.0);
        payload.put("top_k_actions", 3);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(modelUrl, payload, Map.class);
        if (response == null) {
            throw new IllegalArgumentException("Model không trả dữ liệu");
        }

        Object actionCardsObj = response.get("action_cards");
        if (!(actionCardsObj instanceof List<?> cardsRaw) || cardsRaw.isEmpty()) {
            return List.of();
        }

        List<Card> selected = new ArrayList<>();
        List<Card> copy = new ArrayList<>(room.botCards);
        for (Object o : cardsRaw) {
            if (!(o instanceof Map<?, ?> cardMap)) {
                throw new IllegalArgumentException("action_cards không hợp lệ");
            }
            Object rankObj = cardMap.get("rank");
            Object suitObj = cardMap.get("suit");
            if (!(rankObj instanceof Number rankNum) || !(suitObj instanceof Number suitNum)) {
                throw new IllegalArgumentException("action_cards thiếu rank/suit");
            }
            Card card = consumeCard(copy, rankNum.intValue(), suitNum.intValue());
            if (card == null) {
                throw new IllegalArgumentException("Model trả lá không có trong tay bot");
            }
            selected.add(card);
        }

        if (!isValidMove(room.table, selected)) {
            throw new IllegalArgumentException("Model trả nước đi không hợp lệ");
        }
        return selected;
    }

    private Card consumeCard(List<Card> cards, int rank, int suit) {
        for (int i = 0; i < cards.size(); i++) {
            Card c = cards.get(i);
            if (c.getRank() == rank && c.getSuit() == suit) {
                cards.remove(i);
                return c;
            }
        }
        return null;
    }

    private void applyMove(BotRoomState room, int seat, List<Card> move) {
        boolean isPass = move == null || move.isEmpty();
        if (isPass) {
            if (room.table.isEmpty()) {
                throw new BadRequestException("Không thể bỏ lượt khi bắt đầu vòng");
            }
            if (room.lastAttackerSeat == -1) {
                throw new BadRequestException("Trạng thái ván không hợp lệ");
            }
            room.table.clear();
            room.currentTurn = room.lastAttackerSeat;
            return;
        }

        if (!isValidMove(room.table, move)) {
            throw new BadRequestException("Nước đi không hợp lệ");
        }

        List<Card> hand = seat == USER_SEAT ? room.userCards : room.botCards;
        removeCards(hand, move);
        room.table = new ArrayList<>(move);
        room.discardPile.addAll(move);
        room.lastAttackerSeat = seat;

        if (hand.isEmpty()) {
            finish(room, seat);
            return;
        }
        room.currentTurn = oppositeSeat(seat);
    }

    private void finish(BotRoomState room, int firstWinnerSeat) {
        room.finished = true;
        room.currentTurn = firstWinnerSeat;
        room.winners.clear();
        room.winners.add(firstWinnerSeat);
        room.winners.add(oppositeSeat(firstWinnerSeat));
    }

    private boolean isValidMove(List<Card> table, List<Card> move) {
        MoveType type = RuleValidator.detectMoveType(move);
        if (type == null || type == MoveType.ANY) {
            return false;
        }
        if (table == null || table.isEmpty()) {
            return true;
        }
        return RuleValidator.canBeat(table, move);
    }

    private void removeCards(List<Card> hand, List<Card> toRemove) {
        Map<Integer, Integer> need = new HashMap<>();
        for (Card c : toRemove) {
            need.merge(c.getId(), 1, Integer::sum);
        }
        List<Card> next = new ArrayList<>();
        for (Card c : hand) {
            int count = need.getOrDefault(c.getId(), 0);
            if (count > 0) {
                need.put(c.getId(), count - 1);
            } else {
                next.add(c);
            }
        }
        if (need.values().stream().anyMatch(v -> v != 0)) {
            throw new BadRequestException("Bài đánh ra không nằm trong tay");
        }
        next.sort(this::compareCard);
        hand.clear();
        hand.addAll(next);
    }

    private List<Card> chooseEasyMove(List<Card> hand, List<Card> table) {
        List<Card> sorted = new ArrayList<>(hand);
        sorted.sort(this::compareCard);
        if (table == null || table.isEmpty()) {
            return List.of(sorted.get(0));
        }
        return sorted.stream()
                .map(List::of)
                .filter(m -> RuleValidator.canBeat(table, m))
                .findFirst()
                .orElse(List.of());
    }

    private List<Card> chooseMediumMove(List<Card> hand, List<Card> table, List<Card> discardPile) {
        List<List<Card>> candidates = generateCandidates(hand);
        List<List<Card>> valids = candidates.stream()
                .filter(m -> isValidMove(table, m))
                .toList();
        if (valids.isEmpty()) {
            return List.of();
        }

        boolean defend = table != null && !table.isEmpty() && hand.size() > 4;
        Comparator<List<Card>> cmp = defend
                ? Comparator.comparingInt(this::defensiveCost)
                : Comparator.comparingInt(c -> openingCostWithMemory(c, hand.size(), hand, discardPile));
        return valids.stream().min(cmp).orElse(List.of());
    }

    private int defensiveCost(List<Card> move) {
        MoveType type = RuleValidator.detectMoveType(move);
        int max = move.stream().mapToInt(this::cardPower).max().orElse(0);
        int penalty = switch (type) {
            case FOUR_OF_KIND -> 500;
            case DOUBLE_STRAIGHT -> 350;
            case TWO -> 250;
            case STRAIGHT -> 80;
            case TRIPLE -> 50;
            case PAIR -> 20;
            default -> 0;
        };
        return max + penalty;
    }

    private int openingCostWithMemory(List<Card> move, int handSize, List<Card> hand, List<Card> discardPile) {
        int aggressive = aggressiveScore(move, handSize);
        int pressure = estimateCounterPressure(move, hand, discardPile);
        return -aggressive + pressure * 50;
    }

    private int aggressiveScore(List<Card> move, int handSize) {
        MoveType type = RuleValidator.detectMoveType(move);
        int maxPower = move.stream().mapToInt(this::cardPower).max().orElse(0);
        boolean hasTwo = move.stream().anyMatch(c -> c.getRank() == 15);
        int score = move.size() * 150;
        if (handSize - move.size() == 0) score += 10_000;
        if (type == MoveType.STRAIGHT || type == MoveType.DOUBLE_STRAIGHT || type == MoveType.FOUR_OF_KIND) score += 180;
        if (hasTwo && handSize > 5) score -= 220;
        score += maxPower / 5;
        return score;
    }

    private int estimateCounterPressure(List<Card> move, List<Card> hand, List<Card> discardPile) {
        MoveType type = RuleValidator.detectMoveType(move);
        if (type == null || type == MoveType.ANY || type == MoveType.STRAIGHT || type == MoveType.DOUBLE_STRAIGHT) {
            return 0;
        }
        Set<Integer> known = new HashSet<>();
        hand.forEach(c -> known.add(c.getId()));
        discardPile.forEach(c -> known.add(c.getId()));
        move.forEach(c -> known.add(c.getId()));

        Map<Integer, Integer> unseenByRank = new HashMap<>();
        for (Card c : createDeck()) {
            if (!known.contains(c.getId())) {
                unseenByRank.merge(c.getRank(), 1, Integer::sum);
            }
        }

        int rank = move.get(0).getRank();
        return switch (type) {
            case SINGLE, TWO -> unseenByRank.entrySet().stream().filter(e -> e.getKey() > rank).mapToInt(Map.Entry::getValue).sum();
            case PAIR -> unseenByRank.entrySet().stream().filter(e -> e.getKey() > rank && e.getValue() >= 2).mapToInt(Map.Entry::getValue).sum();
            case TRIPLE -> unseenByRank.entrySet().stream().filter(e -> e.getKey() > rank && e.getValue() >= 3).mapToInt(Map.Entry::getValue).sum();
            case FOUR_OF_KIND -> unseenByRank.entrySet().stream().filter(e -> e.getKey() > rank && e.getValue() >= 4).mapToInt(Map.Entry::getValue).sum();
            default -> 0;
        };
    }

    private List<List<Card>> generateCandidates(List<Card> hand) {
        List<Card> sorted = new ArrayList<>(hand);
        sorted.sort(this::compareCard);
        List<List<Card>> candidates = new ArrayList<>();

        for (Card c : sorted) {
            candidates.add(List.of(c));
        }

        Map<Integer, List<Card>> byRank = sorted.stream().collect(Collectors.groupingBy(Card::getRank));
        for (List<Card> group : byRank.values()) {
            List<Card> g = new ArrayList<>(group);
            g.sort(this::compareCard);
            if (g.size() >= 2) candidates.add(List.of(g.get(0), g.get(1)));
            if (g.size() >= 3) candidates.add(List.of(g.get(0), g.get(1), g.get(2)));
            if (g.size() == 4) candidates.add(List.of(g.get(0), g.get(1), g.get(2), g.get(3)));
        }

        List<Integer> straightRanks = byRank.keySet().stream().filter(r -> r != 15).sorted().toList();
        for (int i = 0; i < straightRanks.size(); i++) {
            List<Card> acc = new ArrayList<>();
            int expected = straightRanks.get(i);
            for (int j = i; j < straightRanks.size(); j++) {
                int rank = straightRanks.get(j);
                if (rank != expected) break;
                List<Card> group = new ArrayList<>(byRank.get(rank));
                group.sort(this::compareCard);
                acc.add(group.get(0));
                if (acc.size() >= 3) candidates.add(new ArrayList<>(acc));
                expected++;
            }
        }

        List<Integer> pairRanks = byRank.entrySet().stream()
                .filter(e -> e.getKey() != 15 && e.getValue().size() >= 2)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        for (int i = 0; i < pairRanks.size(); i++) {
            List<Card> acc = new ArrayList<>();
            int expected = pairRanks.get(i);
            for (int j = i; j < pairRanks.size(); j++) {
                int rank = pairRanks.get(j);
                if (rank != expected) break;
                List<Card> group = new ArrayList<>(byRank.get(rank));
                group.sort(this::compareCard);
                acc.add(group.get(0));
                acc.add(group.get(1));
                if (acc.size() >= 6) candidates.add(new ArrayList<>(acc));
                expected++;
            }
        }
        return candidates;
    }

    private void dealCards(BotRoomState room) {
        List<Card> deck = createDeck();
        Collections.shuffle(deck, random);
        room.userCards = new ArrayList<>(deck.subList(0, 13));
        room.botCards = new ArrayList<>(deck.subList(13, 26));
        room.userCards.sort(this::compareCard);
        room.botCards.sort(this::compareCard);
    }

    private int findFirstTurn(BotRoomState room) {
        if (containsCard(room.userCards, 3, 2)) return USER_SEAT;
        if (containsCard(room.botCards, 3, 2)) return BOT_SEAT;

        Card userMin = minCard(room.userCards);
        Card botMin = minCard(room.botCards);
        return compareCard(userMin, botMin) <= 0 ? USER_SEAT : BOT_SEAT;
    }

    private boolean containsCard(List<Card> cards, int rank, int suit) {
        return cards.stream().anyMatch(c -> c.getRank() == rank && c.getSuit() == suit);
    }

    private Card minCard(List<Card> cards) {
        return cards.stream().min(this::compareCard).orElseThrow();
    }

    private List<Card> createDeck() {
        List<Card> deck = new ArrayList<>();
        for (int rank = 3; rank <= 15; rank++) {
            for (int suit = 1; suit <= 4; suit++) {
                deck.add(new Card(rank, suit));
            }
        }
        return deck;
    }

    private Card parseCard(Integer id) {
        if (id == null) throw new BadRequestException("Card id không hợp lệ");
        int rank = id / 10;
        int suit = id % 10;
        if (rank < 3 || rank > 15 || suit < 1 || suit > 4) {
            throw new BadRequestException("Card id không hợp lệ");
        }
        return new Card(rank, suit);
    }

    private List<Integer> toCardIds(List<Card> cards) {
        return cards.stream().sorted(this::compareCard).map(Card::getId).toList();
    }

    private int compareCard(Card a, Card b) {
        if (a.getRank() != b.getRank()) return Integer.compare(a.getRank(), b.getRank());
        return Integer.compare(a.getSuit(), b.getSuit());
    }

    private int cardPower(Card c) {
        return c.getRank() * 10 + c.getSuit();
    }

    private int oppositeSeat(int seat) {
        return seat == USER_SEAT ? BOT_SEAT : USER_SEAT;
    }

    private String turnLabel(int seat) {
        return seat == USER_SEAT ? "USER" : "BOT";
    }

    private List<String> winnersLabel(List<Integer> winners) {
        return winners.stream().map(this::turnLabel).toList();
    }

    private BotRoomState getRoom(Long userId) {
        BotRoomState room = activeRoomsByUserId.get(userId);
        if (room == null) {
            throw new BadRequestException("Bạn chưa tạo phòng bot");
        }
        return room;
    }

    private static class BotRoomState {
        private long betToken;
        private BotLevel botLevel;
        private boolean started;
        private boolean finished;
        private int currentTurn;
        private int lastAttackerSeat;
        private List<Card> userCards;
        private List<Card> botCards;
        private List<Card> table;
        private List<Card> discardPile;
        private List<Integer> winners;
    }
}

