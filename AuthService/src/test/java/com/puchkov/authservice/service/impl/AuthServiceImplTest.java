package com.puchkov.authservice.service.impl;

import com.puchkov.authservice.dto.VerificationMessage;
import com.puchkov.authservice.entity.User;
import com.puchkov.authservice.exception.UserAlreadyExistsException;
import com.puchkov.authservice.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private Random random;

    @Mock
    private KafkaTemplate<String, VerificationMessage> kafkaTemplate;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthServiceImpl authService;

    private final String topicName = "email-auth-event-topic";
    private final String testEmail = "test@example.com";
    private final String verificationCode = "123456";

    @BeforeEach
    void setUp() throws Exception {
        authService = new AuthServiceImpl(userRepository, kafkaTemplate, jwtService);

        Field topicField = AuthServiceImpl.class.getDeclaredField("topicName");
        topicField.setAccessible(true);
        topicField.set(authService, topicName);
    }

    @Test
    void registerUser_ShouldSaveUserAndSendVerificationCode() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());

        authService.registerUser(testEmail);

        verify(userRepository).findByEmail(testEmail);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertEquals(testEmail, savedUser.getEmail());
        assertEquals(6, savedUser.getVerificationCode().length()); // Проверяем длину
        assertTrue(savedUser.getVerificationCode().matches("\\d{6}")); // Проверяем формат
        assertTrue(savedUser.getCodeExpiration().isAfter(LocalDateTime.now()));
        assertFalse(savedUser.isVerified());

        verify(kafkaTemplate).send(eq(topicName), eq(testEmail), any(VerificationMessage.class));
    }

    @Test
    void registerUser_WhenUserAlreadyExists_ShouldThrowException() {
        User existingUser = User.builder().email(testEmail).build();
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(existingUser));

        assertThrows(UserAlreadyExistsException.class, () -> {
            authService.registerUser(testEmail);
        });

        verify(userRepository, never()).save(any(User.class));
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any(VerificationMessage.class));
    }

    @Test
    void verifyCode_WithValidCode_ShouldVerifyUserAndReturnToken() {
        User user = User.builder()
                .email(testEmail)
                .verificationCode(verificationCode)
                .codeExpiration(LocalDateTime.now().plusMinutes(15))
                .verified(false)
                .build();

        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(user));
        when(jwtService.generateToken(testEmail)).thenReturn("jwt-token");

        String result = authService.verifyCode(testEmail, verificationCode);

        assertEquals("jwt-token", result);
        assertTrue(user.isVerified());
        verify(userRepository).save(user);
        verify(jwtService).generateToken(testEmail);
    }

    @Test
    void verifyCode_WithInvalidCode_ShouldThrowException() {
        User user = User.builder()
                .email(testEmail)
                .verificationCode(verificationCode)
                .codeExpiration(LocalDateTime.now().plusMinutes(15))
                .build();

        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class, () -> {
            authService.verifyCode(testEmail, "wrong-code");
        });

        assertFalse(user.isVerified());
        verify(userRepository, never()).save(any(User.class));
        verify(jwtService, never()).generateToken(anyString());
    }

    @Test
    void verifyCode_WithExpiredCode_ShouldThrowException() {
        User user = User.builder()
                .email(testEmail)
                .verificationCode(verificationCode)
                .codeExpiration(LocalDateTime.now().minusMinutes(1)) // expired
                .build();

        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.of(user));

        assertThrows(IllegalArgumentException.class, () -> {
            authService.verifyCode(testEmail, verificationCode);
        });

        assertFalse(user.isVerified());
        verify(userRepository, never()).save(any(User.class));
        verify(jwtService, never()).generateToken(anyString());
    }

    @Test
    void verifyCode_WhenUserNotFound_ShouldThrowException() {
        when(userRepository.findByEmail(testEmail)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> {
            authService.verifyCode(testEmail, verificationCode);
        });

        verify(userRepository, never()).save(any(User.class));
        verify(jwtService, never()).generateToken(anyString());
    }
}