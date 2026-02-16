"""Integration tests -- tests the full pipeline with mocked platform APIs."""

import pytest
import pytest_asyncio
from datetime import date

from worker.config import Config, TwitterConfig, BlueskyConfig, TelegramConfig, RedisConfig
from worker.db import Database
from worker.executor import ActionPicker
from worker.monitor import Monitor


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


@pytest.mark.asyncio
async def test_full_pipeline_monitor_to_action(db):
    """Test: monitor finds something -> queues it -> picker selects it."""
    config = Config.for_testing()
    monitor = Monitor(config, db)

    findings = [
        {
            "url": "https://twitter.com/status/1",
            "text": "anyone know a good keyboard for SSH on my phone?",
            "author": "devuser",
            "platform": "twitter",
        },
    ]

    queued = await monitor.queue_new_findings(findings)
    assert queued == 1

    picker = ActionPicker(config, db)
    actions = await picker.pick_actions()
    assert len(actions) == 1
    assert actions[0]["source"] == "finding"
    assert actions[0]["platform"] == "twitter"


@pytest.mark.asyncio
async def test_full_pipeline_calendar_to_action(db):
    """Test: calendar entry -> picker selects it."""
    config = Config.for_testing()
    today = date.today().isoformat()

    await db.add_calendar_entry(
        scheduled_date=today,
        pillar="demo",
        platform="twitter",
        content_draft="Voice input demo",
    )

    picker = ActionPicker(config, db)
    actions = await picker.pick_actions()
    assert len(actions) == 1
    assert actions[0]["source"] == "calendar"
    assert actions[0]["content"] == "Voice input demo"


@pytest.mark.asyncio
async def test_daily_limit_enforced(db):
    """Test: can't exceed max_actions_per_day."""
    config = _config_with_max_actions(1)

    await db.log_action("tweet", "twitter", "already posted", "posted")

    picker = ActionPicker(config, db)
    actions = await picker.pick_actions()
    assert len(actions) == 0


@pytest.mark.asyncio
async def test_dedup_prevents_double_queue(db):
    """Test: same URL doesn't get queued twice."""
    config = Config.for_testing()
    monitor = Monitor(config, db)

    finding = {
        "url": "https://twitter.com/status/dup",
        "text": "keyboard for SSH on phone?",
        "author": "user1",
        "platform": "twitter",
    }

    assert await monitor.queue_new_findings([finding]) == 1
    assert await monitor.queue_new_findings([finding]) == 0  # deduped
