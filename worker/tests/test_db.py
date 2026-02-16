import pytest
import pytest_asyncio
from datetime import datetime, timezone

from worker.db import Database


@pytest_asyncio.fixture
async def db():
    database = Database(":memory:")
    await database.init()
    yield database
    await database.close()


@pytest.mark.asyncio
async def test_init_creates_tables(db):
    tables = await db.list_tables()
    assert sorted(tables) == ["actions", "calendar", "findings", "metrics"]


@pytest.mark.asyncio
async def test_queue_finding(db):
    finding_id = await db.queue_finding(
        platform="twitter",
        source_url="https://twitter.com/user/status/123",
        source_user="@testuser",
        content="Interesting thread about keyboards",
        relevance_score=0.85,
    )
    assert isinstance(finding_id, str)
    assert len(finding_id) == 12

    finding = await db.get_finding(finding_id)
    assert finding is not None
    assert finding["platform"] == "twitter"
    assert finding["source_url"] == "https://twitter.com/user/status/123"
    assert finding["source_user"] == "@testuser"
    assert finding["content"] == "Interesting thread about keyboards"
    assert finding["relevance_score"] == 0.85
    assert finding["status"] == "queued"
    assert finding["found_at"] is not None


@pytest.mark.asyncio
async def test_log_action(db):
    action_id = await db.log_action(
        action_type="reply",
        platform="bluesky",
        content="Great point about mechanical keyboards!",
        status="posted",
        finding_id=None,
        post_url="https://bsky.app/profile/keyjawn/post/abc",
    )
    assert isinstance(action_id, str)
    assert len(action_id) == 12

    action = await db.get_action(action_id)
    assert action is not None
    assert action["action_type"] == "reply"
    assert action["platform"] == "bluesky"
    assert action["content"] == "Great point about mechanical keyboards!"
    assert action["status"] == "posted"
    assert action["finding_id"] is None
    assert action["post_url"] == "https://bsky.app/profile/keyjawn/post/abc"
    assert action["acted_at"] is not None


@pytest.mark.asyncio
async def test_calendar_entry(db):
    entry_id = await db.add_calendar_entry(
        scheduled_date="2026-02-20",
        pillar="community",
        platform="twitter",
        content_draft="Thread about custom keyboard layouts",
    )
    assert isinstance(entry_id, str)
    assert len(entry_id) == 12

    entries = await db.get_calendar_entries("2026-02-20")
    assert len(entries) == 1
    assert entries[0]["pillar"] == "community"
    assert entries[0]["platform"] == "twitter"
    assert entries[0]["content_draft"] == "Thread about custom keyboard layouts"
    assert entries[0]["status"] == "planned"

    # Different date returns nothing
    empty = await db.get_calendar_entries("2026-02-21")
    assert len(empty) == 0


@pytest.mark.asyncio
async def test_daily_action_count(db):
    await db.log_action("reply", "twitter", "Reply 1", "posted")
    await db.log_action("quote", "twitter", "Quote 1", "posted")

    count = await db.get_daily_action_count()
    assert count == 2

    # Filter by platform
    await db.log_action("reply", "bluesky", "Reply 2", "posted")
    twitter_count = await db.get_daily_action_count(platform="twitter")
    assert twitter_count == 2

    total_count = await db.get_daily_action_count()
    assert total_count == 3


@pytest.mark.asyncio
async def test_finding_status_transitions(db):
    finding_id = await db.queue_finding(
        platform="twitter",
        source_url="https://twitter.com/user/status/456",
        source_user="@someone",
        content="Discussion about android keyboards",
        relevance_score=0.9,
    )

    finding = await db.get_finding(finding_id)
    assert finding["status"] == "queued"

    await db.update_finding_status(finding_id, "approved")
    finding = await db.get_finding(finding_id)
    assert finding["status"] == "approved"

    await db.update_finding_status(finding_id, "acted")
    finding = await db.get_finding(finding_id)
    assert finding["status"] == "acted"
