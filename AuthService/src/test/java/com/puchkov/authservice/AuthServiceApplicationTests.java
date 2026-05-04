package com.puchkov.authservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.puchkov.authservice.dto.AuthResponse;
import com.puchkov.authservice.dto.RegisterRequest;
import com.puchkov.authservice.dto.VerificationMessage;
import com.puchkov.authservice.dto.VerifyRequest;
import com.puchkov.authservice.entity.User;
import com.puchkov.authservice.repository.UserRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthServiceApplicationTests {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka:latest"));

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "update");
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Consumer<String, VerificationMessage> testConsumer;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        setupKafkaConsumer();

    }

    private void setupKafkaConsumer() {
        if (testConsumer != null) {
            testConsumer.close();
        }

        Map<String, Object> consumerProps = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-group",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class,
                JsonDeserializer.TRUSTED_PACKAGES, "com.puchkov.authservice.dto"
        );

        DefaultKafkaConsumerFactory<String, VerificationMessage> consumerFactory =
                new DefaultKafkaConsumerFactory<>(consumerProps);
        testConsumer = consumerFactory.createConsumer();
        testConsumer.subscribe(Collections.singletonList("email-auth-event-topic"));

        testConsumer.poll(Duration.ofMillis(100));
        testConsumer.commitSync();
    }


    @Test
    void register_ShouldCreateUserAndReturnOkAndSendMessageToKafka() throws Exception {
        RegisterRequest request = new RegisterRequest("test@example.com");

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        Optional<User> savedUser = userRepository.findByEmail("test@example.com");
        Assertions.assertTrue(savedUser.isPresent());
        Assertions.assertEquals("test@example.com", savedUser.get().getEmail());
        Assertions.assertEquals(6, savedUser.get().getVerificationCode().length());

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> {
                    ConsumerRecord<String, VerificationMessage> singleRecord =
                            KafkaTestUtils.getSingleRecord(testConsumer, "email-auth-event-topic");

                    Assertions.assertNotNull(singleRecord);
                    Assertions.assertEquals("test@example.com", singleRecord.key());
                    Assertions.assertEquals("test@example.com", singleRecord.value().getEmail());
                    Assertions.assertEquals(savedUser.get().getVerificationCode(), singleRecord.value().getCode());
                    Assertions.assertNotNull(singleRecord.value().getCode());
                    Assertions.assertEquals(6, singleRecord.value().getCode().length());
                });
    }

    @Test
    void register_WhenUserAlreadyExists_ShouldThrowUserAlreadyExistsException() throws Exception {
        User existingUser = User.builder()
                .email("existing@example.com")
                .verificationCode("123456")
                .codeExpiration(java.time.LocalDateTime.now().plusMinutes(15))
                .build();
        userRepository.save(existingUser);

        RegisterRequest request = new RegisterRequest("existing@example.com");

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("User already exists"));
    }

    @Test
    void verify_WithNonExistentUser_ShouldReturnNotFound() throws Exception {
        VerifyRequest request = new VerifyRequest("nonexistent@example.com", "123456");

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("User not found"));
    }

    @Test
    void verify_WithInvalidCode_ShouldReturnBadRequest() throws Exception {
        User user = User.builder()
                .email("invalid@example.com")
                .verificationCode("123456")
                .codeExpiration(LocalDateTime.now().plusMinutes(15))
                .verified(false)
                .build();
        userRepository.save(user);

        VerifyRequest request = new VerifyRequest("invalid@example.com", "wrong_code");

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid verification code"));
    }

    @Test
    void verify_WithValidCode_ShouldReturnToken() throws Exception {
        User user = User.builder()
                .email("valid@example.com")
                .verificationCode("123456")
                .codeExpiration(LocalDateTime.now().plusMinutes(15))
                .verified(false)
                .build();
        userRepository.save(user);

        VerifyRequest request = new VerifyRequest("valid@example.com", "123456");

        mockMvc.perform(MockMvcRequestBuilders.post("/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String content = result.getResponse().getContentAsString();
                    AuthResponse response = objectMapper.readValue(content, AuthResponse.class);
                    Assertions.assertNotNull(response.getToken());
                });
    }

}
