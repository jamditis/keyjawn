"""Configuration for keyjawn-worker."""

from __future__ import annotations

import json
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
    api_key: str
    api_secret: str
    access_token: str
    access_token_secret: str
    bearer_token: str


@dataclass(frozen=True)
class BlueskyConfig:
    handle: str
    app_password: str


@dataclass(frozen=True)
class TelegramConfig:
    bot_token: str
    chat_id: str


@dataclass(frozen=True)
class RedisConfig:
    host: str = "100.122.208.15"
    port: int = 6379
    password: str = ""
    channel_prefix: str = "keyjawn-worker"


@dataclass(frozen=True)
class Config:
    twitter: TwitterConfig
    bluesky: BlueskyConfig
    telegram: TelegramConfig
    redis: RedisConfig
    db_path: str = "keyjawn-worker.db"
    action_window_start_hour: int = 18
    action_window_end_hour: int = 21
    max_actions_per_day: int = 3
    max_posts_per_platform: int = 3
    approval_timeout_seconds: int = 7200

    @classmethod
    def from_pass(cls) -> Config:
        """Build config by reading credentials from the pass store."""
        twitter_creds = _pass_get_json("claude/services/keyjawn-worker-twitter")
        bluesky_creds = _pass_get_json("claude/services/keyjawn-worker-bluesky")
        telegram_token = _pass_get("claude/tokens/telegram-bot")
        redis_password = _pass_get("claude/services/redis-password")

        return cls(
            twitter=TwitterConfig(
                api_key=twitter_creds["api_key"],
                api_secret=twitter_creds["api_secret"],
                access_token=twitter_creds["access_token"],
                access_token_secret=twitter_creds["access_token_secret"],
                bearer_token=twitter_creds["bearer_token"],
            ),
            bluesky=BlueskyConfig(
                handle=bluesky_creds["handle"],
                app_password=bluesky_creds["app_password"],
            ),
            telegram=TelegramConfig(
                bot_token=telegram_token,
                chat_id=bluesky_creds.get("telegram_chat_id", ""),
            ),
            redis=RedisConfig(
                password=redis_password,
            ),
        )

    @classmethod
    def for_testing(cls) -> Config:
        """Return an in-memory config suitable for tests. No real credentials."""
        return cls(
            twitter=TwitterConfig(
                api_key="test-api-key",
                api_secret="test-api-secret",
                access_token="test-access-token",
                access_token_secret="test-access-token-secret",
                bearer_token="test-bearer-token",
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
            db_path=":memory:",
        )
