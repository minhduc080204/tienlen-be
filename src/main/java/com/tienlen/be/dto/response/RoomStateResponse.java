package com.tienlen.be.dto.response;

import com.tienlen.be.model.Card;
import com.tienlen.be.model.Room;
import com.tienlen.be.model.RoomStatus;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class RoomStateResponse {

    private int roomId;
    private int currentTurn;
    private RoomStatus status;
    private PlayerResponse me;
    private long betToken;
    private List<String> table;
    private List<PlayerResponse> players;

    public static RoomStateResponse from(Room room, PlayerResponse me) {
        RoomStateResponse res = new RoomStateResponse();
        res.roomId = room.getRoomId();
        res.currentTurn = room.getCurrentTurn();
        res.status = room.getStatus();
        res.betToken = room.getBetToken();
        res.table = room.getTable();
        res.me = me;

        res.players = room.getPlayers()
                .values()
                .stream()
                .map(p -> PlayerResponse.from(p, false))
                .toList();

        return res;
    }
}
