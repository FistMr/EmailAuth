package com.puchkov.notificationservice.handler;

import com.puchkov.notificationservice.dto.VerificationMessage;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@KafkaListener(topics = "email-auth-event-topic", groupId = "notification-group")
public class EmailAuthEventHandler {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @KafkaHandler
    public void handle(VerificationMessage verificationMessage) {
        logger.info("Отправлено письмо на почту: {}, с кодом: {}", verificationMessage.getEmail(), verificationMessage.getCode());
    }
}
