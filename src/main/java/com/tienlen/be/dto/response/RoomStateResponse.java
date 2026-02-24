package com.tienlen.be.dto.response;

import com.tienlen.be.model.Room;
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

@Data
public class RoomStateResponse {

    private int roomId;
    private int currentTurn;
    private String status;
    private int seatIndex;
    private List<PlayerResponse> players;

    public static RoomStateResponse from(Room room, int seatIndex) {
        RoomStateResponse res = new RoomStateResponse();
        res.roomId = room.getRoomId();
        res.currentTurn = room.getCurrentTurn();
        res.status = room.getStatus().name();
        res.seatIndex = seatIndex;

//        res.players = room.getPlayers().stream()
//                .map(PlayerResponse::from)
//                .collect(Collectors.toList());

        return res;
    }
}
