"""Content generation for social media posts using Gemini CLI."""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass
from typing import Optional

from worker.subprocesses import SubprocessOwner

log = logging.getLogger(__name__)

PLATFORM_LIMITS: dict[str, int] = {
    "twitter": 280,
    "bluesky": 300,
    "producthunt": 260,
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
        "About KeyJawn: keyboard app for CLI/LLM agents. "
        "Android-only: permanent Esc/Tab/Ctrl/arrow row, voice input, slash commands, "
        "and a free lite version. The $4 Android full version adds SCP image upload. "
        "KeyJawn Lite has active internal and closed Google Play test tracks, but Google Play "
        "production is inactive. Never claim public Google Play availability. "
        "Other Android UX features: backspace acceleration (two-stage, faster the longer you hold), "
        "spacebar cursor movement (hold spacebar + drag horizontally to reposition cursor), "
        "adaptive Enter key (label and action change to Go/Send/Search/Next/Done based on context), "
        "double-tap space inserts period+space, auto-capitalize after sentence-ending punctuation, "
        "differentiated haptic feedback per key type (Enter/Shift/Ctrl/swipe each feel different). "
        "iOS-only: a remote SSH terminal and companion custom keyboard. Commands execute on the "
        "user-configured remote host, not on iOS. The app has no local shell or device-file browser. "
        "Copied-image upload writes one pasteboard image to a configured key-authenticated remote host through SFTP; "
        "that upload feature does not browse local files or list, read, or download remote files. "
        "The keyboard works only in apps and text fields that "
        "support third-party keyboards. It inserts text or control sequences, and the receiving app decides "
        "how to interpret them. The built-in SSH terminal sends terminal bytes directly. "
        "Basic typing works without Allow Full Access. "
        "Full Access is optional and is used only for copied-image upload, shared keyboard "
        "settings, user-created shortcuts, or clipboard history. "
        "A fresh signed build 9 archive was created and verified from the corrected source on August 31, "
        "2026. The exact archive was uploaded, and Apple processed build 9 as valid. Build 9 is selected "
        "for version 1.0. Apple approved version 1.0 on September 17, 2026, and manual release is enabled. "
        "App Store Connect reports pending developer release, and the app is not publicly available yet. "
        "Do not publish an App Store URL or tell people to download the iOS app until the public listing is verified. "
        f"Link: {tracked_url}"
    )

    lines.append(
        "Writing rules: developer-to-developer voice. Short sentences. "
        "Use contractions. Max 2 sentences before getting to the point. "
        "No rhetorical questions. No hashtag spam. No exclamation marks. "
        "No emoji strings. No hype words. No filler. No fake emotion. "
        "Never trash competitors. Put links at the end if needed. "
        "Name the platform for platform-specific features. Never say that the iOS keyboard works in "
        "any app, every app, or secure and phone-pad fields. Never say that KeyJawn executes code on "
        "iOS or browses device files. Never claim that iOS copied-image upload works with password authentication. "
        "Never describe iOS extension controls as hardware key events. Never describe an Android-only feature as an iOS feature. "
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


async def generate_content(
    req: ContentRequest,
    subprocesses: SubprocessOwner | None = None,
) -> Optional[str]:
    """Generate content using Gemini CLI. Returns None on failure."""
    prompt = build_generation_prompt(req)
    owner = subprocesses or SubprocessOwner(logger=log)

    try:
        result = await owner.run_exec(
            "gemini",
            "-p",
            prompt,
            "--output-format",
            "text",
            timeout=60,
        )
    except FileNotFoundError:
        log.error("Gemini CLI not found")
        return None
    except Exception:
        log.exception("Failed to run Gemini CLI")
        return None

    if result.timed_out:
        log.warning("Gemini CLI timed out after 60s")
        return None
    if result.returncode != 0:
        log.warning(
            "Gemini CLI returned %d: %s",
            result.returncode,
            result.stderr.decode().strip(),
        )
        return None

    text = result.stdout.decode().strip()

    # Strip surrounding quotes if present
    if len(text) >= 2 and text[0] == '"' and text[-1] == '"':
        text = text[1:-1]

    if not text:
        log.warning("Gemini CLI returned empty output")
        return None

    return text
