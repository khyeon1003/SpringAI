# k6 챗봇 부하 테스트

기본 대상은 `POST http://localhost:8080/api/v1/chat`이다. 실제 API의 요청 필드가 다르면
`chatbot-load-test.js`의 `payload`만 컨트롤러 DTO에 맞게 수정한다.

## 실행

k6가 설치되어 있다면:

```bash
k6 run k6/chatbot-load-test.js
```

Docker로 실행한다면 애플리케이션을 먼저 실행한 뒤:

```bash
docker run --rm -i --network host \
  -v "$PWD/k6:/scripts" \
  grafana/k6 run /scripts/chatbot-load-test.js
```

macOS의 Docker Desktop에서는 `BASE_URL=http://host.docker.internal:8080`을 지정한다.

```bash
docker run --rm -i \
  -e BASE_URL=http://host.docker.internal:8080 \
  -v "$PWD/k6:/scripts" \
  grafana/k6 run /scripts/chatbot-load-test.js
```

## 주요 설정

```bash
BASE_URL=http://localhost:8080 \
CHAT_PATH=/api/v1/chat \
VUS=30 \
RAMP_UP=30s \
HOLD=2m \
RAMP_DOWN=15s \
REQUEST_TIMEOUT=30s \
k6 run k6/chatbot-load-test.js
```

인증이 필요하면 `AUTH_TOKEN`, 테스트 질문은 `MESSAGE`, 사용자 간 요청 간격은
`THINK_TIME`(초)로 지정한다.

기본 성공 기준은 오류율 1% 미만, p95 3초 미만, p99 5초 미만이다. LLM 응답 시간이
긴 API라면 실제 서비스 목표에 맞춰 `thresholds`를 조정한다.
