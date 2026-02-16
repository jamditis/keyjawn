"""Tests for the monitor module."""

import pytest
import pytest_asyncio

from worker.config import Config
from worker.db import Database
from worker.monitor import Monitor


@pytest_asyncio.fixture
async def db():
    database = Database(":memory:")
    await database.init()
    yield database
    await database.close()


@pytest.fixture
def config():
    return Config.for_testing()


@pytest.fixture
def monitor(config, db):
    return Monitor(config=config, db=db)


# --- score_relevance ---


def test_score_finding_high_relevance(monitor):
    text = "anyone know a good keyboard for SSH on my phone?"
    score = monitor.score_relevance(text, "twitter")
    assert score > 0.7


def test_score_finding_low_relevance(monitor):
    text = "just got a new mechanical keyboard for my desk"
    score = monitor.score_relevance(text, "twitter")
    assert score < 0.3


def test_score_finding_medium_relevance(monitor):
    text = "using Claude Code is great but typing on mobile is painful"
    score = monitor.score_relevance(text, "twitter")
    assert score > 0.4


def test_score_question_bonus(monitor):
    text = "where can I find a mobile ssh tool"
    score_no_question = monitor.score_relevance(text, "twitter")
    score_with_question = monitor.score_relevance(text + "?", "twitter")
    assert score_with_question > score_no_question


# --- queue_new_findings ---


@pytest.mark.asyncio
async def test_dedup_findings(monitor, db):
    findings = [
        {
            "url": "https://twitter.com/user/status/111",
            "text": "keyboard for SSH on my phone is something I need",
            "author": "@user1",
            "platform": "twitter",
        },
    ]
    count_first = await monitor.queue_new_findings(findings)
    assert count_first == 1

    count_second = await monitor.queue_new_findings(findings)
    assert count_second == 0


@pytest.mark.asyncio
async def test_low_relevance_filtered(monitor, db):
    findings = [
        {
            "url": "https://twitter.com/user/status/222",
            "text": "just bought a nice desk lamp for my office",
            "author": "@user2",
            "platform": "twitter",
        },
    ]
    count = await monitor.queue_new_findings(findings)
    assert count == 0
