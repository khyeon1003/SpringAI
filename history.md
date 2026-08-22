# RAG 및 Tool Calling 설계 기록

## 현재 목표

Spring AI 기반으로 `vector_store`에서 문서 컨텍스트를 검색하고, 답변 생성 단계에서 필요할 때 현재 유저의 학사 데이터를 tool로 조회하는 구조를 만든다.

## 모델 및 ChatClient 설정

- `ModelConfig`는 `spring.ai.openai.chat.options` 값을 바인딩한다.
- 모델 기본값은 Java 코드가 아니라 `application.yaml`에서 관리한다.
- `ChatClientConfig`는 모델 옵션만 적용된 공용 `ChatClient` 빈을 만든다.
- 유저 데이터 tool은 `ChatClient`에 전역 등록하지 않는다.
- 유저 데이터 tool은 답변 생성처럼 실제로 필요한 호출 지점에서만 등록한다.

## 검색 단계

- `Retriever`는 Spring AI `VectorStore`를 감싼다.
- `VectorStore`는 설정된 pgvector 테이블인 `public.vector_store`를 사용한다.
- 유저 query는 `SearchRequest`로 변환된다.
- 검색 결과는 `List<org.springframework.ai.document.Document>`로 반환한다.
- `Retriever`는 chat model을 호출하지 않고 답변도 생성하지 않는다.

## 답변 생성 단계

- `AnswerGenerator`는 다음 값을 입력으로 받는다.
- 유저 query
- 검색된 문서 목록
- 현재 유저 id
- 검색된 문서 목록은 하나의 context 문자열로 합친다.
- 프롬프트 본문은 코드에 하드코딩하지 않고 리소스 파일로 분리한다.
- 시스템 프롬프트: `src/main/resources/prompts/rag-system.st`
- 유저 프롬프트: `src/main/resources/prompts/rag-user.st`
- `ChatClient` 호출 시 다음 값을 함께 넘긴다.
- 검색 context
- 원본 유저 query
- `.tools(userTool)`로 등록한 `UserTool`
- `.toolContext(Map.of("userId", userId))`로 전달한 현재 유저 id
- 모델이 개인 학사 데이터가 필요하다고 판단하면 `UserTool`을 호출할 수 있다.

## 유저 데이터 Tool

- `UserTool` 위치는 `com.example.springai.tool` 패키지다.
- tool은 모델이 생성한 인자가 아니라 `ToolContext`에서 현재 유저 id를 읽는다.
- context key는 `UserTool.USER_ID_CONTEXT_KEY`이고 실제 값은 `"userId"`다.
- tool call 인자로 `requestedUserId`가 들어온 경우, `ToolContext`의 현재 유저 id와 반드시 비교한다.
- `requestedUserId`와 현재 유저 id가 다르면 `UserDataAccessDeniedException`을 던진다.
- `UserDataAccessDeniedException`은 다른 기술 오류와 마찬가지로 상위 계층으로 전달한다. Rewrite 가드레일이 완성되면 타인 정보 요청은 Tool 호출 전에 `BLOCK` 처리한다.
- 현재 제공하는 tool은 다음과 같다.
- `getUserAcademicInfo`
- `getUserGpa`
- `getUserGrade`
- `getUserGeneralEducationCredits`
- `getUserMajorCredits`
- DB 접근은 `UserRepository`가 담당한다.
- `UserAcademicInfo` record는 DAO가 아니라 DTO 성격의 응답 객체다.

## Service 조립 구조

- `Service`는 유스케이스 흐름을 조립한다.
- 흐름은 다음 순서다.
- `Retriever.retrieveChunks(query)`
- `AnswerGenerator.generate(query, retrievedChunks, userId)`
- 이후 컨트롤러가 붙으면 `Service`의 메서드를 호출하는 구조로 연결한다.

## 설계 이유

- 문서 기반 정적 정보는 RAG 검색으로 가져온다.
- 유저별 동적 정보는 tool로 가져온다.
- `userId`는 모델에게 맡기지 않고 서버가 `ToolContext`로 직접 주입한다.
- 이렇게 하면 모델이 임의의 userId를 만들거나 다른 유저 데이터를 조회하려는 위험을 줄일 수 있다.
- 혹시 모델이 tool call 인자로 다른 userId를 포함하더라도, 서버 주입 userId와 비교해서 차단한다.
- tool 등록 범위는 답변 생성 호출로 제한해서, 관련 없는 `ChatClient` 호출에서 유저 데이터 tool이 열리지 않게 한다.

## 2026-08-21 작업사항

### SSE 통신

- 서버 웹 스택은 WebFlux로 통일하고 WebMVC starter를 제거했다.
- `POST /api/v1/chat/stream`은 `text/event-stream`으로 응답한다.
- SSE 이벤트는 `connected → token 반복 → completed` 순서로 전송한다.
- 오류가 발생하면 내부 예외 상세를 노출하지 않고 `error` 이벤트를 전송한다.
- `ChatStreamEvent.phase`는 `CONNECTED`, `TOKEN`, `COMPLETED`, `ERROR` 중 하나다.
- Spring AI의 `.stream().content()`를 사용해 생성된 텍스트 조각을 SSE token 이벤트로 전달한다.

### RAG 및 Tool Calling 순서

- 한 요청 안에서는 `Retriever → ChatClient → Tool Calling → 최종 생성` 순서를 유지한다.
- 여러 사용자의 요청은 서로 독립적으로 처리되며 완료 순서는 달라질 수 있다.
- Tool Calling 도중 모델의 중간 Tool 요청이나 인자는 SSE 데이터로 직접 노출하지 않는다.

### 현재 선택한 실행 방식

- Retriever의 PGVector JDBC 검색과 UserTool의 JPA 조회는 현재 호출 스레드에서 실행된다.
- 구현은 단순하지만 블로킹 작업이 WebFlux 이벤트 루프를 점유할 수 있다.

### SSE 고민사항과 트레이드오프

| 선택 | 장점 | 단점 |
|---|---|---|
| WebFlux SSE | 연결당 전용 스레드 없이 스트림을 다루기 쉽고 Spring AI Flux와 결합이 단순하다. | 현재 JPA/JDBC 작업이 블로킹이므로 이벤트 루프 지연 위험이 있다. |
| boundedElastic 미사용 | 스케줄러 전환과 별도 작업 큐 없이 코드가 단순하다. | DB나 Tool 호출이 느리면 같은 이벤트 루프의 다른 연결도 지연될 수 있다. |
| 토큰 단위 전송 | 사용자가 전체 답변 완료 전부터 내용을 볼 수 있다. | 연결 중단 시 불완전한 답변이 남고 재연결 시 이어받기 구현이 어렵다. |
| Tool Calling + 스트리밍 | 사용자 데이터를 반영한 최종 답변도 스트리밍할 수 있다. | Tool 전후로 모델 호출이 반복되며 토큰 usage 집계와 중간 응답 관리가 복잡하다. |
| SSE error 이벤트 | 스트림이 열린 뒤 발생한 오류를 클라이언트에 알릴 수 있다. | HTTP 상태 코드를 이미 200으로 전송한 뒤라 상태 코드로 실패를 표현할 수 없다. |
| POST SSE | 질문과 사용자 식별자를 요청 body로 전달하기 쉽다. | 브라우저 기본 `EventSource`는 GET만 지원하므로 `fetch` 스트림과 수동 재연결이 필요하다. |

### 후속 검토사항

- 동시 접속 부하 테스트로 이벤트 루프 지연 여부를 확인한다.
- 부하가 커지면 블로킹 구간만 별도 Scheduler로 격리하거나 데이터 계층을 reactive 방식으로 전환한다.
- 장시간 무응답 구간을 위한 heartbeat 이벤트를 추가한다.
- 재연결을 지원하려면 SSE event ID와 `Last-Event-ID` 처리 및 이벤트 저장소가 필요하다.
- 브라우저 `EventSource`를 사용할 경우 요청 생성 API와 GET 구독 API를 분리하는 방식을 검토한다.
- 스트리밍 응답의 누적 token usage를 완료 시점에 집계하는 방식을 추가한다.
