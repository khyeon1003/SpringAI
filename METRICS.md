# 토큰 사용량 메트릭

Actuator와 Prometheus 엔드포인트를 통해 Spring AI 기본 메트릭과 질의 단위 메트릭을 확인한다.

## 설정

질의당 평균 total token 상한은 환경변수로 설정한다. 기본값은 `2000`이다.

```properties
TOKEN_USAGE_AVERAGE_LIMIT=2000
```

현재 노출된 Actuator 엔드포인트는 다음과 같다.

```text
GET /actuator/health
GET /actuator/metrics
GET /actuator/metrics/gen.ai.client.token.usage
GET /actuator/metrics/chat.query.tokens
GET /actuator/prometheus
```

운영 환경에서는 Actuator 엔드포인트를 외부에 그대로 공개하지 않고 인증 또는 내부망 접근
제어를 적용해야 한다.

## 제공 메트릭

| 메트릭 | 의미 |
|---|---|
| `gen_ai_client_token_usage_total` | Spring AI가 기록하는 모델 호출별 누적 토큰 |
| `chat_query_tokens_count` | 토큰 usage가 기록된 사용자 질의 수 |
| `chat_query_tokens_sum` | 사용자 질의가 사용한 전체 토큰 합계 |
| `chat_query_tokens_max` | 단일 사용자 질의의 최대 토큰 |
| `chat_query_tokens_average` | 애플리케이션 시작 이후 질의당 평균 토큰 |
| `chat_query_tokens_average_limit` | `TOKEN_USAGE_AVERAGE_LIMIT` 설정값 |
| `chat_query_tokens_average_compliant` | 평균이 상한 이하면 `1`, 초과면 `0` |

Tool Calling으로 한 질의에서 모델이 여러 번 호출되더라도 최종 `ChatResponse`의 누적 usage를
한 번 기록한다. Provider가 usage를 반환하지 않은 요청은 질의 단위 메트릭에서 제외된다.

## 로컬 확인

애플리케이션을 실행한다.

```bash
./gradlew bootRun
```

메트릭은 실제 모델 응답 이후 생성되므로 일반 챗봇 API를 먼저 호출한다.

```bash
curl -X POST http://localhost:8080/api/v1/chat \
  -H 'Content-Type: application/json' \
  -d '{"userId":1,"message":"졸업 요건을 알려줘"}'
```

질의별 total token 분포를 확인한다.

```bash
curl -s http://localhost:8080/actuator/metrics/chat.query.tokens
```

평균, 상한, 준수 여부를 각각 확인한다.

```bash
curl -s http://localhost:8080/actuator/metrics/chat.query.tokens.average
curl -s http://localhost:8080/actuator/metrics/chat.query.tokens.average.limit
curl -s http://localhost:8080/actuator/metrics/chat.query.tokens.average.compliant
```

`chat.query.tokens.average.compliant`의 값은 다음 의미를 가진다.

```text
1.0: 애플리케이션 시작 이후 평균이 설정 상한 이하
0.0: 애플리케이션 시작 이후 평균이 설정 상한 초과
```

Spring AI가 모델 호출마다 기록하는 input/output/total token은 다음에서 확인한다.

```bash
curl -s http://localhost:8080/actuator/metrics/gen.ai.client.token.usage
```

Prometheus 형식에서 토큰 관련 항목만 확인하려면 다음 명령을 사용한다.

```bash
curl -s http://localhost:8080/actuator/prometheus \
  | grep -E 'chat_query_tokens|gen_ai_client_token_usage'
```

## Prometheus 쿼리

Prometheus에서 최근 구간의 질의당 평균은 다음과 같이 계산할 수 있다.

```promql
rate(chat_query_tokens_sum[5m]) / rate(chat_query_tokens_count[5m])
```

상한 준수 알림 조건 예시는 다음과 같다.

```promql
(rate(chat_query_tokens_sum[5m]) / rate(chat_query_tokens_count[5m]))
  > chat_query_tokens_average_limit
```

프로세스 시작 이후 누적 평균은 `chat_query_tokens_average`를 사용하고, 운영 알림은 트래픽의
최근 변화를 반영할 수 있도록 위와 같은 구간 평균을 사용하는 것이 적합하다.

## 측정 범위와 제한

- 일반 `POST /api/v1/chat` 요청은 최종 `ChatResponse`의 누적 usage를 질의 단위로 기록한다.
- Tool Calling으로 모델을 여러 번 호출한 경우에도 해당 질의의 total token을 한 번 기록한다.
- SSE `POST /api/v1/chat/stream`은 현재 완료 시점 usage 집계를 구현하지 않아
  `chat.query.tokens`에는 포함되지 않는다.
- SSE 내부 모델 호출은 Spring AI 기본 `gen_ai.client.token.usage` 메트릭에는 포함된다.
- 애플리케이션 재시작 시 프로세스 내부 누적 평균은 초기화된다.
- Prometheus가 주기적으로 수집하면 재시작 전후를 포함한 장기 분석이 가능하다.
- 프롬프트, 응답 본문, Tool 인자와 사용자 개인정보는 메트릭에 포함하지 않는다.
