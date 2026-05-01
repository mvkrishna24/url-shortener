package com.vamshi.urlshortener.auth;

import com.vamshi.urlshortener.auth.dto.AuthResponse;
import com.vamshi.urlshortener.auth.dto.LoginRequest;
import com.vamshi.urlshortener.auth.dto.SignupRequest;
import com.vamshi.urlshortener.security.JwtService;
import com.vamshi.urlshortener.security.SecurityConstants;
import com.vamshi.urlshortener.user.User;
import com.vamshi.urlshortener.user.UserRepository;
import com.vamshi.urlshortener.user.dto.UserResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public UserResponse signup(SignupRequest request) {
        String email = request.getEmail().toLowerCase().strip();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already in use");
        }
        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .build();
        user = userRepository.save(user);
        return new UserResponse(user.getId(), user.getEmail());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().toLowerCase().strip();
        log.info("Authentication attempt for email: {}", email);
        User user = userRepository.findByEmail(email)
                .filter(u -> passwordEncoder.matches(request.getPassword(), u.getPasswordHash()))
                .orElseThrow(() -> new BadCredentialsException(SecurityConstants.INVALID_CREDENTIALS_MSG));
        
        log.info("Authentication successful for email: {}", email);
        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(token, jwtService.getExpirySeconds(), new UserResponse(user.getId(), user.getEmail()));
    }
}