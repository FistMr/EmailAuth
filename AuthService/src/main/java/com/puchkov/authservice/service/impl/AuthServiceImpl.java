package com.puchkov.authservice.service.impl;

import com.puchkov.authservice.dto.VerificationMessage;
import com.puchkov.authservice.entity.User;
import com.puchkov.authservice.exception.UserAlreadyExistsException;
import com.puchkov.authservice.repository.UserRepository;
import com.puchkov.authservice.service.AuthService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final Random random = new Random();
    private final KafkaTemplate<String, VerificationMessage> kafkaTemplate;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;


    @Override
    public void registerUser(String email) {
        if (userRepository.findByEmail(email).isPresent()) {
            throw new UserAlreadyExistsException("User already exists");
        }
        String verificationCode = generateVerificationCode();
        User user = User.builder()
                .email(email)
                .verificationCode(verificationCode)
                .codeExpiration(LocalDateTime.now().plusMinutes(15))
                .build();
        userRepository.save(user);

        sendVerificationCode(email, verificationCode);

    }

    @Override
    @Transactional
    public String verifyCode(String email, String code) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));

        if (!user.getVerificationCode().equals(code)) {
            throw new IllegalArgumentException("Invalid verification code");
        }

        if (user.getCodeExpiration().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Verification code expired");
        }

        user.setVerified(true);
        userRepository.save(user);

        return jwtService.generateToken(email);
    }

    private void sendVerificationCode(String email, String verificationCode) {
        VerificationMessage verificationMessage = new VerificationMessage(email, verificationCode);
        kafkaTemplate.send("email-auth-event-topic", email, verificationMessage);
    }

    private String generateVerificationCode() {
        return String.format("%06d", random.nextInt(999999));
    }
}
