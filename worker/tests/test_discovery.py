import asyncio
import pytest
from unittest.mock import AsyncMock, MagicMock, patch

from worker.discovery import (
    DiscoveryEngine,
    EngagementOpportunity,
    classify_engagement,
)
from worker.db import Database


def test_engagement_opportunity_defaults():
    opp = EngagementOpportunity(
        platform="twitter",
        post_id="123",
        post_url="https://x.com/dev/status/123",
        author="somedev",
        text="cool CLI tool",
        opportunity_type="like",
    )
    assert opp.platform == "twitter"
    assert opp.opportunity_type == "like"


def test_classify_engagement_from_curated_account():
    result = classify_engagement(
        text="Just released a new terminal emulator",
        author="mitchellh",
        is_curated_account=True,
    )
    assert result in ("like", "repost", "quote_repost")


def test_classify_engagement_from_unknown_account():
    result = classify_engagement(
        text="Check out this new CLI tool I built",
        author="randomdev",
        is_curated_account=False,
    )
    assert result in ("like", "skip")


def test_classify_engagement_irrelevant():
    result = classify_engagement(
        text="Beautiful sunset photo today",
        author="photographer",
        is_curated_account=False,
    )
    assert result == "skip"


@pytest.fixture
def db():
    async def _make():
        d = Database(":memory:")
        await d.init()
        return d
    return asyncio.get_event_loop().run_until_complete(_make())


def test_db_has_engagement_table(db):
    async def _check():
        tables = await db.list_tables()
        assert "engagement_opportunities" in tables
    asyncio.get_event_loop().run_until_complete(_check())


from worker.executor import ActionPicker, EscalationTier


def test_like_is_auto_approved():
    assert ActionPicker.get_escalation_tier("like", "twitter") == EscalationTier.AUTO
    assert ActionPicker.get_escalation_tier("like", "bluesky") == EscalationTier.AUTO


def test_repost_is_auto_approved():
    assert ActionPicker.get_escalation_tier("repost", "twitter") == EscalationTier.AUTO
    assert ActionPicker.get_escalation_tier("repost", "bluesky") == EscalationTier.AUTO


def test_follow_is_auto_approved():
    assert ActionPicker.get_escalation_tier("follow", "twitter") == EscalationTier.AUTO
    assert ActionPicker.get_escalation_tier("follow", "bluesky") == EscalationTier.AUTO


def test_quote_repost_needs_approval():
    assert ActionPicker.get_escalation_tier("quote_repost", "twitter") == EscalationTier.BUTTONS


from worker.config import Config


def test_pick_actions_engagement_fallback(db):
    """When no calendar, findings, or curations exist, engagement fills the gap."""
    async def _test():
        config = Config.for_testing()
        picker = ActionPicker(config, db)

        # Insert some engagement opportunities
        await db.insert_engagement(
            platform="twitter",
            post_id="tw1",
            post_url="https://x.com/dev/status/tw1",
            author="somedev",
            text="cool terminal tool",
            opportunity_type="like",
        )
        await db.insert_engagement(
            platform="twitter",
            post_id="tw2",
            post_url="https://x.com/dev/status/tw2",
            author="otherdev",
            text="new CLI keyboard",
            opportunity_type="repost",
        )

        actions = await picker.pick_actions()
        # Should still pick up engagement even with no calendar/findings/curations
        assert len(actions) >= 1
        assert any(a["source"] == "engagement" for a in actions)
    asyncio.get_event_loop().run_until_complete(_test())
