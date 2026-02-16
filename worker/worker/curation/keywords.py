"""Keyword-based relevance scoring for curation candidates (pipeline stage 1)."""

from __future__ import annotations

from worker.curation.models import CurationCandidate

POSITIVE_SIGNALS = [
    "terminal",
    "cli",
    "command line",
    "ssh",
    "keyboard",
    "android",
    "mobile dev",
    "mobile coding",
    "coding on phone",
    "developer tool",
    "dev tool",
    "shell",
    "tmux",
    "vim",
    "neovim",
    "terminal emulator",
    "claude code",
    "gemini cli",
    "llm agent",
    "ai agent",
    "copilot",
    "code editor",
    "ide",
    "rust cli",
    "go cli",
    "python cli",
]

INDIE_SIGNALS = [
    "open source",
    "open-source",
    "oss",
    "free",
    "indie",
    "side project",
    "solo dev",
    "solo developer",
    "no vc",
    "bootstrapped",
    "github.com",
    "gitlab.com",
    "codeberg.org",
    "sourcehut",
]

NEGATIVE_SIGNALS = [
    "limited time",
    "discount",
    "coupon",
    "sponsored",
    "promotion",
    "affiliate",
    "crypto",
    "web3",
    "nft",
    "blockchain",
    "get rich",
    "make money",
    "subscribe for more",
]


def score_keywords(candidate: CurationCandidate) -> float:
    """Score a candidate 0.0-1.0 based on keyword relevance.

    Stage 1 of the evaluation pipeline. Runs locally, no API calls.
    """
    text = f"{candidate.title} {candidate.description}".lower()

    neg_hits = sum(1 for s in NEGATIVE_SIGNALS if s in text)
    if neg_hits >= 2:
        return 0.0
    neg_penalty = neg_hits * 0.3

    pos_hits = sum(1 for s in POSITIVE_SIGNALS if s in text)

    if pos_hits >= 3:
        score = 0.8
    elif pos_hits >= 2:
        score = 0.6
    elif pos_hits >= 1:
        score = 0.4
    else:
        score = 0.1

    indie_hits = sum(1 for s in INDIE_SIGNALS if s in text)
    if indie_hits >= 2:
        score = min(score + 0.25, 1.0)
    elif indie_hits >= 1:
        score = min(score + 0.15, 1.0)

    score = max(score - neg_penalty, 0.0)
    return round(score, 2)
