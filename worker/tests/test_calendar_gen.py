"""Tests for the weekly content calendar generator."""

from datetime import date

import pytest
import pytest_asyncio

from worker.calendar_gen import PILLARS, PLATFORMS, TOPICS, generate_weekly_calendar
from worker.config import Config
from worker.db import Database


@pytest_asyncio.fixture
async def db():
    database = Database(":memory:")
    await database.init()
    yield database
    await database.close()


@pytest.fixture
def config():
    return Config.for_testing()


# --- constants ---


def test_pillars_defined():
    assert len(PILLARS) == 5
    assert "demo" in PILLARS
    assert "awareness" in PILLARS


# --- generate_weekly_calendar ---


@pytest.mark.asyncio
async def test_generate_calendar_creates_entries(db, config):
    monday = date(2026, 2, 17)
    count = await generate_weekly_calendar(config, db, monday)

    assert count >= 5

    # Monday should have at least 1 entry
    entries = await db.get_calendar_entries("2026-02-17")
    assert len(entries) >= 1


@pytest.mark.asyncio
async def test_generate_calendar_skips_weekends(db, config):
    monday = date(2026, 2, 17)
    await generate_weekly_calendar(config, db, monday)

    # Saturday 2026-02-21 and Sunday 2026-02-22 should have 0 entries
    saturday_entries = await db.get_calendar_entries("2026-02-21")
    assert len(saturday_entries) == 0

    sunday_entries = await db.get_calendar_entries("2026-02-22")
    assert len(sunday_entries) == 0


@pytest.mark.asyncio
async def test_calendar_distributes_across_platforms(db, config):
    monday = date(2026, 2, 17)
    await generate_weekly_calendar(config, db, monday)

    all_platforms = set()
    for day_offset in range(5):  # Mon-Fri
        d = date(2026, 2, 17 + day_offset)
        entries = await db.get_calendar_entries(d.isoformat())
        for entry in entries:
            all_platforms.add(entry["platform"])

    assert "twitter" in all_platforms
    assert "bluesky" in all_platforms
