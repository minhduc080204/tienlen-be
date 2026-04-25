# Tien Len Backend

Spring Boot backend for a **Tien Len (Vietnamese card game)** platform with:
- JWT authentication
- Real-time multiplayer rooms over WebSocket
- Player-vs-Bot mode (easy/medium/hard, with optional external AI model)
- Match history and token transaction tracking

## Tech Stack

- Java 17
- Spring Boot 3
- Spring Security + JWT
- Spring Data JPA
- MySQL 8
- Native WebSocket (no STOMP)
- Maven Wrapper

## Project Features

- **Auth**
  - Register, login, logout
  - Google login via Google `id_token`
- **User**
  - Profile (`/me`)
  - Match history
  - Transaction history
- **Room (Multiplayer)**
  - Create room, join room, quick join
  - Real-time game actions via WebSocket
- **Bot Room**
  - Create/start PvB game
  - Attack/pass turns
  - Optional hard-mode inference via external model service (`bot.external.url`)

## Requirements

- JDK 17+
- Docker (recommended for MySQL)

## Run MySQL with Docker

```bash
docker compose up -d
```

Default DB in `docker-compose.yml`:
- Host: `localhost:3306`
- Database: `tienlen`
- User: `tienlen_user`
- Password: `123456`

## Configuration

Main config file: `src/main/resources/application.properties`

Important properties:

```properties
server.port=8080
spring.datasource.url=jdbc:mysql://localhost:3306/tienlen?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh
spring.datasource.username=tienlen_user
spring.datasource.password=123456
spring.jpa.hibernate.ddl-auto=update
jwt.secret=<base64-secret>
bot.external.url=http://127.0.0.1:8000/predict
```

> For production/public deployment, replace credentials and JWT secret with secure values.

## Run the Application

### Windows
```bash
mvnw.cmd spring-boot:run
```

### macOS / Linux
```bash
./mvnw spring-boot:run
```

App base URL: `http://localhost:8080`

## Build and Test

```bash
./mvnw clean test
./mvnw clean package
```

On Windows, replace `./mvnw` with `mvnw.cmd`.

## REST API Overview

### Auth (`/api/auth`)
- `GET /test`
- `POST /register`
- `POST /login`
- `POST /google`
- `POST /logout` (requires Bearer token)

### User (`/api/user`) *(expects Bearer token)*
- `GET /me`
- `GET /matches`
- `GET /transactions`

### Room (`/api/room`)
- `GET /`
- `GET /info`
- `POST /create`
- `POST /join`
- `POST /quick-join`

### Bot Room (`/api/room/bot`) *(requires Bearer token)*
- `POST /create`
- `POST /start`
- `POST /attack`

## WebSocket API

Endpoint:

```text
ws://localhost:8080/ws/room?roomId=<roomId>&token=<jwt>
```

Incoming message format:

```json
{
  "roomId": 1,
  "action": "ATTACK",
  "data": ["31", "32"]
}
```

Supported actions include:
- `CHAT`
- `READY`, `UNREADY`
- `ATTACK`, `PASS`

Server emits events such as:
- `JOIN_ROOM`, `LEFT_ROOM`
- `START_COUNTDOWN`, `START_GAME`
- `NEXT_TURN`, `TIMEOUT`
- `SYNC_DATA`, `GAME_MESSAGE`
- `GAME_FINISHED`

## Suggested Project Structure

```text
src/main/java/com/tienlen/be
├── config
├── controller
├── dto
├── entity
├── exception
├── handler
├── model
├── repository
├── security
├── service
└── websocket
```

## Notes for Public Repository

- This repository currently contains local/dev defaults (DB credentials, JWT secret, CORS origins).
- Before production release, move secrets to environment variables or secret manager.
- Restrict CORS and secure infrastructure-level settings.
