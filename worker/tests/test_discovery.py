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
