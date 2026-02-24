package com.tienlen.be.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienlen.be.dto.response.RoomStateResponse;
import com.tienlen.be.model.Player;
import com.tienlen.be.model.Room;
import com.tienlen.be.model.RoomStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final Map<Integer, Room> rooms = new ConcurrentHashMap<>();

    public Optional<Room> findAvailableRoom() {
        return rooms.values().stream()
                .filter(r -> !r.isFull() && r.getStatus() == RoomStatus.WAITING)
                .findFirst();
    }

    public boolean isPlayerInRoom(int roomId, long userId) {
        Room room = rooms.get(roomId);
        return room != null && room.getPlayers().containsKey(userId);
    }

    public Room createRoom() {
        Room room = new Room();
        room.setStatus(RoomStatus.WAITING);
        room.setCurrentTurn(0);

        rooms.put(room.getRoomId(), room);
        return room;
    }

    public Room getRoom(int roomId) {
        return rooms.get(roomId);
    }

    public int getRoomSize(){
        System.out.println(rooms);
        return rooms.size();
    }
}