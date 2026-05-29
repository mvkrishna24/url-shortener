package com.vamshi.urlshortener.security;

import com.vamshi.urlshortener.auth.AuthService;
import com.vamshi.urlshortener.auth.dto.AuthResponse;
import com.vamshi.urlshortener.auth.dto.LoginRequest;
import com.vamshi.urlshortener.auth.dto.SignupRequest;
import com.vamshi.urlshortener.entity.User;
import com.vamshi.urlshortener.exception.Exceptions.EmailAlreadyInUseException;
import com.vamshi.urlshortener.repository.UserRepository;
import com.vamshi.urlshortener.user.dto.UserResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @InjectMocks private AuthService authService;

    @Test
    void signup_newEmail_createsAndReturnsUser() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        User saved = User.builder().id(1L).email("new@example.com").build();
        when(userRepository.save(any())).thenReturn(saved);

        UserResponse response = authService.signup(signupOf("new@example.com", "password1"));

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getEmail()).isEqualTo("new@example.com");
    }

    @Test
    void signup_normalizesEmailToLowercase() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        User saved = User.builder().id(2L).email("user@example.com").build();
        when(userRepository.save(any())).thenReturn(saved);

        authService.signup(signupOf("USER@EXAMPLE.COM", "pass12345"));

        verify(userRepository).existsByEmail("user@example.com");
    }

    @Test
    void signup_duplicateEmail_throwsEmailAlreadyInUseException() {
        when(userRepository.existsByEmail("taken@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.signup(signupOf("taken@example.com", "password1")))
                .isInstanceOf(EmailAlreadyInUseException.class);
    }

    @Test
    void login_validCredentials_returnsTokenAndUser() {
        User user = User.builder().id(5L).email("user@example.com").passwordHash("hashed").build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password1", "hashed")).thenReturn(true);
        when(jwtService.generateToken(5L, "user@example.com")).thenReturn("jwt.token.value");
        when(jwtService.getExpirySeconds()).thenReturn(3600L);

        AuthResponse response = authService.login(loginOf("user@example.com", "password1"));

        assertThat(response.getToken()).isEqualTo("jwt.token.value");
        assertThat(response.getUser().getEmail()).isEqualTo("user@example.com");
    }

    @Test
    void login_wrongPassword_throwsBadCredentialsException() {
        User user = User.builder().id(5L).email("user@example.com").passwordHash("hashed").build();
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrongpass", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(loginOf("user@example.com", "wrongpass")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_unknownEmail_throwsBadCredentialsException() {
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginOf("unknown@example.com", "password1")))
                .isInstanceOf(BadCredentialsException.class);
    }

    private static SignupRequest signupOf(String email, String password) {
        SignupRequest r = new SignupRequest();
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }

    private static LoginRequest loginOf(String email, String password) {
        LoginRequest r = new LoginRequest();
        r.setEmail(email);
        r.setPassword(password);
        return r;
    }
}
