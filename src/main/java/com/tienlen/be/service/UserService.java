package com.tienlen.be.service;

import com.tienlen.be.dto.response.*;
import com.tienlen.be.entity.User;
import com.tienlen.be.exception.BadRequestException;
import com.tienlen.be.repository.MatchParticipantRepository;
import com.tienlen.be.repository.TransactionRepository;
import com.tienlen.be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final MatchParticipantRepository matchParticipantRepository;
    private final TransactionRepository transactionRepository;

    public User getByUserId(Long userId){
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("USER_NOT_FOUND"));
    }

    public List<MatchParticipantResponse> getMatchHistory(Long userId) {
        return matchParticipantRepository.findByUserId(userId).stream()
                .map(mp -> MatchParticipantResponse.builder()
                        .id(mp.getId())
                        .userId(mp.getUserId())
                        .name(mp.getName())
                        .rank(mp.getRank())
                        .tokenChange(mp.getTokenChange())
                        .match(MatchResponse.builder()
                                .id(mp.getMatch().getId())
                                .roomType(mp.getMatch().getRoomType())
                                .betToken(mp.getMatch().getBetToken())
                                .startTime(mp.getMatch().getStartTime())
                                .endTime(mp.getMatch().getEndTime())
                                .winners(mp.getMatch().getWinners())
                                .build())
                        .build())
                .collect(Collectors.toList());
    }

    public List<TransactionResponse> getTransactionHistory(Long userId) {
        return transactionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(t -> TransactionResponse.builder()
                        .id(t.getId())
                        .userId(t.getUserId())
                        .amount(t.getAmount())
                        .type(t.getType())
                        .description(t.getDescription())
                        .createdAt(t.getCreatedAt())
                        .referenceId(t.getReferenceId())
                        .build())
                .collect(Collectors.toList());
    }

    public User getByAccount(String account){
        return userRepository.findByAccount(account)
                .orElseThrow(() ->
                        new BadRequestException("Không tìm thấy account")
                );
    }

    public List<User> saveAll(List<User> users) {
        return userRepository.saveAll(users);
    }
}
