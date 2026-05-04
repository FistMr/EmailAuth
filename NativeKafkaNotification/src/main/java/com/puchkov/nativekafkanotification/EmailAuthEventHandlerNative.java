package com.puchkov.nativekafkanotification;

import com.puchkov.nativekafkanotification.dto.VerificationMessage;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
@RequiredArgsConstructor
public class EmailAuthEventHandlerNative {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final AtomicBoolean running = new AtomicBoolean(false);
    private Consumer<String, VerificationMessage> consumer;
    private ExecutorService executorService;
    private final KafkaProperties kafkaProperties;

    @PostConstruct
    public void init() {
        Properties props = new Properties();
        props.putAll(kafkaProperties.buildConsumerProperties());
        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Collections.singletonList("email-auth-event-topic"));
        startConsumer();
    }

    public void startConsumer() {
        running.set(true);
        executorService = Executors.newSingleThreadExecutor();

        executorService.submit(() -> {
            while (running.get()) {
                try {
                    ConsumerRecords<String, VerificationMessage> consumerRecords = consumer.poll(Duration.ofMillis(100));

                    consumerRecords.forEach(records -> {
                        VerificationMessage message = records.value();
                        logger.info("Отправлено письмо на почту: {}, с кодом: {}",
                                message.getEmail(), message.getCode());
                    });

                } catch (Exception e) {
                    logger.error("Ошибка при обработке сообщений", e);
                }
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        running.set(false);
        if (executorService != null) {
            executorService.shutdown();
        }
        if (consumer != null) {
            consumer.close();
        }
    }

}
