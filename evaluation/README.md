# RAGAS 간단 평가

Spring 챗봇 API에 골든셋 20개를 요청한다. `BLOCK` 10개는 분기 정확도를 확인하고,
`ANSWER` 10개는 RAGAS로 다음 지표를 계산한다.

- `Faithfulness`: 답변이 검색된 context와 사용자 엔티티 정보에 근거하는지 평가
- `FactualCorrectness`: 답변이 골든셋의 `expectedFacts`와 일치하는지 평가

두 점수가 기본 기준인 `0.7` 이상이고 `action`도 일치해야 해당 케이스가 통과한다.

## API 응답 계약

평가 대상 API는 기본적으로 `POST /api/v1/chat`이다.

```json
{
  "action": "ANSWER",
  "answer": "생성된 답변",
  "contexts": ["실제로 검색된 문서 조각"]
}
```

`contexts` 원소는 문자열이거나 `content`, `text`, `pageContent` 중 하나를 가진 객체여도 된다.

## 설치 및 실행

`uv` 사용을 권장한다.

```bash
cd evaluation
uv sync
OPENAI_API_KEY=... uv run python evaluate.py
```

프로젝트 루트의 `.env`를 자동으로 읽지는 않으므로 셸 환경변수로 전달해야 한다.
Spring API 주소나 평가 모델을 바꾸려면 다음과 같이 실행한다.

```bash
CHAT_EVAL_BASE_URL=http://localhost:8080 \
CHAT_EVAL_PATH=/api/v1/chat \
RAGAS_EVALUATOR_MODEL=gpt-4.1-mini \
RAGAS_MIN_SCORE=0.7 \
OPENAI_API_KEY=... \
uv run python evaluate.py
```

결과는 `evaluation/results/`에 JSON과 CSV로 저장된다. 평가 모델 호출 비용이 발생한다.
