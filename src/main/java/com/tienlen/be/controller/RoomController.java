package com.tienlen.be.controller;

import com.tienlen.be.dto.response.QuickJoinResponse;
import com.tienlen.be.dto.response.UserResponse;
import com.tienlen.be.model.Room;
import com.tienlen.be.security.CurrentUser;
import com.tienlen.be.service.JwtService;
import com.tienlen.be.service.PlayerService;
import com.tienlen.be.service.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {
    private final RoomService roomService;
    private final PlayerService playerService;

    @GetMapping("/")
    public String home(){
        return "Hi";
    }

    @GetMapping("/infor")
    public int infor(){
        return roomService.getRoomSize();
    }

    @PostMapping("/create")
    public Map<String, Integer> createRoom() {
        Room room = roomService.createRoom();
        return Map.of("roomId", room.getRoomId());
    }

    @PostMapping("/quick-join")
    public QuickJoinResponse quickJoin(
            @CurrentUser UserResponse user
    ) {
        Integer currentRoom = playerService.getCurrentRoom(user.getId());

        if (currentRoom != null) {
            // user đã ở trong room → reconnect
            return new QuickJoinResponse(
                    currentRoom,
                    "/ws/room?roomId=" + currentRoom
            );
        }
        Room room = roomService.findAvailableRoom()
                .orElseGet(roomService::createRoom);

        return new QuickJoinResponse(
                room.getRoomId(),
                "/ws/room?roomId=" + room.getRoomId()
        );
    }

//    /**
//     * Join room
//     */
//    @PostMapping("/join/{roomId}")
//    public RoomStateResponse joinRoom(
//            @PathVariable int roomId,
//            @CurrentUser UserResponse user
//    ) {
//        System.out.println(user.getId());
//        System.out.println(user);
//
//        Player player = new Player();
//        player.setUser(user);
//        player.setBot(false);
//
//        Room room = roomService.joinRoom(roomId, player);
//
//        return RoomStateResponse.from(room, player.getSeatIndex());
//    }
}

