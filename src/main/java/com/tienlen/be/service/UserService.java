package com.tienlen.be.service;

import com.tienlen.be.entity.User;
import com.tienlen.be.exception.BadRequestException;
import com.tienlen.be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;

    public User getByUserId(Long userId){
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("USER_NOT_FOUND"));
    }

    public User getByAccount(String account){
        return userRepository.findByAccount(account)
                .orElseThrow(() ->
                        new BadRequestException("Không tìm thấy account")
                );
    }
}
