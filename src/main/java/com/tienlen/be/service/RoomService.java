package com.tienlen.be.service;

import com.tienlen.be.dto.response.UserResponse;
import com.tienlen.be.exception.BadRequestException;
import com.tienlen.be.model.Room;
import com.tienlen.be.model.RoomStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final Map<Integer, Room> rooms = new ConcurrentHashMap<>();
    private final UserService userService;

    public Optional<Room> findAvailableRoom(long userId) {
        UserResponse user = getUserResponseByUserId(userId);

        return rooms.values().stream()
                .filter(r ->
                        !r.isFull() &&
                        r.getStatus() == RoomStatus.WAITING &&
                        r.isEnoughToken(user.getTokenBalance())
                )
                .findFirst();
    }

    public boolean isPlayerInRoom(int roomId, long userId) {
        Room room = rooms.get(roomId);
        return room != null && room.getPlayers().containsKey(userId);
    }

    public Room createRoom(Long userId, long betToken) {
        UserResponse user = getUserResponseByUserId(userId);

        if(user.getTokenBalance()<betToken){
            throw new BadRequestException("Không đủ token");
        }

        Room room = new Room(betToken);

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

    public void deleteRoom(int roomId){
        Room room = rooms.remove(roomId);
        room.shutdown();
    }


    private UserResponse getUserResponseByUserId(long userId){
        return new UserResponse(userService.getByUserId(userId));
    }
}