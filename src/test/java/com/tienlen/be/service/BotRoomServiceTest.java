package com.tienlen.be.service;

import com.tienlen.be.dto.request.BotRoomAttackRequest;
import com.tienlen.be.dto.request.BotRoomCreateRequest;
import com.tienlen.be.dto.response.BotRoomAttackResponse;
import com.tienlen.be.dto.response.BotRoomStartResponse;
import com.tienlen.be.dto.response.UserResponse;
import com.tienlen.be.entity.User;
import com.tienlen.be.exception.BadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotRoomServiceTest {
    private UserService userService;
    private RestTemplate restTemplate;
    private BotRoomService botRoomService;
    private User userEntity;
    private UserResponse currentUser;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        restTemplate = mock(RestTemplate.class);
        botRoomService = new BotRoomService(userService, restTemplate);
        ReflectionTestUtils.setField(botRoomService, "modelUrl", "http://127.0.0.1:8000/predict");

        userEntity = new User();
        userEntity.setId(1L);
        userEntity.setTokenBalance(1000);
        currentUser = new UserResponse(1L, "u1", null, 1000);

        when(userService.getByUserId(1L)).thenReturn(userEntity);
        when(userService.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void createAndStart_shouldReturnValidSnapshot() {
        BotRoomCreateRequest createRequest = new BotRoomCreateRequest();
        createRequest.setBetToken(100L);
        createRequest.setBotLevel("medium");

        botRoomService.create(currentUser, createRequest);
        BotRoomStartResponse start = botRoomService.start(currentUser);

        assertEquals(900, start.getUserTokenBalance());
        assertEquals(13, start.getUserCards().size());
        assertEquals(13, start.getBotRemainingCards());
        assertNotNull(start.getCurrentTurn());
    }

    @Test
    void attack_shouldHandlePassOrAttackFlow() {
        BotRoomCreateRequest createRequest = new BotRoomCreateRequest();
        createRequest.setBetToken(100L);
        createRequest.setBotLevel("easy");
        botRoomService.create(currentUser, createRequest);
        BotRoomStartResponse start = botRoomService.start(currentUser);

        BotRoomAttackRequest attackRequest = new BotRoomAttackRequest();
        if (start.getTable().isEmpty()) {
            attackRequest.setCards(List.of(start.getUserCards().get(0)));
        } else {
            attackRequest.setCards(List.of());
        }

        BotRoomAttackResponse response = botRoomService.attack(currentUser, attackRequest);
        assertNotNull(response.getBotPlayedCards());
        assertTrue(response.getBotRemainingCards() >= 0 && response.getBotRemainingCards() <= 13);
        assertFalse(response.getWinners().isEmpty() && response.isFinished());
    }

    @Test
    void hardBot_shouldFallbackToMediumWhenModelFails() {
        when(restTemplate.postForObject(anyString(), any(), eq(Map.class)))
                .thenThrow(new RestClientException("down"));

        BotRoomCreateRequest createRequest = new BotRoomCreateRequest();
        createRequest.setBetToken(100L);
        createRequest.setBotLevel("hard");
        botRoomService.create(currentUser, createRequest);
        BotRoomStartResponse start = botRoomService.start(currentUser);

        BotRoomAttackRequest attackRequest = new BotRoomAttackRequest();
        attackRequest.setCards(start.getTable().isEmpty() ? List.of(start.getUserCards().get(0)) : List.of());

        BotRoomAttackResponse response = botRoomService.attack(currentUser, attackRequest);
        assertNotNull(response);
    }

    @Test
    void create_shouldRejectInvalidLevel() {
        BotRoomCreateRequest createRequest = new BotRoomCreateRequest();
        createRequest.setBetToken(100L);
        createRequest.setBotLevel("invalid");
        assertThrows(BadRequestException.class, () -> botRoomService.create(currentUser, createRequest));
    }
}

