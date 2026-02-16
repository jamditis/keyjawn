"""Tests for the action executor."""

import pytest
import pytest_asyncio
from datetime import date

from worker.executor import ActionPicker, EscalationTier
from worker.config import Config, TwitterConfig, BlueskyConfig, TelegramConfig, RedisConfig
from worker.db import Database


@pytest_asyncio.fixture
async def db():
    database = Database(":memory:")
    await database.init()
    yield database
    await database.close()


def _config_with_max_actions(n: int) -> Config:
    """Create a test config with a specific max_actions_per_day."""
    return Config(
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
        redis=RedisConfig(password="test-redis-password"),
        db_path=":memory:",
        max_actions_per_day=n,
    )


def test_escalation_tier_original_post():
    tier = ActionPicker.get_escalation_tier(
        action_type="original_post",
        platform="twitter",
    )
    assert tier == EscalationTier.AUTO


def test_escalation_tier_reply():
    tier = ActionPicker.get_escalation_tier(
        action_type="reply",
        platform="twitter",
    )
    assert tier == EscalationTier.BUTTONS


def test_escalation_tier_reddit_anything():
    tier = ActionPicker.get_escalation_tier(
        action_type="original_post",
        platform="reddit",
    )
    assert tier == EscalationTier.BUTTONS


def test_escalation_tier_outreach_dm():
    tier = ActionPicker.get_escalation_tier(
        action_type="outreach_dm",
        platform="bluesky",
    )
    assert tier == EscalationTier.BUTTONS


def test_escalation_tier_like():
    tier = ActionPicker.get_escalation_tier(
        action_type="like",
        platform="twitter",
    )
    assert tier == EscalationTier.AUTO


@pytest.mark.asyncio
async def test_pick_actions_respects_daily_limit(db):
    config = _config_with_max_actions(2)

    # Log 2 actions already done today
    await db.log_action("tweet", "twitter", "post 1", "posted")
    await db.log_action("tweet", "twitter", "post 2", "posted")

    picker = ActionPicker(config=config, db=db)
    actions = await picker.pick_actions()
    assert len(actions) == 0  # at daily limit


@pytest.mark.asyncio
async def test_pick_actions_from_calendar(db):
    config = Config.for_testing()
    today = date.today().isoformat()

    await db.add_calendar_entry(
        scheduled_date=today,
        pillar="demo",
        platform="twitter",
        content_draft="Voice input demo post",
    )

    picker = ActionPicker(config=config, db=db)
    actions = await picker.pick_actions()
    assert len(actions) >= 1
    assert actions[0]["source"] == "calendar"
