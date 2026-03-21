package com.tienlen.be.model;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class RuleValidator {

    public static boolean isValidSingle(List<Card> cards) {
        return cards.size() == 1;
    }

    public static boolean isValidPair(List<Card> cards) {
        return cards.size() == 2 && cards.get(0).getRank() == cards.get(1).getRank();
    }

    public static boolean isValidTriple(List<Card> cards) {
        return cards.size() == 3 &&
                cards.stream().map(Card::getRank).distinct().count() == 1;
    }

    public static boolean isValidStraight(List<Card> cards) {
        if (cards.size() < 3)
            return false;

        List<Integer> ranks = cards.stream()
                .map(Card::getRank)
                .sorted()
                .collect(Collectors.toList());

        // không cho sảnh chứa heo (rank 15 = 2)
        if (ranks.contains(15))
            return false;

        for (int i = 0; i < ranks.size() - 1; i++) {
            if (ranks.get(i) + 1 != ranks.get(i + 1)) {
                return false;
            }
        }
        return true;
    }

    public static boolean isTwo(List<Card> cards) {
        return cards.size() == 1 && cards.get(0).getRank() == 15;
    }

    public static boolean isFourOfKind(List<Card> cards) {
        return cards.size() == 4 &&
                cards.stream().map(Card::getRank).distinct().count() == 1;
    }

    public static boolean isDoubleStraight(List<Card> cards) {
        if (cards.size() < 6 || cards.size() % 2 != 0)
            return false;

        List<Integer> ranks = cards.stream()
                .map(Card::getRank)
                .sorted()
                .collect(Collectors.toList());

        if (ranks.contains(15))
            return false;

        Map<Integer, Long> counter = ranks.stream()
                .collect(Collectors.groupingBy(r -> r, Collectors.counting()));

        if (counter.values().stream().anyMatch(v -> v != 2L))
            return false;

        List<Integer> uniq = counter.keySet().stream().sorted().collect(Collectors.toList());
        for (int i = 0; i < uniq.size() - 1; i++) {
            if (uniq.get(i) + 1 != uniq.get(i + 1)) {
                return false;
            }
        }
        return true;
    }

    public static MoveType detectMoveType(List<Card> cards) {
        if (cards == null || cards.isEmpty())
            return MoveType.ANY;
        if (isTwo(cards))
            return MoveType.TWO;
        if (isFourOfKind(cards))
            return MoveType.FOUR_OF_KIND;
        if (isDoubleStraight(cards))
            return MoveType.DOUBLE_STRAIGHT;
        if (isValidSingle(cards))
            return MoveType.SINGLE;
        if (isValidPair(cards))
            return MoveType.PAIR;
        if (isValidTriple(cards))
            return MoveType.TRIPLE;
        if (isValidStraight(cards))
            return MoveType.STRAIGHT;
        return null; // Invalid combination
    }

    public static int compareSingle(Card a, Card b) {
        if (a.getRank() != b.getRank()) {
            return Integer.compare(a.getRank(), b.getRank());
        }
        return Integer.compare(a.getSuit(), b.getSuit());
    }

    public static boolean canBeat(List<Card> prevCards, List<Card> newCards) {
        if (prevCards == null || prevCards.isEmpty()) {
            return true;
        }

        MoveType prevType = detectMoveType(prevCards);
        MoveType newType = detectMoveType(newCards);
        System.out.println("Compare: 1");
        if (newType == null)
            return false;
        System.out.println("Compare: 2");
        // chặt heo
        if (prevType == MoveType.TWO) {
            if (newType == MoveType.TWO) {
                return compareSingle(newCards.get(0), prevCards.get(0)) > 0;
            }
            if (newType == MoveType.FOUR_OF_KIND || newType == MoveType.DOUBLE_STRAIGHT) {
                return true;
            }
            return false;
        }

        // chặt đôi thông
        if (prevType == MoveType.DOUBLE_STRAIGHT) {
            if (newType == MoveType.DOUBLE_STRAIGHT) {
                if (newCards.size() > prevCards.size())
                    return true;
                if (newCards.size() == prevCards.size()) {
                    Card maxNew = newCards.stream().max(RuleValidator::compareSingle).orElseThrow();
                    Card maxPrev = prevCards.stream().max(RuleValidator::compareSingle).orElseThrow();
                    return compareSingle(maxNew, maxPrev) > 0;
                }
            }
            return false;
        }

        // chặt tứ quý
        if (prevType == MoveType.FOUR_OF_KIND) {
            return newType == MoveType.FOUR_OF_KIND && newCards.get(0).getRank() > prevCards.get(0).getRank();
        }

        // bình thường
        if ((prevType != newType) && !(prevType == MoveType.SINGLE && newType == MoveType.TWO))
            return false;

        if (newCards.size() != prevCards.size())
            return false;

        if (prevType == MoveType.SINGLE) {
            return compareSingle(newCards.get(0), prevCards.get(0)) > 0;
        }

        if (prevType == MoveType.PAIR || prevType == MoveType.TRIPLE) {
            return newCards.get(0).getRank() > prevCards.get(0).getRank() ||
                    (newCards.get(0).getRank() == prevCards.get(0).getRank()
                            && compareSingle(newCards.get(0), prevCards.get(0)) > 0);
        }

        if (prevType == MoveType.STRAIGHT) {
            Card maxNew = newCards.stream().max(Comparator.comparingInt(Card::getRank)).orElseThrow();
            Card maxPrev = prevCards.stream().max(Comparator.comparingInt(Card::getRank)).orElseThrow();
            return compareSingle(maxNew, maxPrev) > 0;
        }

        return false;
    }
}
