package com.tienlen.be.model;

public enum BotLevel {
    EASY,
    MEDIUM,
    HARD;

    public static BotLevel from(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("botLevel is required");
        }
        return BotLevel.valueOf(raw.trim().toUpperCase());
    }
}

