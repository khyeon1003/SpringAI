#!/usr/bin/env python3
import csv
import json
import os
import sys
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from openai import OpenAI
from ragas.llms import llm_factory
from ragas.metrics.collections import Faithfulness, FactualCorrectness


ROOT_DIR = Path(__file__).resolve().parents[1]
GOLDEN_SET_PATH = ROOT_DIR / "golden-set" / "golden-set.json"
RESULT_DIR = Path(__file__).resolve().parent / "results"

BASE_URL = os.getenv("CHAT_EVAL_BASE_URL", "http://localhost:8080").rstrip("/")
CHAT_PATH = os.getenv("CHAT_EVAL_PATH", "/api/v1/chat")
TIMEOUT_SECONDS = float(os.getenv("CHAT_EVAL_TIMEOUT_SECONDS", "60"))
EVALUATOR_MODEL = os.getenv("RAGAS_EVALUATOR_MODEL", "gpt-4.1-mini")
MIN_SCORE = float(os.getenv("RAGAS_MIN_SCORE", "0.7"))


def call_chat_api(case: dict[str, Any]) -> dict[str, Any]:
    payload: dict[str, Any] = {"message": case["question"]}
    if case.get("userContext") is not None:
        payload["userContext"] = case["userContext"]

    request = urllib.request.Request(
        f"{BASE_URL}{CHAT_PATH}",
        data=json.dumps(payload, ensure_ascii=False).encode("utf-8"),
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=TIMEOUT_SECONDS) as response:
        return json.loads(response.read().decode("utf-8"))


def normalize_contexts(raw_contexts: Any) -> list[str]:
    if not isinstance(raw_contexts, list):
        return []

    contexts: list[str] = []
    for context in raw_contexts:
        if isinstance(context, str):
            contexts.append(context)
        elif isinstance(context, dict):
            text = context.get("content") or context.get("text") or context.get("pageContent")
            if text:
                contexts.append(str(text))
    return contexts


def result_value(result: Any) -> float:
    value = getattr(result, "value", result)
    return float(value)


def main() -> int:
    if not os.getenv("OPENAI_API_KEY"):
        print("OPENAI_API_KEY가 필요합니다.", file=sys.stderr)
        return 2

    with GOLDEN_SET_PATH.open(encoding="utf-8") as file:
        cases = json.load(file)["cases"]

    client = OpenAI()
    evaluator_llm = llm_factory(EVALUATOR_MODEL, client=client)
    faithfulness = Faithfulness(llm=evaluator_llm)
    factual_correctness = FactualCorrectness(llm=evaluator_llm, mode="f1")

    rows: list[dict[str, Any]] = []
    for index, case in enumerate(cases, start=1):
        print(f"[{index:02d}/{len(cases)}] {case['id']} 평가 중...")
        row: dict[str, Any] = {
            "id": case["id"],
            "category": case["category"],
            "expected_action": case["expectedAction"],
            "actual_action": "",
            "action_pass": False,
            "faithfulness": None,
            "factual_correctness": None,
            "passed": False,
            "error": "",
        }

        try:
            response = call_chat_api(case)
            actual_action = str(response.get("action", ""))
            row["actual_action"] = actual_action
            row["action_pass"] = actual_action == case["expectedAction"]

            if case["expectedAction"] == "BLOCK":
                row["passed"] = row["action_pass"]
            else:
                answer = str(response.get("answer", ""))
                contexts = normalize_contexts(response.get("contexts"))
                if case.get("userContext") is not None:
                    contexts.append(
                        "사용자 엔티티 정보: "
                        + json.dumps(case["userContext"], ensure_ascii=False)
                    )

                if not answer.strip():
                    raise ValueError("응답의 answer가 비어 있습니다.")
                if not contexts:
                    raise ValueError("응답의 contexts가 비어 있습니다.")

                reference = " ".join(case.get("expectedFacts", []))
                row["faithfulness"] = result_value(
                    faithfulness.score(
                        user_input=case["question"],
                        response=answer,
                        retrieved_contexts=contexts,
                    )
                )
                row["factual_correctness"] = result_value(
                    factual_correctness.score(response=answer, reference=reference)
                )
                row["passed"] = (
                    row["action_pass"]
                    and row["faithfulness"] >= MIN_SCORE
                    and row["factual_correctness"] >= MIN_SCORE
                )
        except (urllib.error.URLError, TimeoutError, ValueError, KeyError, TypeError) as error:
            row["error"] = str(error)
        except Exception as error:  # RAGAS/provider errors should remain visible per case.
            row["error"] = f"{type(error).__name__}: {error}"

        rows.append(row)

    RESULT_DIR.mkdir(parents=True, exist_ok=True)
    timestamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    json_path = RESULT_DIR / f"ragas-{timestamp}.json"
    csv_path = RESULT_DIR / f"ragas-{timestamp}.csv"

    summary = {
        "total": len(rows),
        "passed": sum(bool(row["passed"]) for row in rows),
        "failed": sum(not bool(row["passed"]) for row in rows),
        "minimumScore": MIN_SCORE,
        "evaluatorModel": EVALUATOR_MODEL,
    }
    json_path.write_text(
        json.dumps({"summary": summary, "results": rows}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    with csv_path.open("w", encoding="utf-8-sig", newline="") as file:
        writer = csv.DictWriter(file, fieldnames=rows[0].keys())
        writer.writeheader()
        writer.writerows(rows)

    print(json.dumps(summary, ensure_ascii=False, indent=2))
    print(f"JSON: {json_path}")
    print(f"CSV : {csv_path}")
    return 0 if summary["failed"] == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
