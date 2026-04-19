package com.tienlen.be.service;

import java.util.Map;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.tienlen.be.dto.request.GoogleLoginRequest;
import com.tienlen.be.dto.request.LoginRequest;
import com.tienlen.be.dto.request.RegisterRequest;
import com.tienlen.be.dto.response.LoginResponse;
import com.tienlen.be.dto.response.UserResponse;
import com.tienlen.be.entity.User;
import com.tienlen.be.exception.BadRequestException;
import com.tienlen.be.repository.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final int DEFAULT_TOKEN_BALANCE = 1000;

    public void register(RegisterRequest request) {

        if (request.getAccount() == null ||
                request.getName() == null ||
                request.getPassword() == null ||
                request.getRePassword() == null) {
            throw new BadRequestException("Vui lòng nhập đầy đủ thông tin ");
        }

        if (request.getAccount().length() < 6 ||
                request.getPassword().length() < 6) {
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
        user.setTokenBalance(1000);
        user.setPassword(passwordEncoder.encode(request.getPassword()));

        userRepository.save(user);
    }

    public LoginResponse login(LoginRequest request) {

        if (request.getAccount() == null || request.getPassword() == null) {
            throw new BadRequestException("Vui lòng nhập đầy đủ thông tin");
        }

        User user = userRepository.findByAccount(request.getAccount())
                .orElseThrow(() -> new BadRequestException("Tài khoản hoặc mật khẩu không đúng"));

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword())) {
            throw new BadRequestException("Tài khoản hoặc mật khẩu không đúng");
        }

        jwtService.revokeAllUserTokens(user);
        String token = jwtService.generateToken(user);

        return new LoginResponse(token, new UserResponse(user));
    }

    public LoginResponse loginWithGoogle(GoogleLoginRequest request) {
        if (request.getToken() == null || request.getToken().isEmpty()) {
            throw new BadRequestException("Token Google không hợp lệ");
        }

        RestTemplate restTemplate = new RestTemplate();
        String url = "https://oauth2.googleapis.com/tokeninfo?id_token=" + request.getToken();

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = restTemplate.getForEntity(url, Map.class).getBody();
            if (payload == null || !payload.containsKey("email")) {
                throw new BadRequestException("Không thể lấy thông tin từ Google");
            }
            System.out.println(payload);
            String email = (String) payload.get("email");
            String name = (String) payload.get("name");
            String avatarUrl = (String) payload.get("picture");

            String account = email;

            User user = userRepository.findByAccount(account).orElse(null);
            if (user == null) {
                user = new User();
                user.setAccount(account);
                user.setName(name);
                user.setTokenBalance(DEFAULT_TOKEN_BALANCE);
                user.setAvatarUrl(avatarUrl);
                user.setPassword(passwordEncoder.encode(java.util.UUID.randomUUID().toString()));
                user = userRepository.save(user);
            }

            jwtService.revokeAllUserTokens(user);
            String token = jwtService.generateToken(user);
            return new LoginResponse(token, new UserResponse(user));

        } catch (Exception e) {
            e.printStackTrace();
            throw new BadRequestException("Xác nhận Google Token thất bại. Vui lòng thử lại.");
        }
    }

    public void logout(UserResponse userResponse) {
        User user = userRepository.findById(userResponse.getId())
                .orElseThrow(() -> new BadRequestException("User not found"));
        jwtService.revokeAllUserTokens(user);
    }
}
