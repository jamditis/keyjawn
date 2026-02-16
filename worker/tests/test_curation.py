from worker.curation.models import CurationCandidate


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
