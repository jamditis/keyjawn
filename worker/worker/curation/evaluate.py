"""AI evaluation for the curation pipeline using Claude Code CLI.

No direct API calls. All AI runs through 'claude -p' subprocess calls,
paid for by existing Claude Code subscription. The worker spins up
parallel subagents via asyncio.gather() for concurrent evaluation.
"""

from __future__ import annotations

import asyncio
import base64
import logging
import os
import re

from worker.curation.models import CurationCandidate

log = logging.getLogger(__name__)

CLI_TIMEOUT = 120  # seconds per CLI call


async def _run_claude(prompt: str, model: str = "sonnet") -> str:
    """Run a Claude Code CLI prompt and return the response text.

    Follows claude-scheduler.py pattern: base64-encode the prompt for
    safe shell transmission, pipe it into claude -p, and use shell-level
    timeout (timeout --foreground) instead of asyncio.wait_for which
    can prevent the CLI from initializing properly.
    """
    encoded = base64.b64encode(prompt.encode()).decode()

    # Base64 pipe -> timeout wrapper -> claude -p
    # Same pattern as claude-scheduler.py bash wrapper scripts
    cmd = (
        f"echo '{encoded}' | base64 -d | "
        f"timeout --foreground --kill-after=30 {CLI_TIMEOUT} "
        f"claude --dangerously-skip-permissions -p --model {model}"
    )

    # Strip nesting-detection env vars so claude -p doesn't refuse to run
    # when invoked from within a Claude Code session.
    clean_env = {
        k: v for k, v in os.environ.items()
        if k not in ("CLAUDECODE", "CLAUDE_CODE_ENTRYPOINT")
    }

    try:
        process = await asyncio.create_subprocess_shell(
            cmd,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
            env=clean_env,
        )
        stdout, stderr = await process.communicate()
    except FileNotFoundError:
        log.error("claude CLI not found")
        return ""
    except Exception:
        log.exception("claude CLI subprocess failed")
        return ""

    if process.returncode == 124:
        log.warning("claude CLI timed out after %ds", CLI_TIMEOUT)
        return ""
    if process.returncode != 0:
        log.warning(
            "claude CLI returned %d: %s",
            process.returncode,
            stderr.decode()[:200],
        )
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
CLICKBAIT: yes or no (only if the title is actively deceptive or misleading — standard YouTube SEO like "Best X" or "Free Y" does NOT count as clickbait)
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

Voice: developer-to-developer. Short sentences. No hype. No exclamation marks. No hashtag spam. Contractions are fine.

STRICT RULES — violating any of these means the draft will be rejected:
- ZERO emojis. Not one. No fire, no rocket, no checkmark, no pointing finger, nothing.
- No hashtags unless the content creator uses one as a brand name.
- No "thread" or "1/" numbering.
- Sentence case only. Never Title Case.
- No filler phrases ("check this out", "you need to see this", "this is amazing").

Content to share:
Title: {candidate.title}
Author: {candidate.author}
Source: {candidate.source}
URL: {candidate.url}

Evaluation: {evaluation.get('reasoning', '')}
Quality: {evaluation.get('quality_score', 0)}/10

Should we share this? SHARE if it's genuinely useful or interesting to developers who use CLI tools and terminals. SKIP if it's low effort, clickbaity, overly promotional, or not interesting enough to warrant a post.

If yes, write a {platform} post (max {char_limit} chars) that adds a brief take or context, credits the creator, and puts the link at the end. Keep it dry and matter-of-fact. Imagine a developer you respect shared this in a group chat with one line of context.

Format:
DECISION: SHARE or SKIP
REASONING: [one line why]
DRAFT: [post text, only if SHARE]"""


def build_batch_draft_prompt(
    candidate: CurationCandidate,
    evaluation: dict,
    platform: str = "twitter",
) -> str:
    """Build a prompt that asks for 4 draft variants (A/B/C/D).

    Each variant takes a different angle or framing. All must respect
    platform character limits and voice rules.
    """
    from worker.content import PLATFORM_LIMITS
    char_limit = PLATFORM_LIMITS.get(platform, 280)

    return f"""You are drafting social media posts for @KeyJawn, a developer tools curation account.

Voice: developer-to-developer. Short sentences. No hype. No exclamation marks. No hashtag spam. Contractions are fine.

STRICT RULES — violating any of these means the draft will be rejected:
- ZERO emojis. Not one. No fire, no rocket, no checkmark, no pointing finger, nothing.
- No hashtags unless the content creator uses one as a brand name.
- No "thread" or "1/" numbering.
- Sentence case only. Never Title Case.
- No filler phrases ("check this out", "you need to see this", "this is amazing").
- No "it's not just X — it's Y" patterns.

Content to share:
Title: {candidate.title}
Author: {candidate.author}
Source: {candidate.source}
URL: {candidate.url}

Evaluation: {evaluation.get('reasoning', '')}
Quality: {evaluation.get('quality_score', 0)}/10

Should we share this? SHARE if it's genuinely useful or interesting to developers who use CLI tools and terminals. SKIP if it's low effort, clickbaity, overly promotional, or not interesting enough to warrant a post.

If SHARE, write 4 different {platform} posts (each max {char_limit} chars). Each variant should take a different angle: one might focus on the tech, another on the creator, another on the use case, another on what makes it stand out. All must add context, credit the creator, and put the link at the end. Keep them dry and matter-of-fact.

Format your response exactly like this:
DECISION: SHARE or SKIP
REASONING: [one line why]
DRAFT_A: [variant A text, only if SHARE]
DRAFT_B: [variant B text, only if SHARE]
DRAFT_C: [variant C text, only if SHARE]
DRAFT_D: [variant D text, only if SHARE]"""


def _strip_emojis(text: str) -> str:
    """Remove all emoji characters from text as a safety net."""
    # Covers all major Unicode emoji ranges
    emoji_pattern = re.compile(
        "["
        "\U0001F600-\U0001F64F"  # emoticons
        "\U0001F300-\U0001F5FF"  # symbols & pictographs
        "\U0001F680-\U0001F6FF"  # transport & map
        "\U0001F1E0-\U0001F1FF"  # flags
        "\U0001F900-\U0001F9FF"  # supplemental symbols
        "\U0001FA00-\U0001FA6F"  # chess symbols
        "\U0001FA70-\U0001FAFF"  # symbols extended-A
        "\U00002702-\U000027B0"  # dingbats
        "\U000024C2-\U0001F251"  # enclosed characters
        "\U0000FE0F"             # variation selector
        "]+",
        flags=re.UNICODE,
    )
    return emoji_pattern.sub("", text).strip()


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

    # Safety net: strip any emojis the AI might have slipped in
    draft = _strip_emojis(draft)

    return {
        "share": decision == "share",
        "reasoning": reasoning,
        "draft": draft,
    }


def parse_batch_draft_response(text: str) -> dict:
    """Parse a batch draft response with up to 4 variants.

    Returns {"share": bool, "reasoning": str, "drafts": {"A": str, ...}}.
    Handles partial responses (fewer than 4 drafts). Strips emojis from
    all draft text.
    """
    lines = text.strip().split("\n")

    decision = "skip"
    reasoning = ""
    drafts: dict[str, str] = {}
    current_label: str | None = None
    current_lines: list[str] = []

    draft_labels = {"DRAFT_A", "DRAFT_B", "DRAFT_C", "DRAFT_D"}

    def _flush_draft():
        nonlocal current_label, current_lines
        if current_label and current_lines:
            key = current_label.split("_")[1]  # "A", "B", "C", or "D"
            draft_text = _strip_emojis("\n".join(current_lines).strip())
            if draft_text:
                drafts[key] = draft_text
        current_label = None
        current_lines = []

    for line in lines:
        upper = line.upper().strip()
        if upper.startswith("DECISION:"):
            _flush_draft()
            val = line.split(":", 1)[1].strip().upper()
            decision = "share" if "SHARE" in val else "skip"
        elif upper.startswith("REASONING:"):
            _flush_draft()
            reasoning = line.split(":", 1)[1].strip()
        elif any(upper.startswith(f"{label}:") for label in draft_labels):
            _flush_draft()
            label = upper.split(":")[0]
            current_label = label
            current_lines.append(line.split(":", 1)[1].strip())
        elif current_label:
            current_lines.append(line)

    _flush_draft()

    return {
        "share": decision == "share",
        "reasoning": reasoning,
        "drafts": drafts if decision == "share" else {},
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
    if not evaluation["relevant"]:
        return evaluation
    if evaluation["quality_score"] < 5.0:
        return evaluation

    # Step 2: Batch draft (4 variants via Opus)
    draft_prompt = build_batch_draft_prompt(candidate, evaluation, platform)
    draft_text = await _run_claude(draft_prompt, model="opus")
    if not draft_text:
        evaluation["share"] = False
        return evaluation

    draft_result = parse_batch_draft_response(draft_text)
    evaluation.update(draft_result)
    # Backward compat: set "draft" to the first variant
    if draft_result.get("drafts"):
        first_key = sorted(draft_result["drafts"].keys())[0]
        evaluation["draft"] = draft_result["drafts"][first_key]
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
        if result.get("share") and result.get("drafts"):
            approved.append((candidate, result))

    log.info("Batch evaluation: %d/%d approved", len(approved), len(candidates))
    return approved
