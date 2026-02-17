"""Curated account list for on-platform engagement.

These accounts are monitored for content to like, repost, or engage with.
The worker checks their recent posts during on-platform scans.
"""

from __future__ import annotations

CURATED_ACCOUNTS: dict[str, list[dict]] = {
    "twitter": [
        {"handle": "ThePrimeagen", "tags": ["terminal", "vim", "dev"]},
        {"handle": "fireship_dev", "tags": ["cli", "tools"]},  # Fireship
        {"handle": "NetworkChuck", "tags": ["networking", "terminal"]},
        {"handle": "mitchellh", "tags": ["terminal", "ghostty"]},
        {"handle": "typecraft_dev", "tags": ["cli", "linux"]},  # typecraft
        {"handle": "sharkdp86", "tags": ["cli", "rust", "oss"]},  # bat, fd, hyperfine
        {"handle": "junegunn", "tags": ["cli", "vim", "fzf"]},  # fzf, vim-plug
        {"handle": "burntsushi5", "tags": ["cli", "rust", "ripgrep"]},
        {"handle": "astral_sh", "tags": ["python", "cli", "ruff"]},  # Astral (ruff, uv)
    ],
    "bluesky": [
        {"handle": "mitchellh.com", "tags": ["terminal", "ghostty"]},
        {"handle": "simulacrum.bsky.social", "tags": ["rust", "cli"]},
    ],
}


def get_accounts_for_platform(platform: str) -> list[dict]:
    """Get the curated account list for a platform."""
    return CURATED_ACCOUNTS.get(platform, [])


def get_all_handles(platform: str) -> list[str]:
    """Get just the handles for a platform."""
    return [a["handle"] for a in get_accounts_for_platform(platform)]
