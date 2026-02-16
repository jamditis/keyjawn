"""Content generation for social media posts using Gemini CLI."""

from __future__ import annotations

import asyncio
import logging
import re
from dataclasses import dataclass
from typing import Optional

log = logging.getLogger(__name__)

PLATFORM_LIMITS: dict[str, int] = {
    "twitter": 280,
    "bluesky": 300,
    "reddit": 10000,
    "hn": 10000,
    "devto": 50000,
}

BANNED_WORDS: list[str] = [
    "comprehensive",
    "sophisticated",
    "robust",
    "transformative",
    "leveraging",
    "seamlessly",
    "innovative",
    "cutting-edge",
    "state-of-the-art",
    "holistic",
    "synergy",
    "ecosystem",
    "paradigm",
    "empower",
    "game-changer",
    "revolutionary",
    "thrilled",
    "excited to announce",
]

BANNED_OPENERS: list[str] = [
    "ever struggled",
    "imagine a world",
    "if you're like me",
    "are you tired",
    "check out",
    "don't miss",
]


@dataclass
class ContentRequest:
    pillar: str  # demo, awareness, engagement, social_proof, behind_scenes
    platform: str  # twitter, bluesky, reddit, hn, devto
    topic: str
    context: Optional[str] = None  # for replies


def utm_url(platform: str, campaign: str = "worker") -> str:
    """Build a UTM-tagged URL for keyjawn.amditis.tech."""
    return (
        f"keyjawn.amditis.tech"
        f"?utm_source={platform}"
        f"&utm_medium=social"
        f"&utm_campaign={campaign}"
    )


def build_generation_prompt(req: ContentRequest) -> str:
    """Build a prompt for Gemini CLI to generate a social media post."""
    char_limit = PLATFORM_LIMITS.get(req.platform, 280)
    tracked_url = utm_url(req.platform)

    lines = [
        f"Write a {req.platform} post. Max {char_limit} characters.",
        f"Content pillar: {req.pillar}.",
        f"Topic: {req.topic}.",
    ]

    if req.context:
        lines.append(f"This is a reply. Context: {req.context}")

    lines.append(
        "About KeyJawn: Android keyboard for CLI/LLM agents. "
        "Permanent Esc/Tab/Ctrl/arrow row, voice input, SCP image upload, "
        "slash commands. $4 one-time purchase, free lite version. "
        f"Link: {tracked_url}"
    )

    lines.append(
        "Writing rules: developer-to-developer voice. Short sentences. "
        "Use contractions. Max 2 sentences before getting to the point. "
        "No rhetorical questions. No hashtag spam. No exclamation marks. "
        "No emoji strings. No hype words. No filler. No fake emotion. "
        "Never trash competitors. Put links at the end if needed. "
        f"If you include the link, use EXACTLY: {tracked_url}"
    )

    lines.append(
        f"Output ONLY the post text, nothing else. Max {char_limit} characters."
    )

    return "\n".join(lines)


def validate_generated_content(text: str, platform: str) -> list[str]:
    """Check content against rules. Returns a list of violations (empty = clean)."""
    violations: list[str] = []
    text_lower = text.lower()

    # Check length
    char_limit = PLATFORM_LIMITS.get(platform, 280)
    if len(text) > char_limit:
        violations.append(
            f"Too long: {len(text)} chars, limit is {char_limit} for {platform}"
        )

    # Check banned words
    for word in BANNED_WORDS:
        if word.lower() in text_lower:
            violations.append(f"Banned word: {word}")

    # Check banned openers
    for opener in BANNED_OPENERS:
        if text_lower.startswith(opener.lower()):
            violations.append(f"Banned opener: {opener}")

    # Check hashtag spam (more than 1)
    hashtag_count = len(re.findall(r"#\w+", text))
    if hashtag_count > 1:
        violations.append(f"Too many hashtags: {hashtag_count} (max 1)")

    # Check exclamation marks (more than 1)
    exclamation_count = text.count("!")
    if exclamation_count > 1:
        violations.append(
            f"Too many exclamation marks: {exclamation_count} (max 1)"
        )

    # Check emoji density (more than 2)
    emoji_pattern = re.compile(
        "["
        "\U0001f600-\U0001f64f"  # emoticons
        "\U0001f300-\U0001f5ff"  # symbols and pictographs
        "\U0001f680-\U0001f6ff"  # transport and map
        "\U0001f1e0-\U0001f1ff"  # flags
        "\U00002702-\U000027b0"  # dingbats
        "\U0000fe00-\U0000fe0f"  # variation selectors
        "\U0001f900-\U0001f9ff"  # supplemental symbols
        "\U0001fa00-\U0001fa6f"  # chess symbols
        "\U0001fa70-\U0001faff"  # symbols extended-a
        "\U00002600-\U000026ff"  # misc symbols
        "]+",
        flags=re.UNICODE,
    )
    emoji_count = len(emoji_pattern.findall(text))
    if emoji_count > 2:
        violations.append(f"Too many emoji: {emoji_count} (max 2)")

    return violations


async def generate_content(req: ContentRequest) -> Optional[str]:
    """Generate content using Gemini CLI. Returns None on failure."""
    prompt = build_generation_prompt(req)

    try:
        process = await asyncio.create_subprocess_exec(
            "gemini",
            "-p",
            prompt,
            "--output-format",
            "text",
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        stdout, stderr = await asyncio.wait_for(
            process.communicate(), timeout=60
        )
    except asyncio.TimeoutError:
        log.warning("Gemini CLI timed out after 60s")
        return None
    except FileNotFoundError:
        log.error("Gemini CLI not found")
        return None
    except Exception:
        log.exception("Failed to run Gemini CLI")
        return None

    if process.returncode != 0:
        log.warning(
            "Gemini CLI returned %d: %s",
            process.returncode,
            stderr.decode().strip(),
        )
        return None

    text = stdout.decode().strip()

    # Strip surrounding quotes if present
    if len(text) >= 2 and text[0] == '"' and text[-1] == '"':
        text = text[1:-1]

    if not text:
        log.warning("Gemini CLI returned empty output")
        return None

    return text
