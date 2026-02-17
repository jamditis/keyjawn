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


# --- real-world tweet regression tests ---


def test_score_mobile_terminal_recommendations(monitor):
    """@neomewtwo: 'who has good mobile terminal recommendations?'"""
    score = monitor.score_relevance(
        "who has good mobile terminal recommendations?", "twitter"
    )
    assert score >= 0.5, f"expected >= 0.5, got {score}"


def test_score_claude_code_terminal_syncing(monitor):
    """@gabefletcher: 'Recommendations for mobile-to-local terminal syncing for Claude Code?'"""
    score = monitor.score_relevance(
        "Recommendations for mobile-to-local terminal syncing for Claude Code?",
        "twitter",
    )
    assert score >= 0.5, f"expected >= 0.5, got {score}"


def test_score_remote_terminal_mobile(monitor):
    """@byadhddev: 'built remote terminal... access from mobile'"""
    score = monitor.score_relevance(
        "built remote terminal that I can access from mobile", "twitter"
    )
    assert score >= 0.5, f"expected >= 0.5, got {score}"


def test_score_tmux_mobile_access(monitor):
    """@nummanali: 'Why should you use Tmux?... Access the same session from anywhere ie mobile'"""
    score = monitor.score_relevance(
        "Why should you use Tmux? Access the same session from anywhere ie mobile",
        "twitter",
    )
    assert score >= 0.4, f"expected >= 0.4, got {score}"


def test_score_phone_productivity_terminal_keyboard(monitor):
    """@0xEljh: 'My phone is a productivity machine... mobile terminal... fuller keyboard'"""
    score = monitor.score_relevance(
        "My phone is a productivity machine with a mobile terminal and a fuller keyboard",
        "twitter",
    )
    assert score >= 0.5, f"expected >= 0.5, got {score}"


def test_score_nativephp_android_ssh(monitor):
    """@PovilasKorop: 'Tried @nativephp Jump app on my Android phone... SSH sessions'"""
    score = monitor.score_relevance(
        "Tried NativePHP Jump app on my Android phone, and it handles SSH sessions",
        "twitter",
    )
    assert score >= 0.4, f"expected >= 0.4, got {score}"


def test_score_irrelevant_browser_agent(monitor):
    """@iannuttall: browser agent question (tangential, should stay low)"""
    score = monitor.score_relevance(
        "what's the best agent browser skill that can use my actual chrome with logged in creds?",
        "twitter",
    )
    assert score < 0.4, f"tangential tweet should score low, got {score}"


# --- queue_new_findings ---


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
