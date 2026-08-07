# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
./gradlew build           # Full build (compile + test)
./gradlew compileJava     # Compile only
./gradlew test            # Run tests
./gradlew bootRun         # Start the app on port 8080
```

Requirements: Java 25 (`gradle.properties` points to `D:/JDK/JDK25`), MySQL 8, Redis.

On first run, execute `src/main/resources/db/init.sql` against a database named `Link_Between_Us` (utf8mb4). Create `src/main/resources/application-local.yaml` with secrets — see `application.yaml` for `${ENV:}` placeholders that need values (`DB_USERNAME`, `DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET`).

## Architecture

**Stack:** Spring Boot 4.1.0 → MyBatis-Plus 3.5.16 → MySQL 8 + Redis (Lettuce). STOMP over WebSocket for real-time messaging. No frontend app — `test-ws.html` at the project root is the manual test client for REST + STOMP flows.

### Auth

Custom JWT auth, **not Spring Security** (spring-security-crypto is only used for `BCryptPasswordEncoder`).

- `JwtAuthenticationFilter` — a `OncePerRequestFilter` that intercepts every request. Whitelisted paths: `/api/auth/`, `/ws`, `/error`, plus all `OPTIONS` preflights. On pass, stores `account` in `request.setAttribute("account")`. Controllers consume it via `@RequestAttribute("account")`.
- `AuthHandshakeInterceptor` — validates JWT from `?token=` query param during WebSocket handshake, stores `account` in session attributes.
- Passwords: BCrypt. Tokens: jjwt 0.12.6, HMAC-SHA, 24h expiry, subject = account.

### WebSocket / STOMP

`WebSocketConfig` wires the STOMP broker:
- Endpoint: `ws://host:port/ws?token=xxx`
- Client → Server: `/app/...` (routed to `@MessageMapping` methods)
- Server → Specific user: `/user/{account}/queue/private` (requires the custom `HandshakeHandler` that binds `account` as `Principal` — this is what makes `convertAndSendToUser()` work)
- Server → Broadcast: `/topic/status` (online/offline events)

`WebSocketChannelInterceptor` — maintains a `ConcurrentHashMap<sessionId, account>` for CONNECT/DISCONNECT. On CONNECT: sets `Principal` on the accessor (critical for user-destination routing), increments Redis online counter, broadcasts ONLINE to `/topic/status`. On DISCONNECT: decrements counter, broadcasts OFFLINE. Uses `@Lazy` on `SimpMessagingTemplate` to break circular dependency with the broker config.

### Module Structure

Each feature module under `mvc/` follows **Controller → Service interface → ServiceImpl → MyBatis-Plus Mapper**:

| Module | Purpose |
|---|---|
| `auth` | Register/login, Redis-backed account-taken cache (`@Cacheable`), user-info cache |
| `chat` | STOMP message handler (`ChatWebSocketController`) + REST endpoints for history, conversations, offline pull, read receipts |
| `friend` | Search, friend requests (PENDING→ACCEPTED/REJECTED/DISSOLVED state machine), friend list, remove |
| `online` | Redis-backed multi-device online status (`online:count:{account}` counter + `online:users` set) |
| `user` | Profile read, nickname update (DB + Redis cache sync) |

### Key Patterns

**Friend pair canonicalization**: Friend rows always store accounts in lexicographic order (`account_a < account_b`). Always normalize before querying or inserting — see `isFriend()` in `MessageServiceImpl` and `removeFriend()` in `FriendServiceImpl`.

**WebSocket notifications after commit**: Friend request notifications use `TransactionSynchronization.afterCommit()` so pushes only fire after the DB transaction succeeds.

**Offline message queue**: Messages sent to offline users stay at `STATUS_SENT` in MySQL. When the recipient connects and calls `/api/message/offline`, all `SENT` messages addressed to them are delivered and marked `DELIVERED`. Read receipts (`markAsRead`) update status to `READ` and push a `ReadReceiptDto` to the sender via `/queue/read-receipt`.

**Batch user queries**: Service methods avoid N+1 by collecting account sets and calling `userMapper.selectBatchIds()` — see `getFriendList()`, `getChatHistory()`, `getConversations()`.

**Redis dual role**: (1) Live state — online counters and user sets managed by `OnlineStatusService`. (2) Cache layer — `user:cache:{account}` (24h TTL) and `account:taken` (7d TTL via Spring `@Cacheable`).

### Database

Tables prefixed `LBU_`:
- `LBU_User` — account (PK, VARCHAR 32), BCrypt password, name, create_time
- `LBU_Friend` — account_a/account_b with UNIQUE(account_a, account_b) and lexicographic ordering
- `LBU_Friend_Request` — status: 0=PENDING, 1=ACCEPTED, 2=REJECTED, 3=DISSOLVED
- `LBU_Message` — `LBU_Message` (note: no underscore between prefix and name unlike other tables), status: 0=SENT, 1=DELIVERED, 2=READ

MyBatis-Plus: `id-type: input` (manual PK assignment). SQL logging to stdout. `map-underscore-to-camel-case: true`.

### Response Convention

`Result<T>` wrapper with `code`/`message`/`data`. Success: `Result.ok(data)`, code 200. Error: `Result.error(code, message)`. `GlobalExceptionHandler` catches `MethodArgumentNotValidException` (→400 with field errors) and general `RuntimeException` (→400).
