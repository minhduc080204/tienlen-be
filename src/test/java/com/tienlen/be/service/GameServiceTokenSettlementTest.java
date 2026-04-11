package com.tienlen.be.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tienlen.be.dto.response.UserResponse;
import com.tienlen.be.entity.User;
import com.tienlen.be.model.Player;
import com.tienlen.be.model.Room;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameServiceTokenSettlementTest {

    @Test
    void collectBetForRound_shouldDeductBetAndStorePot() {
        RoomService roomService = mock(RoomService.class);
        UserService userService = mock(UserService.class);
        JwtService jwtService = mock(JwtService.class);
        GameService gameService = new GameService(roomService, userService, jwtService, new ObjectMapper());

        Room room = new Room(100);
        room.addPlayer(new Player(new UserResponse(1L, "u1", null, 500), 0), mock(org.springframework.web.socket.WebSocketSession.class));
        room.addPlayer(new Player(new UserResponse(2L, "u2", null, 350), 1), mock(org.springframework.web.socket.WebSocketSession.class));

        User user1 = new User();
        user1.setId(1L);
        user1.setTokenBalance(500);
        User user2 = new User();
        user2.setId(2L);
        user2.setTokenBalance(350);

        when(userService.getByUserId(1L)).thenReturn(user1);
        when(userService.getByUserId(2L)).thenReturn(user2);

        boolean collected = gameService.collectBetForRound(room);

        assertTrue(collected);
        assertEquals(200, room.getCurrentPot());
        assertEquals(Set.of(1L, 2L), new HashSet<>(room.getRoundParticipantIds()));
        assertEquals(400, user1.getTokenBalance());
        assertEquals(250, user2.getTokenBalance());
        assertEquals(400, room.getPlayers().get(1L).getUser().getTokenBalance());
        assertEquals(250, room.getPlayers().get(2L).getUser().getTokenBalance());

        ArgumentCaptor<List<User>> usersCaptor = ArgumentCaptor.forClass(List.class);
        verify(userService).saveAll(usersCaptor.capture());
        assertEquals(2, usersCaptor.getValue().size());
    }

    @Test
    void settleRoundPot_shouldDistributeByRankingForThreePlayers() {
        RoomService roomService = mock(RoomService.class);
        UserService userService = mock(UserService.class);
        JwtService jwtService = mock(JwtService.class);
        GameService gameService = new GameService(roomService, userService, jwtService, new ObjectMapper());

        Room room = new Room(100);
        room.addPlayer(new Player(new UserResponse(1L, "u1", null, 900), 0), mock(org.springframework.web.socket.WebSocketSession.class));
        room.addPlayer(new Player(new UserResponse(2L, "u2", null, 900), 1), mock(org.springframework.web.socket.WebSocketSession.class));
        room.addPlayer(new Player(new UserResponse(3L, "u3", null, 900), 2), mock(org.springframework.web.socket.WebSocketSession.class));
        room.setRoundParticipantIds(List.of(1L, 2L, 3L));
        room.setCurrentPot(300);

        User user1 = new User();
        user1.setId(1L);
        user1.setTokenBalance(900);
        User user2 = new User();
        user2.setId(2L);
        user2.setTokenBalance(900);
        User user3 = new User();
        user3.setId(3L);
        user3.setTokenBalance(900);

        when(userService.getByUserId(1L)).thenReturn(user1);
        when(userService.getByUserId(2L)).thenReturn(user2);
        when(userService.getByUserId(3L)).thenReturn(user3);

        gameService.settleRoundPot(room, List.of(2L, 1L, 3L));

        assertEquals(990, user1.getTokenBalance());
        assertEquals(1110, user2.getTokenBalance());
        assertEquals(900, user3.getTokenBalance());
        assertEquals(990, room.getPlayers().get(1L).getUser().getTokenBalance());
        assertEquals(1110, room.getPlayers().get(2L).getUser().getTokenBalance());
        assertEquals(900, room.getPlayers().get(3L).getUser().getTokenBalance());
    }

    @Test
    void buildFinalRanking_shouldPutLeaverAtEnd() {
        RoomService roomService = mock(RoomService.class);
        UserService userService = mock(UserService.class);
        JwtService jwtService = mock(JwtService.class);
        GameService gameService = new GameService(roomService, userService, jwtService, new ObjectMapper());

        Room room = new Room(100);
        room.addPlayer(new Player(new UserResponse(1L, "u1", null, 900), 0), mock(org.springframework.web.socket.WebSocketSession.class));
        room.addPlayer(new Player(new UserResponse(2L, "u2", null, 900), 1), mock(org.springframework.web.socket.WebSocketSession.class));
        room.setRoundParticipantIds(List.of(1L, 2L, 3L));
        room.setRoundLeaverIds(List.of(3L));
        room.getWinners().add(1L);

        List<Long> ranking = gameService.buildFinalRanking(room);

        assertEquals(List.of(1L, 2L, 3L), ranking);
    }
}
