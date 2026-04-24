"""
Scores a single seam for extraction risk using Claude.

Seams can have three action types:
  DELETE   — remove entirely (e.g. security liability), no extraction risk score
  EXTRACT  — move to standalone service, scored 1-10
  DISSOLVE — replaced by configuration change, scored 1-10 but recommendation is always "dissolve"

Scoring dimensions (1–10, higher = harder to extract):
  coupling          — how many other layers reference this seam
  test_coverage     — inverse: low tests = high risk
  data_tangle       — how entangled its data model is with other seams
  business_criticality — how much breakage would hurt in production

Weighted risk_score = coupling*0.30 + test_coverage*0.20 + data_tangle*0.25 + business_criticality*0.25
"""

import json
import anthropic

SYSTEM_PROMPT = """You are a senior software architect reviewing a Java Spring Boot application
called spring-music. Your job is to assess how risky it would be to extract or remove a given
code seam.

For each seam you receive, return ONLY a JSON object with exactly this shape:
{
  "coupling": <int 1-10>,
  "test_coverage": <int 1-10>,
  "data_tangle": <int 1-10>,
  "business_criticality": <int 1-10>,
  "rationale": "<two sentences max>"
}

Scoring guide (higher number = higher extraction risk):
- coupling: 1 = self-contained, 10 = referenced everywhere
- test_coverage: 1 = well-tested, 10 = no tests at all (untested = risky to move)
- data_tangle: 1 = owns its data cleanly, 10 = shared tables / mixed persistence
- business_criticality: 1 = demo/seed data, 10 = core domain that must never break

Return only the JSON, no markdown fences, no extra keys.
"""

CODEBASE_CONTEXT = """
spring-music is a sample Spring Boot 2.4.0 app demonstrating multi-backend persistence.
It stores a catalog of music albums and can persist them to H2, MySQL, PostgreSQL,
MongoDB, or Redis depending on the active Spring profile.

Key facts:
- Single AlbumRepository interface; four implementations selected via Spring profiles
- Domain model (Album) carries JPA annotations but is reused for MongoDB too
- No dedicated service layer — controllers call repositories directly
- No unit tests; only a context-load smoke test
- Configuration reads Cloud Foundry VCAP_SERVICES to auto-configure the datasource
- AlbumRepositoryPopulator seeds demo data on every startup via ApplicationReadyEvent
- ErrorController exposes unauthenticated crash endpoints (System.exit, OOM loop)
- All Actuator endpoints are exposed publicly (env vars, credentials, heap dumps)
- InfoController instantiates CfEnv directly (not injected — untestable)
- PUT/POST semantics are inverted: both call repository.save() with no distinction

Extraction strategy: Strangler Fig with API Facade.
Prerequisite: characterization tests must be written before any extraction begins.
"""


def score_seam(seam: dict, client: anthropic.Anthropic) -> dict:
    action = seam.get("action", "EXTRACT").upper()

    # DELETE seams are not scored — they are removed, not extracted
    if action.startswith("DELETE"):
        return {
            "id": seam["id"],
            "name": seam["name"],
            "action": seam["action"],
            "coupling": 1,
            "test_coverage": 10,
            "data_tangle": 1,
            "business_criticality": 10,
            "risk_score": None,
            "phase": seam.get("phase", "Phase 0"),
            "rationale": seam.get("notes", "Security liability — delete, do not extract."),
            "recommendation": "Delete immediately — security risk, not an extraction candidate.",
        }

    prompt = f"""{CODEBASE_CONTEXT}

Seam to assess:
  ID: {seam['id']}
  Name: {seam['name']}
  Action: {seam['action']}
  Description: {seam['description']}
  Files: {', '.join(seam['files'])}
  Notes: {seam['notes']}

Score this seam's extraction risk."""

    message = client.messages.create(
        model="claude-haiku-4-5-20251001",
        max_tokens=512,
        system=SYSTEM_PROMPT,
        messages=[{"role": "user", "content": prompt}],
    )

    raw = message.content[0].text.strip()
    scores = json.loads(raw)

    risk_score = round(
        scores["coupling"] * 0.30
        + scores["test_coverage"] * 0.20
        + scores["data_tangle"] * 0.25
        + scores["business_criticality"] * 0.25,
        1,
    )

    # Recommendation depends on action type and risk score
    if action.startswith("DISSOLVE"):
        recommendation = "Dissolve — refactor away CF dependency, do not extract as a service"
    elif risk_score <= 3.5:
        recommendation = "Extract first — low coupling and risk"
    elif risk_score <= 6.5:
        recommendation = "Extract alongside related seam — medium risk"
    else:
        recommendation = "Dissolve or refactor first — high risk, not directly extractable"

    return {
        "id": seam["id"],
        "name": seam["name"],
        "action": seam["action"],
        "coupling": scores["coupling"],
        "test_coverage": scores["test_coverage"],
        "data_tangle": scores["data_tangle"],
        "business_criticality": scores["business_criticality"],
        "risk_score": risk_score,
        "phase": seam.get("phase", ""),
        "rationale": scores["rationale"],
        "recommendation": recommendation,
    }
