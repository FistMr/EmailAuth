package com.puchkov.notificationservice;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.puchkov.notificationservice.dto.VerificationMessage;
import com.puchkov.notificationservice.handler.EmailAuthEventHandler;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext
class NotificationServiceApplicationTests {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:latest"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private KafkaTemplate<String, VerificationMessage> kafkaTemplate;

    private ListAppender<ILoggingEvent> listAppender;

    @BeforeEach
    void setUp() {
        Logger logger = (Logger) LoggerFactory.getLogger(EmailAuthEventHandler.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @Test
    void shouldLogMessageWhenKafkaMessageReceived() {
        String topic = "email-auth-event-topic";
        VerificationMessage testMessage = VerificationMessage.builder()
                .email("test@example.com")
                .code("123456")
                .build();

        String expectedLog = "Отправлено письмо на почту: test@example.com, с кодом: 123456";

        kafkaTemplate.send(new ProducerRecord<>(topic, testMessage));

        await().atMost(10, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    List<ILoggingEvent> logsList = listAppender.list;
                    boolean logFound = logsList.stream()
                            .anyMatch(event ->
                                    event.getLevel() == Level.INFO &&
                                            event.getFormattedMessage().contains(expectedLog));

                    assertTrue(logFound, "Expected log message not found: " + expectedLog);
                });
    }
}
