"""Configuration for keyjawn-worker."""

from __future__ import annotations

import json
import os
import subprocess
from dataclasses import dataclass, field


def _pass_get(key: str) -> str:
    """Read a single value from the pass password store."""
    result = subprocess.run(
        ["pass", "show", key],
        capture_output=True,
        text=True,
        check=True,
    )
    return result.stdout.strip()


def _pass_get_json(key: str) -> dict:
    """Read a JSON value from the pass password store."""
    return json.loads(_pass_get(key))


@dataclass(frozen=True)
class TwitterConfig:
    username: str
    email: str
    password: str
    cookies_path: str = "twitter_cookies.json"


@dataclass(frozen=True)
class BlueskyConfig:
    handle: str
    app_password: str


@dataclass(frozen=True)
class TelegramConfig:
    bot_token: str
    chat_id: str


@dataclass(frozen=True)
class ProductHuntConfig:
    developer_token: str = ""


@dataclass(frozen=True)
class RedisConfig:
    host: str = "100.122.208.15"
    port: int = 6379
    password: str = ""
    channel_prefix: str = "keyjawn-worker"


@dataclass(frozen=True)
class CurationConfig:
    youtube_api_key: str = ""
    twitch_client_id: str = ""
    twitch_client_secret: str = ""
    google_alert_urls: tuple = ()
    max_curated_shares_per_day: int = 2
    max_parallel_evaluations: int = 5
    scan_interval_hours: int = 6
    twitch_scan_interval_hours: int = 8


@dataclass(frozen=True)
class SocialScrollerConfig:
    enabled: bool = True
    ssh_host: str = ""  # empty = run locally (worker and DISPLAY=:99 are both on officejawn)
    script_path: str = "~/social-scroller.py"
    scroll_duration: int = 15
    search_platforms: tuple[str, ...] = ()
    feed_platforms: tuple[str, ...] = ("twitter", "bluesky")


@dataclass(frozen=True)
class Config:
    twitter: TwitterConfig
    bluesky: BlueskyConfig
    telegram: TelegramConfig
    redis: RedisConfig
    producthunt: ProductHuntConfig = field(default_factory=ProductHuntConfig)
    curation: CurationConfig = field(default_factory=CurationConfig)
    social_scroller: SocialScrollerConfig = field(
        default_factory=SocialScrollerConfig,
    )
    db_path: str = "keyjawn-worker.db"
    action_window_start_hour: int = 18
    action_window_end_hour: int = 21
    max_actions_per_day: int = 3
    max_posts_per_platform: int = 3
    approval_timeout_seconds: int = 7200

    @classmethod
    def from_pass(cls) -> Config:
        """Build config by reading credentials from the pass store."""
        # Twitter: stored as username\nemail\npassword
        twitter_raw = _pass_get("claude/social/twitter-keyjawn")
        twitter_lines = twitter_raw.split("\n")

        bluesky_raw = _pass_get("claude/services/bluesky-keyjawn")
        bluesky_lines = bluesky_raw.split("\n")

        telegram_token = _pass_get("claude/tokens/telegram-bot")

        from pathlib import Path
        redis_password = Path(
            "/home/jamditis/.config/brain/redis.key"
        ).read_text().strip()

        # Product Hunt: optional, may not be set on all machines
        try:
            ph_token = _pass_get("claude/tokens/producthunt-dev")
        except subprocess.CalledProcessError:
            ph_token = ""

        # Curation: optional API keys
        try:
            yt_key = _pass_get("claude/api/youtube")
        except subprocess.CalledProcessError:
            yt_key = ""
        try:
            twitch_creds = _pass_get("claude/api/twitch").split("\n")
            twitch_id = twitch_creds[0]
            twitch_secret = twitch_creds[1] if len(twitch_creds) > 1 else ""
        except subprocess.CalledProcessError:
            twitch_id = ""
            twitch_secret = ""

        return cls(
            twitter=TwitterConfig(
                username=twitter_lines[0],
                email=twitter_lines[1] if len(twitter_lines) > 1 else "",
                password=twitter_lines[2] if len(twitter_lines) > 2 else "",
            ),
            bluesky=BlueskyConfig(
                handle=bluesky_lines[0],
                app_password=bluesky_lines[1] if len(bluesky_lines) > 1 else "",
            ),
            telegram=TelegramConfig(
                bot_token=telegram_token,
                chat_id=os.environ.get("TELEGRAM_CHAT_ID", "743339387"),
            ),
            redis=RedisConfig(
                password=redis_password,
            ),
            producthunt=ProductHuntConfig(
                developer_token=ph_token,
            ),
            curation=CurationConfig(
                youtube_api_key=yt_key,
                twitch_client_id=twitch_id,
                twitch_client_secret=twitch_secret,
            ),
            social_scroller=SocialScrollerConfig(),
        )

    @classmethod
    def for_testing(cls) -> Config:
        """Return an in-memory config suitable for tests. No real credentials."""
        return cls(
            twitter=TwitterConfig(
                username="test-user",
                email="test@example.com",
                password="test-password",
            ),
            bluesky=BlueskyConfig(
                handle="test.bsky.social",
                app_password="test-app-password",
            ),
            telegram=TelegramConfig(
                bot_token="test-bot-token",
                chat_id="test-chat-id",
            ),
            redis=RedisConfig(
                password="test-redis-password",
            ),
            producthunt=ProductHuntConfig(
                developer_token="test-ph-token",
            ),
            curation=CurationConfig(),
            social_scroller=SocialScrollerConfig(),
            db_path=":memory:",
        )
