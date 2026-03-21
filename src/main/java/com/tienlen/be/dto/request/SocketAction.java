package com.tienlen.be.dto.request;

public enum SocketAction {
    JOIN_ROOM,
    LEFT_ROOM,

    READY,
    UNREADY,
    START_COUNTDOWN,

    START_GAME,
    ATTACK,
    PASS,
    TIMEOUT,
    NEXT_TURN,

    GAME_FINISHED,

    CHAT,

    SYNC_DATA,
    GAME_MESSAGE,
}
