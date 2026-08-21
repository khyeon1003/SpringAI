# Spring AI Mini Project 구조

## 1. 프로젝트 개요

이 프로젝트는 사용자 학사 정보를 바탕으로 대화 세션과 채팅 이력을 관리하고, 원문 데이터를 임베딩하여 유사도 검색에 활용하는 Spring Boot 애플리케이션이다.

- Java 21
- Spring Boot 4.1.0
- Spring AI 2.0.0
- Spring Data JPA
- PostgreSQL
- PGvector
- OpenAI Chat/Embedding 모델
- Gradle

## 2. 디렉터리 구조

```text
SpringAI-mini-project/
├── .env                         # 로컬 환경변수 및 비밀정보(Git 제외)
├── .env.example                 # 환경변수 작성 예시
├── build.gradle                 # 플러그인 및 의존성 설정
├── settings.gradle              # Gradle 프로젝트 이름 설정
├── PROJECT_STRUCTURE.md         # 프로젝트 구조 문서
└── src/
    ├── main/
    │   ├── java/com/example/springai/
    │   │   ├── SpringAiApplication.java
    │   │   ├── controller/
    │   │   │   └── Controller.java
    │   │   ├── service/
    │   │   │   └── Service.java
    │   │   ├── entity/
    │   │   │   ├── User.java
    │   │   │   ├── Session.java
    │   │   │   ├── History.java
    │   │   │   └── Document.java
    │   │   └── repository/
    │   │       ├── UserRepository.java
    │   │       ├── SessionRepository.java
    │   │       ├── HistoryRepository.java
    │   │       └── DocumentRepository.java
    │   └── resources/
    │       └── application.yaml
    └── test/
        └── java/com/example/springai/
            └── SpringAiApplicationTests.java
```

## 3. 패키지별 역할

| 패키지 | 역할 |
|---|---|
| `controller` | HTTP 요청을 받고 요청값 검증 및 응답 반환 |
| `service` | 유스케이스와 비즈니스 로직 처리 |
| `entity` | PostgreSQL의 일반 관계형 테이블과 매핑되는 JPA 엔티티 |
| `repository` | 엔티티의 조회 및 저장을 담당하는 Spring Data JPA 저장소 |
| `resources` | 데이터베이스, OpenAI, PGvector 등의 애플리케이션 설정 |

의존 방향은 `Controller → Service → Repository → Entity`를 기본으로 한다. Controller에서 Repository를 직접 호출하지 않는다.

## 4. 엔티티 및 관계

```mermaid
erDiagram
    USER ||--o{ CHAT_SESSION : creates
    CHAT_SESSION ||--o{ CHAT_HISTORY : contains

    USER {
        bigint id PK
        decimal gpa
        decimal grade
        integer general_education_credits
        integer major_credits
    }

    CHAT_SESSION {
        bigint id PK
        bigint user_id FK
        timestamp created_at
    }

    CHAT_HISTORY {
        bigint id PK
        bigint session_id FK
        text chat
        timestamp created_at
    }

    DOCUMENTS {
        bigint id PK
        text original_text
    }

    VECTOR_STORE {
        uuid id PK
        text content
        json metadata
        vector embedding
    }
```

### `User`

사용자의 학사 정보를 저장한다. 학년(`grade`)은 `1.0`, `2.5`처럼 소수점 한 자리까지 저장한다. `users` 테이블을 사용하여 PostgreSQL 예약어 또는 일반적인 `user` 테이블 이름과의 충돌을 피한다.

### `Session`

사용자별 채팅 세션을 나타낸다. `User`와 지연 로딩 방식의 다대일 관계이며 실제 테이블 이름은 `chat_sessions`이다.

### `History`

세션에서 발생한 채팅 내용을 저장한다. `Session`과 지연 로딩 방식의 다대일 관계이며 실제 테이블 이름은 `chat_history`이다.

### `Document`

임베딩 전 원문 데이터를 `documents` 테이블에 보관한다. 임베딩 벡터를 이 엔티티에 직접 저장하지 않는다.

## 5. Vector Store

임베딩 전용 JPA 엔티티나 별도 임베딩 테이블은 만들지 않는다. Spring AI의 `PgVectorStore`를 사용하며, 애플리케이션 시작 시 다음 객체를 자동으로 준비한다.

- PostgreSQL 확장: `vector`, `hstore`, `uuid-ossp`
- 벡터 테이블: `public.vector_store`
- 검색 인덱스: HNSW
- 거리 계산: Cosine Distance

임베딩 차원은 `application.yaml`에서 고정하지 않고 선택된 OpenAI 임베딩 모델로부터 자동으로 결정한다. 모델이나 차원이 변경되면 기존 `vector_store` 테이블의 벡터 차원과 호환되는지 확인해야 한다.

## 6. 환경변수

로컬 실행에 사용하는 값은 `.env`에 작성한다. `.env`는 Git에서 제외되며 저장소에는 실제 API 키를 올리지 않는다.

| 변수 | 설명 | 기본/예시 값 |
|---|---|---|
| `DB_URL` | PostgreSQL JDBC 주소 | `jdbc:postgresql://localhost:5432/springai` |
| `DB_USERNAME` | PostgreSQL 사용자 | `postgres` |
| `DB_PASSWORD` | PostgreSQL 비밀번호 | `postgres` |
| `OPENAI_API_KEY` | OpenAI API 키 | `sk-...` |
| `OPENAI_CHAT_MODEL` | 채팅 모델 | `gpt-4.1-mini` |
| `OPENAI_EMBEDDING_MODEL` | 임베딩 모델 | `text-embedding-3-small` |
| `RAG_TOP_K` | 질문마다 검색할 유사 문서 개수 | `4` |

새 환경에서는 `.env.example`을 복사한 뒤 실제 접속 정보와 OpenAI API 키를 입력한다.

## 7. 데이터 처리 흐름

1. Controller가 클라이언트 요청을 받는다.
2. Service가 사용자 정보와 채팅 세션을 기준으로 비즈니스 로직을 수행한다.
3. 일반 데이터는 각 JPA Repository를 통해 PostgreSQL에 저장한다.
4. 원문은 필요에 따라 분할한 뒤 OpenAI Embedding Model로 벡터화한다.
5. 생성된 벡터와 메타데이터는 Spring AI `VectorStore`를 통해 `vector_store`에 저장한다.
6. 질문이 들어오면 유사도 검색 결과를 채팅 모델의 문맥으로 전달한다.

## 8. 실행 전 확인사항

- Java 21이 설치되어 있어야 한다.
- PostgreSQL에 `springai` 데이터베이스가 생성되어 있어야 한다.
- PostgreSQL 서버에 PGvector가 설치되어 있어야 한다.
- 데이터베이스 계정에 테이블과 확장을 생성할 권한이 있어야 한다.
- `.env`의 `OPENAI_API_KEY`를 실제 키로 교체해야 한다.

```bash
./gradlew bootRun
```

컴파일만 확인하려면 다음 명령을 사용한다.

```bash
./gradlew compileJava
```
