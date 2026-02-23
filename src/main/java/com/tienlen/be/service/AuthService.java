package com.tienlen.be.service;

import com.tienlen.be.dto.request.LoginRequest;
import com.tienlen.be.dto.request.RegisterRequest;
import com.tienlen.be.dto.response.LoginResponse;
import com.tienlen.be.dto.response.UserResponse;
import com.tienlen.be.entity.User;
import com.tienlen.be.exception.BadRequestException;
import com.tienlen.be.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public void register(RegisterRequest request) {

        if (
                request.getAccount() == null ||
                        request.getName() == null ||
                        request.getPassword() == null ||
                        request.getRePassword() == null
        ) {
            throw new BadRequestException("Vui lòng nhập đầy đủ thông tin ");
        }

        if (
                request.getAccount().length() < 6 ||
                        request.getPassword().length() < 6
        ) {
            throw new BadRequestException("Tài khoản và mật khẩu phải dài hơn 6 ký tự");
        }

        if (!request.getPassword().equals(request.getRePassword())) {
            throw new BadRequestException("Mật khẩu chưa khớp");
        }

        if (userRepository.existsByAccount(request.getAccount())) {
            throw new BadRequestException("Tài khoản đã tồn tại");
        }

        User user = new User();
        user.setAccount(request.getAccount());
        user.setName(request.getName());
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        userRepository.save(user);
    }

    public LoginResponse login(LoginRequest request) {

        if (request.getAccount() == null || request.getPassword() == null) {
            throw new BadRequestException("Vui lòng nhập đầy đủ thông tin");
        }

        User user = userRepository.findByAccount(request.getAccount())
                .orElseThrow(() ->
                        new BadRequestException("Tài khoản hoặc mật khẩu không đúng")
                );

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )) {
            throw new BadRequestException("Tài khoản hoặc mật khẩu không đúng");
        }

        String token = jwtService.generateToken(user);

        return new LoginResponse(token, new UserResponse(user));
    }
}
