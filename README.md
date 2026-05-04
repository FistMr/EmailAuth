# EmailAuth

## 📖 Описание

**EmailAuth** — это микросервисная система аутентификации пользователей по email на основе одноразовых кодов подтверждения (passwordless authentication). После регистрации пользователю отправляется 6-значный код, ввод которого подтверждает email и возвращает JWT-токен для доступа к защищённым ресурсам.

Проект построен на Spring Boot 3 и демонстрирует двухуровневую интеграцию с Apache Kafka:
- через высокоуровневый Spring Kafka (`@KafkaListener`);
- через низкоуровневый нативный `KafkaConsumer` API.

### Основные возможности

- Регистрация пользователя по email с генерацией 6-значного кода (TTL 15 минут).
- Подтверждение email и выдача JWT-токена.
- Доступ к защищённым эндпоинтам по JWT (Bearer-токен).
- Асинхронная отправка событий в Kafka (топик `email-auth-event-topic`) при регистрации.
- Два варианта обработки событий: Spring Kafka и нативный Kafka Consumer.
- Кластер Kafka из 3 брокеров (KRaft mode) и PostgreSQL — поднимаются через Docker Compose.

---

## 🛠️ Технологический стек

| Категория | Технология |
|---|---|
| Язык | Java 17 |
| Фреймворк | Spring Boot 3.5.4 / 3.5.5 |
| Сборка | Maven (Wrapper) |
| Безопасность | Spring Security, JWT (jjwt 0.12.3), BCrypt |
| Хранилище | PostgreSQL 16, Spring Data JPA / Hibernate |
| Брокер сообщений | Apache Kafka (KRaft, 3 узла), Spring Kafka |
| Документация API | SpringDoc OpenAPI (Swagger UI) 2.8.9 |
| Валидация | Jakarta Validation (Hibernate Validator) |
| Утилиты | Lombok |
| Тестирование | JUnit 5, Mockito, Testcontainers (PostgreSQL + Kafka), Spring Kafka Test, Awaitility, Spring Security Test |
| Инфраструктура | Docker, Docker Compose |

---

## 📁 Структура проекта

```
EmailAuth/
├── docker-compose.yml              # Kafka-кластер (3 брокера) + PostgreSQL
├── kafka_data/                     # Volume для данных Kafka
│
├── AuthService/                    # 🔐 Сервис аутентификации (REST API)
│   ├── pom.xml
│   └── src/main/java/com/puchkov/authservice/
│       ├── AuthServiceApplication.java
│       ├── config/                 # SecurityConfig, KafkaConfig
│       ├── controller/             # AuthController, ProtectedController
│       ├── dto/                    # Request/Response DTO
│       ├── entity/                 # User (UserDetails)
│       ├── exception/              # GlobalExceptionHandler, custom exceptions
│       ├── filter/                 # JwtAuthenticationFilter
│       ├── repository/             # UserRepository (Spring Data JPA)
│       └── service/                # AuthService, JwtService, UserDetailsServiceImpl
│
├── NotificationService/            # 📨 Подписчик на Kafka (Spring Kafka)
│   ├── pom.xml
│   └── src/main/java/com/puchkov/notificationservice/
│       ├── NotificationServiceApplication.java
│       ├── dto/                    # VerificationMessage
│       └── handler/                # EmailAuthEventHandler (@KafkaListener)
│
└── NativeKafkaNotification/        # 📨 Подписчик на Kafka (нативный API)
    ├── pom.xml
    └── src/main/java/com/puchkov/nativekafkanotification/
        ├── NativeKafkaNotificationApplication.java
        ├── dto/                    # VerificationMessage
        └── EmailAuthEventHandlerNative.java   # KafkaConsumer + ExecutorService
```

---

## ✅ Требования

- **Java 17+** (JDK)
- **Maven 3.8+** (или встроенный `./mvnw`)
- **Docker** и **Docker Compose** (для PostgreSQL и Kafka-кластера)
- Открытые порты на хосте: `5432` (PostgreSQL), `9092`, `9094`, `9096` (Kafka брокеры)

---

## 📦 Установка

```bash
# 1. Клонировать репозиторий
git clone <repository-url>
cd EmailAuth

# 2. Поднять инфраструктуру (Kafka + PostgreSQL)
docker-compose up -d

# 3. Собрать все сервисы
cd AuthService && ./mvnw clean package -DskipTests && cd ..
cd NotificationService && ./mvnw clean package -DskipTests && cd ..
cd NativeKafkaNotification && ./mvnw clean package -DskipTests && cd ..
```

> На Windows используйте `mvnw.cmd` вместо `./mvnw`.

---

## ⚙️ Конфигурация

Конфигурация хранится в `src/main/resources/application.yml` каждого сервиса. Текущие значения зашиты в коде (TODO: вынести в переменные окружения / `.env`).

### AuthService (`AuthService/src/main/resources/application.yml`)

| Параметр | По умолчанию | Описание |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/auth_db` | JDBC URL PostgreSQL |
| `spring.datasource.username` | `admin` | Пользователь БД |
| `spring.datasource.password` | `admin` | Пароль БД |
| `spring.jpa.hibernate.ddl-auto` | `update` | Стратегия миграции схемы |
| `spring.kafka.producer.bootstrap-servers` | `localhost:9092,localhost:9094,localhost:9096` | Адреса брокеров Kafka |
| `server.port` | `0` | Случайный свободный порт (TODO: задать фиксированный) |
| `jwt.secret` | `<base64-key>` | Base64-секрет для подписи JWT (HMAC-SHA) |
| `jwt.expiration` | `3600000` | Время жизни токена в миллисекундах (1 час) |
| `topic.name` | `email-auth-event-topic` | Имя топика Kafka |

### NotificationService / NativeKafkaNotification

| Параметр | По умолчанию | Описание |
|---|---|---|
| `spring.kafka.consumer.bootstrap-servers` | `localhost:9092,localhost:9094,localhost:9096` | Адреса брокеров Kafka |
| `spring.kafka.consumer.group-id` | `notification-group` | Идентификатор consumer-группы |
| `server.port` | `0` | Случайный свободный порт |

> ⚠️ **TODO**: вынести JWT-секрет, креды БД и адреса Kafka в переменные окружения. В репозитории отсутствует файл `.env.example`.

---

## 🚀 Запуск

### 1. Поднять инфраструктуру

```bash
docker-compose up -d
```

Сервисы Docker Compose:
- `kafka-1`, `kafka-2`, `kafka-3` — Kafka-кластер (KRaft) на портах 9092/9094/9096.
- `postgres` — PostgreSQL 16 на порту 5432 (БД `auth_db`, юзер `admin/admin`).

### 2. Запустить микросервисы (режим разработки)

В трёх отдельных терминалах:

```bash
# Терминал 1 — AuthService (REST API)
cd AuthService
./mvnw spring-boot:run

# Терминал 2 — NotificationService (Spring Kafka listener)
cd NotificationService
./mvnw spring-boot:run

# Терминал 3 — NativeKafkaNotification (нативный Kafka Consumer)
cd NativeKafkaNotification
./mvnw spring-boot:run
```

> Порт `server.port=0` означает, что Spring Boot выбирает случайный свободный порт. Реальный порт смотрите в логах при старте AuthService (строка `Tomcat started on port(s): XXXXX`).

### 3. Production-режим

```bash
java -jar AuthService/target/AuthService-0.0.1-SNAPSHOT.jar
java -jar NotificationService/target/NotificationService-0.0.1-SNAPSHOT.jar
java -jar NativeKafkaNotification/target/NativeKafkaNotification-0.0.1-SNAPSHOT.jar
```

> ⚠️ **TODO**: добавить `Dockerfile` для каждого сервиса и расширить `docker-compose.yml`, чтобы поднимать всё одной командой.

### 4. Swagger UI

После старта AuthService документация доступна по адресу:
```
http://localhost:<port>/swagger-ui/index.html
http://localhost:<port>/v3/api-docs
```

---

## 📡 API Документация

Базовый URL: `http://localhost:<port>` (порт назначается случайно).

### 🔐 Auth — регистрация и подтверждение

#### `POST /auth/register`

Регистрация пользователя по email. Создаёт запись в БД с 6-значным кодом подтверждения (TTL 15 минут) и публикует событие в Kafka-топик `email-auth-event-topic`.

**Тело запроса** (`application/json`):

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `email` | string (email) | да | Email пользователя |

**Ответы:**

| Код | Тело | Описание |
|---|---|---|
| `200 OK` | пусто | Пользователь создан, код отправлен в Kafka |
| `409 Conflict` | `ErrorResponse` | Пользователь с таким email уже существует |
| `400 Bad Request` | `{"email": "must be a well-formed email address"}` | Невалидный email |

**Пример запроса:**

```bash
curl -X POST http://localhost:8080/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com"}'
```

**Пример ответа (409):**

```json
{
  "timestamp": "2026-05-04T12:30:45",
  "status": 409,
  "error": "Conflict",
  "message": "User already exists",
  "path": "/auth/register"
}
```

---

#### `POST /auth/verify`

Подтверждение email по коду. При успехе выдаёт JWT-токен (HMAC-SHA, TTL 1 час).

**Тело запроса** (`application/json`):

| Поле | Тип | Обязательное | Описание |
|---|---|---|---|
| `email` | string (email) | да | Email пользователя |
| `code` | string | да | 6-значный код из Kafka-сообщения |

**Ответы:**

| Код | Тело | Описание |
|---|---|---|
| `200 OK` | `AuthResponse` | Возвращает JWT-токен |
| `404 Not Found` | `ErrorResponse` | Пользователь не найден |
| `400 Bad Request` | `ErrorResponse` | Неверный код или код истёк |

**Пример запроса:**

```bash
curl -X POST http://localhost:8080/auth/verify \
  -H "Content-Type: application/json" \
  -d '{"email": "user@example.com", "code": "123456"}'
```

**Пример успешного ответа (200):**

```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ1c2VyQGV4YW1wbGUuY29tIiwiaWF0IjoxNzE..."
}
```

**Пример ошибки (400):**

```json
{
  "timestamp": "2026-05-04T12:35:00",
  "status": 400,
  "error": "Bad Request",
  "message": "Invalid verification code",
  "path": "/auth/verify"
}
```

---

### 🧪 Test — публичные и защищённые эндпоинты

#### `GET /test/public`

Публичный эндпоинт, не требует аутентификации.

**Ответ (200):**

```
Это публичный эндпоинт, доступен всем
```

**Пример:**

```bash
curl http://localhost:8080/test/public
```

---

#### `GET /test/protected`

Защищённый эндпоинт. Требует JWT-токен в заголовке `Authorization: Bearer <token>`.

**Заголовки:**

| Заголовок | Значение | Обязательный |
|---|---|---|
| `Authorization` | `Bearer <JWT>` | да |

**Ответы:**

| Код | Тело | Описание |
|---|---|---|
| `200 OK` | строка с email из токена | Успешная аутентификация |
| `401/403` | — | Отсутствующий или невалидный токен |
| `500` | `ErrorResponse` | Невалидный токен (бросается `TokenInvalidException`, TODO: маппинг на 401) |

**Пример запроса:**

```bash
curl http://localhost:8080/test/protected \
  -H "Authorization: Bearer eyJhbGciOiJIUzUxMiJ9..."
```

**Пример ответа (200):**

```
Это защищённый эндпоинт. Вы аутентифицированы по почте: user@example.com
```

---

### 📊 Модель данных

**Таблица `users`** (управляется Hibernate `ddl-auto: update`):

| Колонка | Тип | Описание |
|---|---|---|
| `id` | `BIGINT` (PK, auto-increment) | Идентификатор |
| `email` | `VARCHAR` (unique) | Email пользователя |
| `verified` | `BOOLEAN` | Флаг подтверждённой почты |
| `verification_code` | `VARCHAR` | Текущий 6-значный код |
| `code_expiration` | `TIMESTAMP` | Срок действия кода (15 мин) |

> ⚠️ **TODO**: подключить Flyway/Liquibase. Сейчас используется автогенерация схемы Hibernate.

### 📨 Kafka-событие

**Топик:** `email-auth-event-topic` (3 партиции, репликация 3, `min.insync.replicas=2`)
**Ключ:** email (String)
**Значение:** JSON

```json
{
  "email": "user@example.com",
  "code": "123456"
}
```

Подписчики:
- `NotificationService` — group `notification-group`, `@KafkaListener` логирует факт отправки.
- `NativeKafkaNotification` — group `notification-group`, нативный `KafkaConsumer` в отдельном потоке логирует факт отправки.

> ⚠️ **TODO**: оба сервиса используют одну и ту же consumer-group и сейчас будут конкурировать за партиции. Реальной отправки email нет — только логирование.

---

## 🧪 Тестирование

В каждом сервисе тесты запускаются через Maven Wrapper.

```bash
# AuthService — unit-тесты (Mockito) + интеграционные (Testcontainers: PostgreSQL + Kafka)
cd AuthService && ./mvnw test

# NotificationService
cd NotificationService && ./mvnw test

# NativeKafkaNotification
cd NativeKafkaNotification && ./mvnw test
```

Покрытие AuthService:
- **Unit:** `AuthServiceImplTest` — регистрация, валидация кода, истёкший код, отсутствующий пользователь.
- **Integration:** `AuthServiceApplicationTests` — полный цикл REST + Kafka + PostgreSQL через Testcontainers, проверка отправки сообщения в топик через Awaitility.

> Для интеграционных тестов требуется работающий Docker (Testcontainers поднимает PostgreSQL и Kafka автоматически).

---