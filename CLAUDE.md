# CLAUDE.md

이 파일은 이 저장소에서 작업할 때 Claude Code(claude.ai/code)에게 제공되는 가이드입니다.

## 프로젝트

Spring Boot 4.1.0 / Java 21로 만든 실시간 채팅 서비스(그룹방 + 1:1 DM)입니다. 인증과 방 관리는
REST, 실시간 메시징은 STOMP-over-WebSocket, 영속성은 Postgres, 인스턴스 간 메시지 전파와 JWT
블랙리스트는 Redis를 사용합니다.

## 명령어

모든 명령은 Maven wrapper를 사용합니다 (PowerShell):

```
.\mvnw.cmd clean compile          # 빌드
.\mvnw.cmd test                   # 전체 테스트 실행
.\mvnw.cmd test -Dtest=ChatServiceTest                      # 단일 테스트 클래스
.\mvnw.cmd test -Dtest=ChatServiceTest#sendMessage_ok        # 단일 테스트 메서드
.\mvnw.cmd spring-boot:run         # 로컬에서 앱 실행
```

요구 사항:
- **테스트는 Docker가 실행 중이어야 합니다.** 모든 테스트 클래스는 `AbstractIntegrationTest` 또는
  `AbstractRepositoryTest`를 상속하며, 둘 다 Testcontainers(`support/Containers.java`)로 실제
  Postgres + Redis 컨테이너를 띄웁니다. 이 레이어들에는 mock DB 기반의 순수 단위 테스트가 없습니다.
- **로컬에서 앱을 실행**하려면 Postgres(`localhost:5432`, db `chatservice`, 계정
  `postgres`/`postgres`)와 Redis(`localhost:6379`)가 미리 떠 있어야 합니다 — 저장소에
  docker-compose 파일이 없으므로 직접 띄워야 합니다. 필요하면 `REDIS_HOST`/`REDIS_PORT` 환경변수로
  덮어쓸 수 있습니다.

## 아키텍처

### 인증: 서로 분리된 두 개의 JWT 검증 지점

HTTP와 WebSocket 트래픽은 서로 독립적으로 인증되며, 공유되는 필터가 없습니다:

- **HTTP**: `JwtAuthenticationFilter`(서블릿 필터)가 `Authorization: Bearer <token>`을 읽어
  검증하고 `SecurityContextHolder`를 채웁니다. `SecurityConfig`에서 설정되며, `/api/auth/**`,
  `/ws/**`, Thymeleaf 뷰 라우트는 허용하고 나머지는 인증을 요구합니다.
- **WebSocket**: `/ws/**`는 HTTP 레이어에서 `permitAll`이라 STOMP 핸드셰이크 자체는 여기서
  보호되지 않습니다. 실제 인증은 `StompAuthChannelInterceptor`에서 이루어지는데, STOMP `CONNECT`
  프레임의 `Authorization` 헤더만 검사해서 STOMP 세션의 `Principal`을 설정합니다 — 이후 같은
  세션의 프레임들은 이 Principal을 그대로 물려받습니다.
- 두 경로 모두 `TokenBlacklistService`(Redis 기반, 원본 토큰을 키로 사용, 토큰의 남은 만료 시간만큼
  TTL 설정)를 확인하므로, `/api/auth/logout`은 토큰의 자연 만료를 기다리지 않고 즉시 무효화할 수
  있습니다.

### 메시지 흐름: WebSocket으로 바로 쏘지 않고 DB 저장 + Redis 전파

`ChatController`의 `@MessageMapping` 핸들러에서 호출되는 `ChatService.sendMessage`는 메시지를
저장한 뒤 `ChatMessagePublisher.publish(...)`를 호출해 Redis 채널 `chat-messages`로 발행합니다 —
STOMP 브로커로 직접 푸시하지 **않습니다**. 각 앱 인스턴스의 `ChatMessageSubscriber`
(`RedisMessageListener`)는 이 채널의 모든 메시지를 수신하며, 여기에는 자기 자신이 방금 발행한
메시지도 포함됩니다. 이를 `SimpMessagingTemplate`을 통해 `/topic/rooms/{roomId}`로 다시
발행합니다. 이 구조 덕분에 로드밸런서 뒤에 여러 앱 인스턴스가 있어도 서비스가 올바르게 동작합니다:
클라이언트가 어느 인스턴스에 붙어 있든 모든 메시지를 받을 수 있는 이유는, WebSocket 클라이언트로의
전달이 항상 각 인스턴스의 로컬 브로커 상태가 아니라 Redis를 거치기 때문입니다. 메시지 전달 방식을
바꿀 때는 `ChatService`에서 `SimpMessagingTemplate`을 직접 호출하는 식으로 이 Redis 왕복 구조를
생략하지 말고 유지해야 합니다.

관련 주의점: `RedisConfig`는 `GenericJacksonJsonRedisSerializer`를 사용하는데, 이 직렬화기는
직렬화된 페이로드에 타입 정보를 포함하지 않습니다. 그래서 `ChatMessageSubscriber`는 반드시 명시적
타입(`ChatMessageResponse.class`)으로 역직렬화해야 하며, 타입을 지정하지 않고 역직렬화하면
`LinkedHashMap`이 반환됩니다.

### 방(Room) 멤버십 모델

`ChatRoom`은 그룹방과 DM 모두에 쓰이는 범용 엔티티이며(`group` boolean 플래그로 구분), 두 경우
모두 멤버십은 `RoomParticipant`(`room_id, user_id` 유니크 제약) 로우로 표현됩니다.
`RoomService.getOrCreateDirectRoom`은 새 방을 만들기 전에 셀프 조인 쿼리
(`RoomParticipantRepository.findDirectRoomBetween`)로 기존 DM방을 먼저 찾으므로, 같은 사용자
쌍에 대해 DM방은 멱등하게 생성됩니다. 메시지 전송과 히스토리 조회
(`ChatService.sendMessage`, `RoomService.getMessages`) 모두
`RoomParticipantRepository.existsByRoomIdAndUserId`로만 권한을 검사하며, 별도의 역할/권한 레이어는
없습니다.

### 테스트 베이스 클래스

- `AbstractIntegrationTest` — 전체 Spring 컨텍스트, 실제 시큐리티 필터 체인, 랜덤 포트에 대한
  `MockMvc`, 컨테이너화된 Postgres + Redis를 사용합니다. JWT를 직접 만들지 않고 기존 사용자로
  인증하려면 `bearerToken(username)`을 사용하세요.
- `AbstractRepositoryTest` — `@DataJpaTest` 슬라이스, 컨테이너화된 Postgres만 사용하고
  web/security 컨텍스트는 없습니다. 커스텀 JPQL 검증(예:
  `RoomParticipantRepository.findDirectRoomBetween`)에 사용하세요.
- `Containers` — 두 베이스 클래스 모두 이 인터페이스를 구현하며, 테스트 클래스마다가 아니라 JVM당
  Postgres/Redis 컨테이너를 하나씩만 띄우고 `@DynamicPropertySource`로 주소를 주입합니다.

### WebSocket 라우팅

클라이언트 목적지는 `/app` 프리픽스를 사용하고(`/app/rooms/{roomId}/send`,
`/app/rooms/{roomId}/enter`), 브로커는 `/topic/rooms/{roomId}`로 릴레이합니다(`WebSocketConfig`
참고). `src/main/resources/static/js/room.js`의 프론트엔드 JS가 이에 대응하는 STOMP 클라이언트
역할을 합니다.
