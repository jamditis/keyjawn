"""AI evaluation for the curation pipeline using Claude Code CLI.

No direct API calls. All AI runs through 'claude -p' subprocess calls,
paid for by existing Claude Code subscription. The worker spins up
parallel subagents via asyncio.gather() for concurrent evaluation.
"""

from __future__ import annotations

import asyncio
import logging
import re

from worker.curation.models import CurationCandidate

log = logging.getLogger(__name__)

CLI_TIMEOUT = 90  # seconds per subprocess call


async def _run_claude(prompt: str, model: str = "sonnet") -> str:
    """Run a Claude Code CLI prompt and return the response text.

    Uses 'claude -p' (print mode) for non-interactive one-shot prompts.
    No API key needed -- uses the CLI's own auth/subscription.
    """
    try:
        process = await asyncio.create_subprocess_exec(
            "claude", "-p", prompt, "--model", model,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        stdout, stderr = await asyncio.wait_for(
            process.communicate(), timeout=CLI_TIMEOUT
        )
    except asyncio.TimeoutError:
        log.warning("claude CLI timed out after %ds", CLI_TIMEOUT)
        return ""
    except FileNotFoundError:
        log.error("claude CLI not found")
        return ""
    except Exception:
        log.exception("claude CLI failed")
        return ""

    if process.returncode != 0:
        log.warning("claude CLI returned %d: %s", process.returncode, stderr.decode()[:200])
        return ""

    return stdout.decode().strip()


# --- Evaluation prompt (quick-check + investigation in one call) ---

def build_evaluate_prompt(candidate: CurationCandidate) -> str:
    """Build a single evaluation prompt that covers relevance + quality."""
    return f"""Evaluate this content for a developer tools curation account that shares interesting CLI tools, terminal projects, and indie developer work.

Title: {candidate.title}
Author: {candidate.author}
Description: {candidate.description[:500]}
Source: {candidate.source}
URL: {candidate.url}

Answer each line exactly in this format:
RELEVANT: yes or no
REASONING: one-line explanation
OPEN_SOURCE: yes or no or unknown
INDIE: yes or no or unknown (is the creator a solo/indie dev or small team?)
CORPORATE: yes or no (is this a large company product launch?)
CLICKBAIT: yes or no
QUALITY: N/10 (overall quality and relevance score)"""


def parse_evaluate_response(text: str) -> dict:
    """Parse the evaluation response into structured data."""
    text_upper = text.upper()

    def _check(key: str) -> bool:
        pattern = rf"{key}:\s*(YES)"
        return bool(re.search(pattern, text_upper))

    def _extract_score() -> float:
        match = re.search(r"QUALITY:\s*(\d+(?:\.\d+)?)\s*/\s*10", text_upper)
        return float(match.group(1)) if match else 0.0

    reasoning = ""
    for line in text.strip().split("\n"):
        if line.upper().startswith("REASONING:"):
            reasoning = line.split(":", 1)[1].strip()
            break

    return {
        "relevant": _check("RELEVANT"),
        "reasoning": reasoning,
        "is_oss": _check("OPEN_SOURCE"),
        "is_indie": _check("INDIE"),
        "is_corporate": _check("CORPORATE"),
        "is_clickbait": _check("CLICKBAIT"),
        "quality_score": _extract_score(),
        "raw": text.strip(),
    }


# --- Draft prompt (final judgment + post writing) ---

def build_draft_prompt(
    candidate: CurationCandidate,
    evaluation: dict,
    platform: str = "twitter",
) -> str:
    """Build the draft writing prompt."""
    from worker.content import PLATFORM_LIMITS
    char_limit = PLATFORM_LIMITS.get(platform, 280)

    return f"""You are drafting a social media post for @KeyJawn, a developer tools curation account.

Voice: developer-to-developer. Short sentences. No hype. No exclamation marks. No hashtag spam. No emoji strings. Contractions are fine.

Content to share:
Title: {candidate.title}
Author: {candidate.author}
Source: {candidate.source}
URL: {candidate.url}

Evaluation: {evaluation.get('reasoning', '')}
Quality: {evaluation.get('quality_score', 0)}/10

Should we share this? Answer on the first line.

If yes, write a {platform} post (max {char_limit} chars) that adds a brief take or context, credits the creator, and puts the link at the end.

Format:
DECISION: SHARE or SKIP
REASONING: [one line why]
DRAFT: [post text, only if SHARE]"""


def parse_draft_response(text: str) -> dict:
    """Parse the draft response."""
    lines = text.strip().split("\n")

    decision = "skip"
    reasoning = ""
    in_draft = False
    draft_lines = []

    for line in lines:
        upper = line.upper().strip()
        if upper.startswith("DECISION:"):
            val = line.split(":", 1)[1].strip().upper()
            decision = "share" if "SHARE" in val else "skip"
            in_draft = False
        elif upper.startswith("REASONING:"):
            reasoning = line.split(":", 1)[1].strip()
            in_draft = False
        elif upper.startswith("DRAFT:"):
            draft_lines.append(line.split(":", 1)[1].strip())
            in_draft = True
        elif in_draft:
            draft_lines.append(line)

    draft = "\n".join(draft_lines).strip() if draft_lines else ""

    return {
        "share": decision == "share",
        "reasoning": reasoning,
        "draft": draft,
    }


async def evaluate_candidate(
    candidate: CurationCandidate, platform: str = "twitter"
) -> dict:
    """Full evaluation of a single candidate using Claude Code CLI.

    Runs two sequential claude -p calls:
    1. Evaluate: relevance, quality, OSS/indie classification
    2. Draft: final share/skip decision + post text (only if eval passes)

    Returns a dict with all evaluation results.
    """
    # Step 1: Evaluate
    eval_prompt = build_evaluate_prompt(candidate)
    eval_text = await _run_claude(eval_prompt, model="haiku")
    if not eval_text:
        return {"relevant": False, "reasoning": "CLI evaluation failed"}

    evaluation = parse_evaluate_response(eval_text)

    # Early exit if not relevant or low quality
    if not evaluation["relevant"] or evaluation["is_clickbait"]:
        return evaluation
    if evaluation["quality_score"] < 6.0:
        return evaluation

    # Step 2: Draft post (only for candidates that pass evaluation)
    draft_prompt = build_draft_prompt(candidate, evaluation, platform)
    draft_text = await _run_claude(draft_prompt, model="sonnet")
    if not draft_text:
        evaluation["share"] = False
        return evaluation

    draft_result = parse_draft_response(draft_text)
    evaluation.update(draft_result)
    return evaluation


async def evaluate_batch(
    candidates: list[CurationCandidate],
    platform: str = "twitter",
    max_parallel: int = 5,
) -> list[tuple[CurationCandidate, dict]]:
    """Evaluate multiple candidates in parallel using Claude Code CLI subagents.

    Spins up to max_parallel concurrent subprocess evaluations.
    Returns list of (candidate, result) tuples for candidates that pass.
    """
    semaphore = asyncio.Semaphore(max_parallel)

    async def _eval_one(c: CurationCandidate) -> tuple[CurationCandidate, dict]:
        async with semaphore:
            result = await evaluate_candidate(c, platform)
            return (c, result)

    tasks = [_eval_one(c) for c in candidates]
    results = await asyncio.gather(*tasks, return_exceptions=True)

    approved = []
    for r in results:
        if isinstance(r, Exception):
            log.error("Evaluation error: %s", r)
            continue
        candidate, result = r
        if result.get("share") and result.get("draft"):
            approved.append((candidate, result))

    log.info("Batch evaluation: %d/%d approved", len(approved), len(candidates))
    return approved
