"""
The Scorecard — eval harness for Claude's extraction boundary judgement.

Measures how well Claude identifies correct vs incorrect service extraction
boundaries for the Spring Music monolith, using ADR-001 as ground truth.

Metrics reported:
  accuracy            — fraction of examples Claude labels correctly
  precision_correct   — of examples Claude calls CORRECT, how many actually are
  precision_incorrect — of examples Claude calls INCORRECT, how many actually are
  false_confidence    — fraction where confidence=HIGH but decision is wrong
  by_difficulty       — accuracy broken down by easy / medium / hard

Usage:
    export ANTHROPIC_API_KEY=sk-...
    python scorecard/scorecard.py

Output: scorecard/results/scorecard_<timestamp>.json
"""

import json
import os
import sys
from datetime import datetime
from pathlib import Path

import anthropic

GOLDEN_FILE  = Path(__file__).parent / "golden_dataset.json"
RESULTS_DIR  = Path(__file__).parent / "results"
MAX_RETRIES  = 3
MODEL        = "us.anthropic.claude-haiku-4-5-20251001-v1:0"
AWS_PROFILE  = "bootcamp"
AWS_REGION   = "us-west-2"

SYSTEM_PROMPT = """You are a senior software architect reviewing proposed service extraction
boundaries for a Spring Boot monolith called spring-music.

spring-music stores a catalog of music albums. It supports H2 / MySQL / PostgreSQL /
MongoDB / Redis via Spring profiles selected at runtime. There is no dedicated service
layer — controllers call repositories directly. It runs on Cloud Foundry and uses
VCAP_SERVICES for datasource auto-configuration.

The modernisation goal: extract the Album Catalog into a standalone service using
PostgreSQL as the default backend, remove Cloud Foundry dependencies, and delete
dangerous unauthenticated chaos endpoints (ErrorController).

For each proposed extraction boundary you receive, decide:
  CORRECT   — this is a good extraction unit (cohesive, low coupling, clean API contract)
  INCORRECT — this would be a poor extraction (splits cohesion, leaves dangling deps,
              exposes internal fields, or carries forward problems we want to eliminate)

Return ONLY a JSON object with exactly this shape, no markdown fences:
{
  "decision": "CORRECT" or "INCORRECT",
  "confidence": "HIGH" or "MEDIUM" or "LOW",
  "reasoning": "<two sentences max>"
}"""


def build_prompt(example: dict) -> str:
    included = "\n".join(f"  - {f}" for f in example["included"]) or "  (none — this is a deletion)"
    excluded = "\n".join(f"  - {f}" for f in example.get("excluded_from_monolith", [])) or "  (none)"

    return f"""Proposed extraction: {example['title']}

{example['description']}

Files included in extracted service:
{included}

Files deleted from monolith (not extracted):
{excluded}

Is this a CORRECT or INCORRECT extraction boundary?"""


def parse_response(text: str) -> dict:
    """Parse and validate Claude's JSON response. Raises ValueError on bad shape."""
    data = json.loads(text.strip())
    if data.get("decision") not in ("CORRECT", "INCORRECT"):
        raise ValueError(f"decision must be CORRECT or INCORRECT, got: {data.get('decision')}")
    if data.get("confidence") not in ("HIGH", "MEDIUM", "LOW"):
        raise ValueError(f"confidence must be HIGH/MEDIUM/LOW, got: {data.get('confidence')}")
    if "reasoning" not in data:
        raise ValueError("missing reasoning field")
    return data


def evaluate_example(example: dict, client: anthropic.Anthropic) -> dict:
    """Ask Claude to evaluate one extraction boundary. Retries up to MAX_RETRIES on bad output."""
    prompt = build_prompt(example)
    messages = [{"role": "user", "content": prompt}]
    last_error = None

    for attempt in range(1, MAX_RETRIES + 1):
        response = client.messages.create(
            model=MODEL,
            max_tokens=256,
            system=SYSTEM_PROMPT,
            messages=messages,
        )
        raw = response.content[0].text.strip()

        try:
            parsed = parse_response(raw)
            return {
                "id": example["id"],
                "difficulty": example["difficulty"],
                "label": example["label"],
                "decision": parsed["decision"],
                "confidence": parsed["confidence"],
                "reasoning": parsed["reasoning"],
                "correct": parsed["decision"] == example["label"],
                "false_confident": parsed["confidence"] == "HIGH" and parsed["decision"] != example["label"],
                "attempts": attempt,
                "error": None,
            }
        except (json.JSONDecodeError, ValueError) as e:
            last_error = str(e)
            # Feed the error back for the retry — validation-retry loop
            messages = messages + [
                {"role": "assistant", "content": raw},
                {"role": "user", "content": f"Your response was invalid: {last_error}. "
                                             f"Return only the JSON object with decision, confidence, and reasoning."},
            ]

    # All retries exhausted
    return {
        "id": example["id"],
        "difficulty": example["difficulty"],
        "label": example["label"],
        "decision": None,
        "confidence": None,
        "reasoning": None,
        "correct": False,
        "false_confident": False,
        "attempts": MAX_RETRIES,
        "error": last_error,
    }


def compute_metrics(results: list) -> dict:
    total = len(results)
    errors = [r for r in results if r["error"]]
    valid  = [r for r in results if not r["error"]]

    if not valid:
        return {"error": "all examples failed to parse"}

    correct_count = sum(1 for r in valid if r["correct"])
    false_conf    = sum(1 for r in valid if r["false_confident"])
    high_conf     = sum(1 for r in valid if r["confidence"] == "HIGH")

    # Precision per predicted class
    predicted_correct   = [r for r in valid if r["decision"] == "CORRECT"]
    predicted_incorrect = [r for r in valid if r["decision"] == "INCORRECT"]

    prec_correct   = (sum(1 for r in predicted_correct   if r["correct"]) / len(predicted_correct)
                      if predicted_correct else None)
    prec_incorrect = (sum(1 for r in predicted_incorrect if r["correct"]) / len(predicted_incorrect)
                      if predicted_incorrect else None)

    # Accuracy by difficulty (stratified)
    by_difficulty = {}
    for diff in ("easy", "medium", "hard"):
        subset = [r for r in valid if r["difficulty"] == diff]
        if subset:
            by_difficulty[diff] = {
                "count": len(subset),
                "correct": sum(1 for r in subset if r["correct"]),
                "accuracy": round(sum(1 for r in subset if r["correct"]) / len(subset), 3),
            }

    return {
        "total_examples":      total,
        "parse_errors":        len(errors),
        "accuracy":            round(correct_count / len(valid), 3),
        "precision_correct":   round(prec_correct,   3) if prec_correct   is not None else None,
        "precision_incorrect": round(prec_incorrect, 3) if prec_incorrect is not None else None,
        "false_confidence_rate": round(false_conf / high_conf, 3) if high_conf else 0.0,
        "high_confidence_count": high_conf,
        "false_confident_count": false_conf,
        "by_difficulty":       by_difficulty,
    }


def print_summary(metrics: dict, results: list):
    print("\n" + "=" * 60)
    print("SCORECARD RESULTS")
    print("=" * 60)
    print(f"  Accuracy              : {metrics['accuracy']:.1%}  ({metrics['total_examples'] - metrics['parse_errors']}/{metrics['total_examples']} valid)")
    print(f"  Precision (CORRECT)   : {metrics['precision_correct']:.1%}" if metrics['precision_correct'] is not None else "  Precision (CORRECT)   : n/a")
    print(f"  Precision (INCORRECT) : {metrics['precision_incorrect']:.1%}" if metrics['precision_incorrect'] is not None else "  Precision (INCORRECT) : n/a")
    print(f"  False-confidence rate : {metrics['false_confidence_rate']:.1%}  ({metrics['false_confident_count']} wrong at HIGH confidence)")
    print()
    print("  By difficulty:")
    for diff, stats in metrics.get("by_difficulty", {}).items():
        print(f"    {diff:<8}: {stats['accuracy']:.1%}  ({stats['correct']}/{stats['count']})")
    print()
    print("  Per-example results:")
    for r in results:
        status = "OK" if r["correct"] else "XX"
        conf   = r["confidence"] or "ERR"
        fc     = " ← FALSE CONFIDENT" if r["false_confident"] else ""
        err    = f" [parse error: {r['error']}]" if r["error"] else ""
        print(f"    {status} {r['id']} [{r['difficulty']:<6}] label={r['label']:<9} decision={r['decision'] or 'ERROR':<9} conf={conf:<6}{fc}{err}")
    print("=" * 60)


def main():
    with open(GOLDEN_FILE) as f:
        dataset = json.load(f)

    examples = dataset["examples"]
    client   = anthropic.AnthropicBedrock(
        aws_profile=AWS_PROFILE,
        aws_region=AWS_REGION,
    )

    print(f"\nScorecard — {dataset['project']}")
    print(f"  model    : {MODEL} (via AWS Bedrock / {AWS_REGION})")
    print(f"  examples : {len(examples)}")
    print(f"  retries  : up to {MAX_RETRIES} per example\n")

    results = []
    for example in examples:
        print(f"  evaluating {example['id']} [{example['difficulty']}] {example['title']} ...", end=" ", flush=True)
        result = evaluate_example(example, client)
        mark = "OK" if result["correct"] else "XX"
        fc   = " !! FALSE CONFIDENT" if result["false_confident"] else ""
        print(f"{mark}{fc}")
        results.append(result)

    metrics = compute_metrics(results)
    print_summary(metrics, results)

    # Write full report
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)
    ts = datetime.utcnow().strftime("%Y%m%dT%H%M%SZ")
    out_path = RESULTS_DIR / f"scorecard_{ts}.json"
    report = {
        "generated_at": datetime.utcnow().isoformat() + "Z",
        "model": MODEL,
        "dataset": dataset["project"],
        "metrics": metrics,
        "results": results,
    }
    with open(out_path, "w") as f:
        json.dump(report, f, indent=2)
    print(f"\n  Full report: {out_path}\n")


if __name__ == "__main__":
    main()
