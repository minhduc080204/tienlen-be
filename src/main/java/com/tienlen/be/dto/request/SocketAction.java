package com.tienlen.be.dto.request;

public enum SocketAction {
    JOIN_ROOM,
    LEFT_ROOM,

    READY,
    UNREADY,

    START_GAME,
    PLAY_CARDS,
    PASS,
    TIMEOUT,

    GAME_FINISHED,

    CHAT,

    SYNC_DATA,
}
