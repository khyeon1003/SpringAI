# 토큰 사용량 메트릭

Actuator와 Prometheus 엔드포인트를 통해 Spring AI 기본 메트릭과 질의 단위 메트릭을 확인한다.

```text
GET /actuator/metrics/gen.ai.client.token.usage
GET /actuator/metrics/chat.query.tokens
GET /actuator/prometheus
```

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

Prometheus에서 최근 구간의 질의당 평균은 다음과 같이 계산할 수 있다.

```promql
rate(chat_query_tokens_sum[5m]) / rate(chat_query_tokens_count[5m])
```

상한 준수 알림 조건 예시는 다음과 같다.

```promql
(rate(chat_query_tokens_sum[5m]) / rate(chat_query_tokens_count[5m]))
  > chat_query_tokens_average_limit
```

프롬프트와 응답 본문은 메트릭에 포함하지 않는다.
