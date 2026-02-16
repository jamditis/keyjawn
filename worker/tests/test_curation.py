from worker.curation.models import CurationCandidate
from worker.curation.keywords import score_keywords, POSITIVE_SIGNALS, NEGATIVE_SIGNALS
from worker.curation.boost import BOOST_CHANNELS, is_boosted
from worker.curation.sources.youtube import YouTubeSource, SEARCH_TERMS


def test_curation_candidate_defaults():
    c = CurationCandidate(
        source="youtube",
        url="https://youtube.com/watch?v=abc",
        title="Test video",
        description="A test",
        author="testuser",
    )
    assert c.source == "youtube"
    assert c.keyword_score == 0.0
    assert c.haiku_pass is None
    assert c.metadata == {}


def test_curation_candidate_with_scores():
    c = CurationCandidate(
        source="google_news",
        url="https://example.com/article",
        title="Open source CLI tool",
        description="A new terminal tool",
        author="blogger",
        keyword_score=0.7,
        haiku_pass=True,
        gemini_score=8.5,
    )
    assert c.keyword_score == 0.7
    assert c.haiku_pass is True
    assert c.gemini_score == 8.5


import pytest
import asyncio
from worker.db import Database


@pytest.fixture
def db():
    """Create an in-memory database for testing."""
    async def _make():
        d = Database(":memory:")
        await d.init()
        return d
    return asyncio.get_event_loop().run_until_complete(_make())


def test_db_has_curation_table(db):
    async def _check():
        tables = await db.list_tables()
        assert "curation_candidates" in tables
    asyncio.get_event_loop().run_until_complete(_check())


def test_insert_curation_candidate(db):
    async def _insert():
        cid = await db.insert_curation_candidate(
            source="youtube",
            url="https://youtube.com/watch?v=test123",
            title="Cool CLI tool",
            author="devuser",
            description="A terminal tool for Android",
        )
        assert cid is not None
        row = await db.get_curation_candidate(cid)
        assert row["source"] == "youtube"
        assert row["title"] == "Cool CLI tool"
        assert row["status"] == "new"
    asyncio.get_event_loop().run_until_complete(_insert())


def test_update_curation_evaluation(db):
    async def _test():
        cid = await db.insert_curation_candidate(
            source="google_news",
            url="https://example.com/article",
            title="Test article",
            author="blogger",
            description="Desc",
        )
        await db.update_curation_evaluation(
            cid,
            keyword_score=0.7,
            haiku_pass=True,
            haiku_reasoning="Relevant dev tools content",
            gemini_score=8.0,
            gemini_analysis="Open source, indie dev",
            sonnet_pass=True,
            sonnet_draft="Check out this cool tool...",
            sonnet_reasoning="High quality, good fit",
            final_score=0.85,
            status="approved",
        )
        row = await db.get_curation_candidate(cid)
        assert row["keyword_score"] == 0.7
        assert row["haiku_pass"] == 1
        assert row["final_score"] == 0.85
        assert row["status"] == "approved"
    asyncio.get_event_loop().run_until_complete(_test())


def test_get_approved_curations(db):
    async def _test():
        c1 = await db.insert_curation_candidate(
            source="youtube", url="https://a.com", title="Good",
            author="a", description="d",
        )
        await db.update_curation_evaluation(
            c1, keyword_score=0.8, final_score=0.9, status="approved",
            sonnet_draft="Draft post text",
        )
        c2 = await db.insert_curation_candidate(
            source="youtube", url="https://b.com", title="Bad",
            author="b", description="d",
        )
        await db.update_curation_evaluation(
            c2, keyword_score=0.2, final_score=0.1, status="rejected",
        )
        approved = await db.get_approved_curations(limit=10)
        assert len(approved) == 1
        assert approved[0]["title"] == "Good"
    asyncio.get_event_loop().run_until_complete(_test())


def test_curation_dedup(db):
    async def _test():
        await db.insert_curation_candidate(
            source="youtube", url="https://same.com", title="First",
            author="a", description="d",
        )
        result = await db.insert_curation_candidate(
            source="youtube", url="https://same.com", title="Dupe",
            author="a", description="d",
        )
        assert result is None
    asyncio.get_event_loop().run_until_complete(_test())


def test_keyword_score_high_relevance():
    c = CurationCandidate(
        source="youtube", url="http://a.com", author="dev",
        title="Open source terminal keyboard for Android",
        description="A CLI keyboard for mobile developers",
    )
    score = score_keywords(c)
    assert score >= 0.7


def test_keyword_score_medium_relevance():
    c = CurationCandidate(
        source="youtube", url="http://b.com", author="dev",
        title="New mobile development tool",
        description="A tool for coding on the go",
    )
    score = score_keywords(c)
    assert 0.3 <= score <= 0.7


def test_keyword_score_irrelevant():
    c = CurationCandidate(
        source="youtube", url="http://c.com", author="dev",
        title="Best photo filters for Instagram",
        description="Beautiful photo editing app",
    )
    score = score_keywords(c)
    assert score < 0.3


def test_keyword_score_open_source_boost():
    base = CurationCandidate(
        source="youtube", url="http://d.com", author="dev",
        title="New terminal emulator",
        description="A terminal app",
    )
    oss = CurationCandidate(
        source="youtube", url="http://e.com", author="dev",
        title="New open source terminal emulator",
        description="A terminal app, code on GitHub",
    )
    assert score_keywords(oss) > score_keywords(base)


def test_keyword_score_negative_signals():
    c = CurationCandidate(
        source="youtube", url="http://f.com", author="dev",
        title="LIMITED TIME: Terminal keyboard discount",
        description="Sponsored content about a CLI tool",
    )
    score = score_keywords(c)
    assert score < 0.3


def test_boost_list_known_channel():
    assert is_boosted("youtube", "Fireship")
    assert is_boosted("youtube", "ThePrimeagen")


def test_boost_list_unknown_channel():
    assert not is_boosted("youtube", "RandomChannel123")


def test_boost_list_case_insensitive():
    assert is_boosted("youtube", "fireship")


def test_youtube_search_terms_defined():
    assert len(SEARCH_TERMS) > 0
    assert any("terminal" in t or "CLI" in t or "keyboard" in t for t in SEARCH_TERMS)


def test_youtube_source_init():
    source = YouTubeSource("test-api-key")
    assert source.api_key == "test-api-key"


def test_youtube_parse_results():
    source = YouTubeSource("test-key")
    raw_items = [
        {
            "id": {"videoId": "abc123"},
            "snippet": {
                "title": "Cool CLI tool",
                "description": "A terminal emulator for Android",
                "channelTitle": "DevChannel",
                "publishedAt": "2026-02-16T12:00:00Z",
            },
        },
    ]
    candidates = source._parse_search_results(raw_items)
    assert len(candidates) == 1
    assert candidates[0].source == "youtube"
    assert candidates[0].title == "Cool CLI tool"
    assert candidates[0].author == "DevChannel"
    assert "abc123" in candidates[0].url
