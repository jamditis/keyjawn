# Content curation pipeline implementation plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a content curation pipeline that discovers, evaluates, and shares interesting dev tools content from YouTube, Google News/RSS, and Twitch -- biased toward open source and indie projects.

**Architecture:** Three source monitors feed CurationCandidate objects into a four-stage AI evaluation pipeline (keyword filter, Haiku, Gemini CLI, Sonnet). Approved candidates post to Twitter/Bluesky via the existing action system with Telegram approval. Separate daily budget from self-promo (2 curated shares/day).

**Tech stack:** httpx (HTTP, already in deps), feedparser (RSS parsing, new dep), anthropic (Haiku/Sonnet, new dep), Gemini CLI (existing on officejawn), aiosqlite (existing)

**Design doc:** docs/plans/2026-02-16-content-curation-design.md

**Test runner:** ssh officejawn "cd /home/jamditis/projects/keyjawn/worker && venv/bin/python -m pytest tests/ -v"

**Existing patterns to follow:**
- HTTP clients use httpx.AsyncClient (see worker/platforms/producthunt.py)
- DB uses aiosqlite with Row factory (see worker/db.py)
- Platform config uses frozen dataclasses (see worker/config.py)
- Telegram messages use HTML parse mode, _escape_html() (see worker/telegram.py)
- All tests run offline -- mock external APIs, no real network calls

---

### Task 1: Add dependencies and curation package skeleton

**Files:**
- Modify: pyproject.toml
- Create: worker/curation/__init__.py
- Create: worker/curation/models.py
- Create: worker/curation/sources/__init__.py

**Step 1: Add new dependencies to pyproject.toml**

Add feedparser and anthropic to the dependencies list in pyproject.toml:

```toml
dependencies = [
    "aiosqlite>=0.20.0",
    "httpx>=0.27.0",
    "twikit>=2.0.0",
    "atproto>=0.0.55",
    "apscheduler>=3.10.0",
    "redis>=5.0.0",
    "feedparser>=6.0.0",
    "anthropic>=0.40.0",
]
```

**Step 2: Create the curation package with models**

Create worker/curation/__init__.py:
```python
"""Content curation pipeline for discovering and sharing dev tools content."""
```

Create worker/curation/sources/__init__.py:
```python
"""Source monitors for content discovery."""
```

Create worker/curation/models.py:
```python
"""Data models for the curation pipeline."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from typing import Optional


@dataclass
class CurationCandidate:
    """A piece of content discovered by a source monitor."""
    source: str          # "youtube", "google_news", "twitch"
    url: str
    title: str
    description: str
    author: str
    published: Optional[datetime] = None
    metadata: dict = field(default_factory=dict)

    # Populated by evaluation pipeline stages
    keyword_score: float = 0.0
    haiku_pass: Optional[bool] = None
    haiku_reasoning: str = ""
    gemini_score: float = 0.0
    gemini_analysis: str = ""
    sonnet_pass: Optional[bool] = None
    sonnet_draft: str = ""
    sonnet_reasoning: str = ""
    final_score: float = 0.0
```

**Step 3: Install new deps on officejawn**

Run: ssh officejawn "cd /home/jamditis/projects/keyjawn/worker && venv/bin/pip install feedparser anthropic"

**Step 4: Write test for CurationCandidate model**

Create worker/tests/test_curation.py:
```python
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
```

**Step 5: Run tests to verify**

Run: ssh officejawn "cd /home/jamditis/projects/keyjawn/worker && venv/bin/python -m pytest tests/test_curation.py -v"
Expected: 2 PASSED

**Step 6: Commit**

```bash
git add pyproject.toml worker/curation/ tests/test_curation.py
git commit -m "feat(curation): add package skeleton and CurationCandidate model"
```

---

### Task 2: Database schema for curation candidates

**Files:**
- Modify: worker/db.py
- Test: tests/test_curation.py

**Step 1: Write failing tests for curation DB methods**

Add to tests/test_curation.py:
```python
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
```

**Step 2: Run tests to verify they fail**

Run: ssh officejawn "cd /home/jamditis/projects/keyjawn/worker && venv/bin/python -m pytest tests/test_curation.py -v -k 'db'"
Expected: FAIL (methods don't exist yet)

**Step 3: Add curation schema and DB methods**

Add to the SCHEMA string in worker/db.py, after the existing CREATE INDEX statements:

```sql
CREATE TABLE IF NOT EXISTS curation_candidates (
    id TEXT PRIMARY KEY,
    source TEXT NOT NULL,
    url TEXT NOT NULL UNIQUE,
    title TEXT NOT NULL,
    author TEXT,
    description TEXT,
    published_at TEXT,
    metadata TEXT,
    keyword_score REAL DEFAULT 0,
    haiku_pass INTEGER,
    haiku_reasoning TEXT,
    gemini_score REAL DEFAULT 0,
    gemini_analysis TEXT,
    sonnet_pass INTEGER,
    sonnet_draft TEXT,
    sonnet_reasoning TEXT,
    final_score REAL DEFAULT 0,
    status TEXT DEFAULT 'new',
    created_at TEXT NOT NULL,
    evaluated_at TEXT,
    posted_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_curation_status ON curation_candidates(status);
CREATE INDEX IF NOT EXISTS idx_curation_url ON curation_candidates(url);
```

Add these methods to the Database class in worker/db.py:

```python
    # -- curation --

    async def insert_curation_candidate(
        self,
        source: str,
        url: str,
        title: str,
        author: str,
        description: str,
        published_at: str = None,
        metadata: str = None,
    ) -> Optional[str]:
        """Insert a curation candidate. Returns ID or None if URL already exists."""
        cursor = await self._db.execute(
            "SELECT 1 FROM curation_candidates WHERE url = ?", (url,)
        )
        if await cursor.fetchone():
            return None

        cid = _new_id()
        await self._db.execute(
            """INSERT INTO curation_candidates
               (id, source, url, title, author, description, published_at, metadata, status, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'new', ?)""",
            (cid, source, url, title, author, description, published_at, metadata, _now()),
        )
        await self._db.commit()
        return cid

    async def get_curation_candidate(self, cid: str) -> Optional[dict]:
        cursor = await self._db.execute(
            "SELECT * FROM curation_candidates WHERE id = ?", (cid,)
        )
        row = await cursor.fetchone()
        return dict(row) if row else None

    async def update_curation_evaluation(self, cid: str, **kwargs):
        """Update evaluation fields on a curation candidate."""
        kwargs["evaluated_at"] = _now()
        sets = ", ".join(f"{k} = ?" for k in kwargs)
        vals = list(kwargs.values()) + [cid]
        await self._db.execute(
            f"UPDATE curation_candidates SET {sets} WHERE id = ?", vals
        )
        await self._db.commit()

    async def get_new_curations(self, limit: int = 50) -> list[dict]:
        """Get unevaluated curation candidates."""
        cursor = await self._db.execute(
            "SELECT * FROM curation_candidates WHERE status = 'new' ORDER BY created_at ASC LIMIT ?",
            (limit,),
        )
        return [dict(row) for row in await cursor.fetchall()]

    async def get_approved_curations(self, limit: int = 5) -> list[dict]:
        """Get approved curations ready to post."""
        cursor = await self._db.execute(
            "SELECT * FROM curation_candidates WHERE status = 'approved' ORDER BY final_score DESC LIMIT ?",
            (limit,),
        )
        return [dict(row) for row in await cursor.fetchall()]

    async def get_daily_curation_count(self) -> int:
        """Count curated shares posted today."""
        from datetime import datetime, timezone
        today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
        cursor = await self._db.execute(
            "SELECT COUNT(*) FROM curation_candidates WHERE status = 'posted' AND date(posted_at) = ?",
            (today,),
        )
        row = await cursor.fetchone()
        return row[0]
```

**Step 4: Run tests**

Run: ssh officejawn "cd /home/jamditis/projects/keyjawn/worker && venv/bin/python -m pytest tests/test_curation.py -v"
Expected: All PASSED

**Step 5: Commit**

```bash
git add worker/db.py tests/test_curation.py
git commit -m "feat(curation): add curation_candidates table and DB methods"
```

---

### Task 3: Keyword filter and boost list

**Files:**
- Create: worker/curation/keywords.py
- Create: worker/curation/boost.py
- Test: tests/test_curation.py

**Step 1: Write failing tests**

Add to tests/test_curation.py:
```python
from worker.curation.keywords import score_keywords, POSITIVE_SIGNALS, NEGATIVE_SIGNALS
from worker.curation.boost import BOOST_CHANNELS, is_boosted
from worker.curation.models import CurationCandidate


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
```

**Step 2: Run tests to verify they fail**

Run: ssh officejawn "cd /home/jamditis/projects/keyjawn/worker && venv/bin/python -m pytest tests/test_curation.py -v -k 'keyword or boost'"
Expected: FAIL (modules don't exist)

**Step 3: Implement keyword scoring**

Create worker/curation/keywords.py:
```python
"""Keyword-based relevance scoring for curation candidates (pipeline stage 1)."""

from __future__ import annotations

from worker.curation.models import CurationCandidate

POSITIVE_SIGNALS = [
    "terminal",
    "cli",
    "command line",
    "ssh",
    "keyboard",
    "android",
    "mobile dev",
    "mobile coding",
    "coding on phone",
    "developer tool",
    "dev tool",
    "shell",
    "tmux",
    "vim",
    "neovim",
    "terminal emulator",
    "claude code",
    "gemini cli",
    "llm agent",
    "ai agent",
    "copilot",
    "code editor",
    "ide",
    "rust cli",
    "go cli",
    "python cli",
]

INDIE_SIGNALS = [
    "open source",
    "open-source",
    "oss",
    "free",
    "indie",
    "side project",
    "solo dev",
    "solo developer",
    "no vc",
    "bootstrapped",
    "github.com",
    "gitlab.com",
    "codeberg.org",
    "sourcehut",
]

NEGATIVE_SIGNALS = [
    "limited time",
    "discount",
    "coupon",
    "sponsored",
    "promotion",
    "affiliate",
    "crypto",
    "web3",
    "nft",
    "blockchain",
    "get rich",
    "make money",
    "subscribe for more",
]


def score_keywords(candidate: CurationCandidate) -> float:
    """Score a candidate 0.0-1.0 based on keyword relevance.

    Stage 1 of the evaluation pipeline. Runs locally, no API calls.
    """
    text = f"{candidate.title} {candidate.description}".lower()

    neg_hits = sum(1 for s in NEGATIVE_SIGNALS if s in text)
    if neg_hits >= 2:
        return 0.0
    neg_penalty = neg_hits * 0.3

    pos_hits = sum(1 for s in POSITIVE_SIGNALS if s in text)

    if pos_hits >= 3:
        score = 0.8
    elif pos_hits >= 2:
        score = 0.6
    elif pos_hits >= 1:
        score = 0.4
    else:
        score = 0.1

    indie_hits = sum(1 for s in INDIE_SIGNALS if s in text)
    if indie_hits >= 2:
        score = min(score + 0.25, 1.0)
    elif indie_hits >= 1:
        score = min(score + 0.15, 1.0)

    score = max(score - neg_penalty, 0.0)
    return round(score, 2)
```

**Step 4: Implement boost list**

Create worker/curation/boost.py:
```python
"""Boost list for trusted content sources."""

from __future__ import annotations

BOOST_CHANNELS: dict[str, list[str]] = {
    "youtube": [
        "ThePrimeagen",
        "Fireship",
        "NetworkChuck",
        "TechHut",
        "Dreams of Code",
        "typecraft",
        "Chris Titus Tech",
        "Mental Outlaw",
        "Luke Smith",
        "DistroTube",
    ],
    "twitch": [
        "ThePrimeagen",
        "teaboraxofficial",
    ],
    "news_domains": [
        "github.com",
        "lobste.rs",
        "news.ycombinator.com",
        "dev.to",
    ],
}

_BOOST_LOOKUP: dict[str, set[str]] = {
    source: {name.lower() for name in names}
    for source, names in BOOST_CHANNELS.items()
}

BOOST_SCORE = 0.15


def is_boosted(source: str, author_or_domain: str) -> bool:
    """Check if a source/author is on the boost list."""
    lookup = _BOOST_LOOKUP.get(source, set())
    return author_or_domain.lower() in lookup
```

**Step 5: Run tests**

Run: ssh officejawn "cd /home/jamditis/projects/keyjawn/worker && venv/bin/python -m pytest tests/test_curation.py -v"
Expected: All PASSED

**Step 6: Commit**

```bash
git add worker/curation/keywords.py worker/curation/boost.py tests/test_curation.py
git commit -m "feat(curation): add keyword scoring and boost list (pipeline stage 1)"
```

---

### Task 4: YouTube source monitor

**Files:**
- Create: worker/curation/sources/youtube.py
- Test: tests/test_curation.py

**Step 1: Write failing tests**

Add to tests/test_curation.py:
```python
from worker.curation.sources.youtube import YouTubeSource, SEARCH_TERMS


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
```

**Step 2: Implement YouTube source**

Create worker/curation/sources/youtube.py:
```python
"""YouTube Data API v3 source monitor."""

from __future__ import annotations

import logging
from datetime import datetime, timedelta, timezone
from typing import Optional

import httpx

from worker.curation.models import CurationCandidate

log = logging.getLogger(__name__)

API_BASE = "https://www.googleapis.com/youtube/v3"

SEARCH_TERMS = [
    "terminal keyboard android",
    "mobile CLI tools",
    "SSH mobile app",
    "developer keyboard android",
    "indie dev tools",
    "open source android tools",
    "coding on phone",
    "terminal emulator mobile",
    "command line mobile",
    "neovim mobile",
    "tmux phone",
]


class YouTubeSource:
    def __init__(self, api_key: str):
        self.api_key = api_key

    def _parse_search_results(self, items: list[dict]) -> list[CurationCandidate]:
        """Parse YouTube API search results into CurationCandidates."""
        candidates = []
        for item in items:
            video_id = item.get("id", {}).get("videoId")
            if not video_id:
                continue

            snippet = item.get("snippet", {})
            published = None
            if snippet.get("publishedAt"):
                try:
                    published = datetime.fromisoformat(
                        snippet["publishedAt"].replace("Z", "+00:00")
                    )
                except (ValueError, TypeError):
                    pass

            candidates.append(CurationCandidate(
                source="youtube",
                url=f"https://www.youtube.com/watch?v={video_id}",
                title=snippet.get("title", ""),
                description=snippet.get("description", ""),
                author=snippet.get("channelTitle", ""),
                published=published,
                metadata={
                    "video_id": video_id,
                    "channel": snippet.get("channelTitle", ""),
                    "thumbnail": snippet.get("thumbnails", {}).get("high", {}).get("url", ""),
                },
            ))
        return candidates

    async def search(self, query: str, max_results: int = 10) -> list[CurationCandidate]:
        """Search YouTube for videos matching a query."""
        cutoff = (datetime.now(timezone.utc) - timedelta(days=7)).strftime("%Y-%m-%dT%H:%M:%SZ")
        params = {
            "part": "snippet",
            "q": query,
            "type": "video",
            "maxResults": max_results,
            "order": "relevance",
            "key": self.api_key,
            "publishedAfter": cutoff,
        }

        try:
            async with httpx.AsyncClient() as client:
                resp = await client.get(f"{API_BASE}/search", params=params, timeout=15)
                if resp.status_code != 200:
                    log.error("YouTube API error (%d): %s", resp.status_code, resp.text[:200])
                    return []
                data = resp.json()
                return self._parse_search_results(data.get("items", []))
        except Exception:
            log.exception("YouTube search failed")
            return []

    async def scan(self) -> list[CurationCandidate]:
        """Run all search terms and return deduplicated candidates."""
        seen_urls = set()
        all_candidates = []

        for term in SEARCH_TERMS:
            results = await self.search(term, max_results=5)
            for c in results:
                if c.url not in seen_urls:
                    seen_urls.add(c.url)
                    all_candidates.append(c)

        log.info("YouTube scan: %d candidates from %d search terms", len(all_candidates), len(SEARCH_TERMS))
        return all_candidates
```

**Step 3: Run tests**

Run: ssh officejawn "cd /home/jamditis/projects/keyjawn/worker && venv/bin/python -m pytest tests/test_curation.py -v -k 'youtube'"
Expected: All PASSED

**Step 4: Commit**

```bash
git add worker/curation/sources/youtube.py tests/test_curation.py
git commit -m "feat(curation): add YouTube Data API v3 source monitor"
```

---

### Task 5: Google News / RSS source monitor

**Files:**
- Create: worker/curation/sources/news.py
- Test: tests/test_curation.py

**Step 1: Write failing tests**

Add to tests/test_curation.py:
```python
from unittest.mock import MagicMock
from worker.curation.sources.news import NewsSource, RSS_FEEDS, GOOGLE_ALERT_TOPICS


def test_news_rss_feeds_defined():
    assert len(RSS_FEEDS) > 0


def test_news_google_alert_topics_defined():
    assert len(GOOGLE_ALERT_TOPICS) > 0


def test_news_parse_feed_entry():
    source = NewsSource()
    entry = MagicMock()
    entry.title = "New open source CLI tool released"
    entry.link = "https://example.com/article"
    entry.summary = "A new terminal tool for developers"
    entry.published_parsed = None
    entry.author = "TechBlog"

    candidate = source._parse_entry(entry, "test_feed")
    assert candidate.source == "google_news"
    assert candidate.title == "New open source CLI tool released"
    assert candidate.url == "https://example.com/article"
```

**Step 2: Implement News/RSS source**

Create worker/curation/sources/news.py:
```python
"""Google News RSS and tech blog feed monitor."""

from __future__ import annotations

import logging
import re
from calendar import timegm
from datetime import datetime, timezone
from typing import Optional

import feedparser

from worker.curation.models import CurationCandidate

log = logging.getLogger(__name__)

GOOGLE_ALERT_TOPICS = [
    "mobile developer tools",
    "terminal keyboard",
    "open source Android",
    "indie developer tools",
    "CLI tools",
    "SSH mobile",
]

RSS_FEEDS = {
    "hn_best": "https://hnrss.org/best?q=terminal+OR+cli+OR+keyboard+OR+ssh+OR+android",
    "lobsters": "https://lobste.rs/t/cli,android,devops.rss",
    "devto_cli": "https://dev.to/feed/tag/cli",
    "devto_terminal": "https://dev.to/feed/tag/terminal",
    "devto_android": "https://dev.to/feed/tag/android",
}


class NewsSource:
    def __init__(self, google_alert_urls: list[str] = None):
        self.google_alert_urls = google_alert_urls or []

    def _parse_entry(self, entry, feed_name: str) -> Optional[CurationCandidate]:
        """Parse a single feedparser entry into a CurationCandidate."""
        title = getattr(entry, "title", "")
        link = getattr(entry, "link", "")
        if not title or not link:
            return None

        description = getattr(entry, "summary", "") or getattr(entry, "description", "")
        description = re.sub(r"<[^>]+>", "", description)[:500]

        published = None
        if hasattr(entry, "published_parsed") and entry.published_parsed:
            try:
                published = datetime.fromtimestamp(
                    timegm(entry.published_parsed), tz=timezone.utc
                )
            except (TypeError, ValueError, OverflowError):
                pass

        author = getattr(entry, "author", "")

        return CurationCandidate(
            source="google_news",
            url=link,
            title=title,
            description=description,
            author=author,
            published=published,
            metadata={"feed": feed_name},
        )

    async def scan_feed(self, url: str, feed_name: str) -> list[CurationCandidate]:
        """Parse a single RSS feed and return candidates."""
        try:
            feed = feedparser.parse(url)
            candidates = []
            for entry in feed.entries[:20]:
                c = self._parse_entry(entry, feed_name)
                if c:
                    candidates.append(c)
            return candidates
        except Exception:
            log.exception("Failed to parse feed %s", feed_name)
            return []

    async def scan(self) -> list[CurationCandidate]:
        """Scan all configured feeds and return deduplicated candidates."""
        seen_urls = set()
        all_candidates = []

        for name, url in RSS_FEEDS.items():
            results = await self.scan_feed(url, name)
            for c in results:
                if c.url not in seen_urls:
                    seen_urls.add(c.url)
                    all_candidates.append(c)

        for i, url in enumerate(self.google_alert_urls):
            results = await self.scan_feed(url, f"google_alert_{i}")
            for c in results:
                if c.url not in seen_urls:
                    seen_urls.add(c.url)
                    all_candidates.append(c)

        log.info("News scan: %d candidates from %d feeds",
                 len(all_candidates), len(RSS_FEEDS) + len(self.google_alert_urls))
        return all_candidates
```

**Step 3: Run tests**

Run: ssh officejawn "cd /home/jamditis/projects/keyjawn/worker && venv/bin/python -m pytest tests/test_curation.py -v -k 'news'"
Expected: All PASSED

**Step 4: Commit**

```bash
git add worker/curation/sources/news.py tests/test_curation.py
git commit -m "feat(curation): add Google News and RSS source monitor"
```

---

### Task 6: Twitch source monitor

**Files:**
- Create: worker/curation/sources/twitch.py
- Test: tests/test_curation.py

**Step 1: Write failing tests**

Add to tests/test_curation.py:
```python
from worker.curation.sources.twitch import TwitchSource, CATEGORIES


def test_twitch_categories_defined():
    assert len(CATEGORIES) > 0
    assert "Science & Technology" in CATEGORIES


def test_twitch_source_init():
    source = TwitchSource("client-id", "client-secret")
    assert source.client_id == "client-id"


def test_twitch_parse_clip():
    source = TwitchSource("id", "secret")
    raw = {
        "id": "clip123",
        "title": "Building a terminal tool live",
        "url": "https://clips.twitch.tv/clip123",
        "broadcaster_name": "devstreamer",
        "view_count": 500,
        "created_at": "2026-02-16T10:00:00Z",
        "game_id": "509670",
    }
    candidate = source._parse_clip(raw)
    assert candidate.source == "twitch"
    assert candidate.title == "Building a terminal tool live"
    assert candidate.author == "devstreamer"
    assert candidate.metadata["view_count"] == 500
```

**Step 2: Implement Twitch source**

Create worker/curation/sources/twitch.py:
```python
"""Twitch Helix API source monitor for dev-related clips."""

from __future__ import annotations

import logging
from datetime import datetime, timedelta, timezone
from typing import Optional

import httpx

from worker.curation.models import CurationCandidate

log = logging.getLogger(__name__)

API_BASE = "https://api.twitch.tv/helix"
TOKEN_URL = "https://id.twitch.tv/oauth2/token"

CATEGORIES = {
    "Science & Technology": "509670",
    "Software and Game Development": "1469308723",
}


class TwitchSource:
    def __init__(self, client_id: str, client_secret: str):
        self.client_id = client_id
        self.client_secret = client_secret
        self._access_token: Optional[str] = None

    async def _ensure_token(self) -> str:
        """Get or refresh the OAuth app access token."""
        if self._access_token:
            return self._access_token

        try:
            async with httpx.AsyncClient() as client:
                resp = await client.post(TOKEN_URL, params={
                    "client_id": self.client_id,
                    "client_secret": self.client_secret,
                    "grant_type": "client_credentials",
                })
                if resp.status_code != 200:
                    log.error("Twitch token error (%d): %s", resp.status_code, resp.text[:200])
                    return ""
                self._access_token = resp.json()["access_token"]
                return self._access_token
        except Exception:
            log.exception("Twitch token request failed")
            return ""

    def _headers(self, token: str) -> dict:
        return {
            "Client-ID": self.client_id,
            "Authorization": f"Bearer {token}",
        }

    def _parse_clip(self, clip: dict) -> CurationCandidate:
        """Parse a Twitch clip into a CurationCandidate."""
        published = None
        if clip.get("created_at"):
            try:
                published = datetime.fromisoformat(
                    clip["created_at"].replace("Z", "+00:00")
                )
            except (ValueError, TypeError):
                pass

        return CurationCandidate(
            source="twitch",
            url=clip.get("url", ""),
            title=clip.get("title", ""),
            description=clip.get("title", ""),
            author=clip.get("broadcaster_name", ""),
            published=published,
            metadata={
                "clip_id": clip.get("id", ""),
                "view_count": clip.get("view_count", 0),
                "game_id": clip.get("game_id", ""),
                "broadcaster": clip.get("broadcaster_name", ""),
            },
        )

    async def get_clips(self, game_id: str, max_results: int = 20) -> list[CurationCandidate]:
        """Get recent clips for a game/category."""
        token = await self._ensure_token()
        if not token:
            return []

        started_at = (datetime.now(timezone.utc) - timedelta(days=7)).strftime("%Y-%m-%dT%H:%M:%SZ")

        try:
            async with httpx.AsyncClient() as client:
                resp = await client.get(
                    f"{API_BASE}/clips",
                    params={
                        "game_id": game_id,
                        "first": max_results,
                        "started_at": started_at,
                    },
                    headers=self._headers(token),
                    timeout=15,
                )
                if resp.status_code != 200:
                    log.error("Twitch clips error (%d): %s", resp.status_code, resp.text[:200])
                    return []
                data = resp.json()
                return [self._parse_clip(c) for c in data.get("data", [])]
        except Exception:
            log.exception("Twitch clips request failed")
            return []

    async def scan(self) -> list[CurationCandidate]:
        """Scan all monitored categories for clips."""
        seen_urls = set()
        all_candidates = []

        for name, game_id in CATEGORIES.items():
            clips = await self.get_clips(game_id, max_results=10)
            for c in clips:
                if c.url and c.url not in seen_urls:
                    seen_urls.add(c.url)
                    all_candidates.append(c)

        log.info("Twitch scan: %d clips from %d categories", len(all_candidates), len(CATEGORIES))
        return all_candidates
```

**Step 3: Run tests**

Run: ssh officejawn "cd /home/jamditis/projects/keyjawn/worker && venv/bin/python -m pytest tests/test_curation.py -v -k 'twitch'"
Expected: All PASSED

**Step 4: Commit**

```bash
git add worker/curation/sources/twitch.py tests/test_curation.py
git commit -m "feat(curation): add Twitch Helix API source monitor"
```

---

### Task 7: AI evaluation stages (Haiku, Gemini, Sonnet)

**Files:**
- Create: worker/curation/evaluate.py
- Test: tests/test_curation.py

**Step 1: Write failing tests**

Add to tests/test_curation.py:
```python
from worker.curation.evaluate import (
    build_haiku_prompt, parse_haiku_response,
    build_gemini_prompt, parse_gemini_response,
    build_sonnet_prompt, parse_sonnet_response,
)


def test_build_haiku_prompt():
    c = CurationCandidate(
        source="youtube", url="http://test.com",
        title="Open source terminal tool",
        description="A CLI keyboard for Android developers",
        author="devuser",
    )
    prompt = build_haiku_prompt(c)
    assert "Open source terminal tool" in prompt
    assert "CLI keyboard" in prompt
    assert "YES or NO" in prompt


def test_parse_haiku_response_yes():
    response = "YES - This is a developer tools video about a CLI keyboard for Android."
    result = parse_haiku_response(response)
    assert result["pass"] is True
    assert len(result["reasoning"]) > 0


def test_parse_haiku_response_no():
    response = "NO - This is a cooking tutorial, not developer tools."
    result = parse_haiku_response(response)
    assert result["pass"] is False


def test_parse_haiku_response_with_oss():
    response = "YES - Open source terminal emulator for Android. OPEN_SOURCE: yes, INDIE: yes, CORPORATE: no, CLICKBAIT: no"
    result = parse_haiku_response(response)
    assert result["pass"] is True
    assert result["is_oss"] is True
    assert result["is_indie"] is True
    assert result["is_corporate"] is False


def test_parse_gemini_response_with_score():
    text = """This is a Rust-based terminal file manager.
The project is open source on GitHub.
Creator is a solo developer.
Content is well-produced and practical.
SCORE: 8/10"""
    result = parse_gemini_response(text)
    assert result["score"] == 8.0
    assert "terminal file manager" in result["analysis"]


def test_parse_gemini_response_low_score():
    text = "Generic tutorial, nothing special. SCORE: 3/10"
    result = parse_gemini_response(text)
    assert result["score"] == 3.0


def test_parse_sonnet_response_share():
    text = """DECISION: SHARE
REASONING: High quality indie dev content about terminal tools
DRAFT: Solid Rust terminal file manager from a solo dev. Open source, clean codebase. youtube.com/watch?v=abc"""
    result = parse_sonnet_response(text)
    assert result["pass"] is True
    assert "indie" in result["reasoning"].lower()
    assert "Solid Rust" in result["draft"]


def test_parse_sonnet_response_skip():
    text = """DECISION: SKIP
REASONING: Corporate product launch, not relevant enough"""
    result = parse_sonnet_response(text)
    assert result["pass"] is False
    assert result["draft"] == ""
```

**Step 2: Implement evaluate.py**

Create worker/curation/evaluate.py:
```python
"""AI evaluation stages for the curation pipeline (stages 2-4)."""

from __future__ import annotations

import asyncio
import logging
import re
from typing import Optional

from worker.curation.models import CurationCandidate

log = logging.getLogger(__name__)


# --- Stage 2: Haiku quick-check ---

def build_haiku_prompt(candidate: CurationCandidate) -> str:
    """Build the prompt for Haiku stage 2 evaluation."""
    return f"""Evaluate this content for a developer tools curation account.

Title: {candidate.title}
Author: {candidate.author}
Description: {candidate.description[:500]}
Source: {candidate.source}
URL: {candidate.url}

Answer these questions:
1. Is this content about developer tools, terminal/CLI software, mobile development tools, keyboards, or related indie tech? Would a developer interested in terminal tools and mobile coding find this useful or interesting? Start your answer with YES or NO, then a one-line reason.

2. Classify (answer yes/no for each):
OPEN_SOURCE: Is this about an open source or free project?
INDIE: Is the creator a solo dev, indie dev, or small team?
CORPORATE: Is this a corporate product launch from a large company?
CLICKBAIT: Is this clickbait or low-effort content?"""


def parse_haiku_response(text: str) -> dict:
    """Parse Haiku's response into structured data."""
    text_upper = text.upper()
    first_word = text.strip().split()[0] if text.strip() else ""

    passed = first_word.upper().startswith("YES")

    is_oss = "OPEN_SOURCE: YES" in text_upper or "OPEN_SOURCE:YES" in text_upper
    is_indie = "INDIE: YES" in text_upper or "INDIE:YES" in text_upper
    is_corporate = "CORPORATE: YES" in text_upper or "CORPORATE:YES" in text_upper
    is_clickbait = "CLICKBAIT: YES" in text_upper or "CLICKBAIT:YES" in text_upper

    return {
        "pass": passed and not is_clickbait,
        "is_oss": is_oss,
        "is_indie": is_indie,
        "is_corporate": is_corporate,
        "is_clickbait": is_clickbait,
        "reasoning": text.strip(),
    }


async def run_haiku_check(candidate: CurationCandidate, api_key: str) -> dict:
    """Run Haiku quick-check on a candidate. Returns parsed result dict."""
    try:
        import anthropic
        client = anthropic.AsyncAnthropic(api_key=api_key)
        prompt = build_haiku_prompt(candidate)

        message = await client.messages.create(
            model="claude-haiku-4-5-20251001",
            max_tokens=300,
            messages=[{"role": "user", "content": prompt}],
        )
        response_text = message.content[0].text
        return parse_haiku_response(response_text)
    except Exception:
        log.exception("Haiku check failed for %s", candidate.url)
        return {"pass": False, "reasoning": "Haiku check failed"}


# --- Stage 3: Gemini CLI deep-dive ---

def build_gemini_prompt(candidate: CurationCandidate) -> str:
    """Build the prompt for Gemini stage 3 investigation."""
    return f"""Research this content and evaluate it for sharing on a developer tools curation account.

Title: {candidate.title}
Author: {candidate.author}
Source: {candidate.source}
URL: {candidate.url}
Description: {candidate.description[:500]}

Tell me:
1. What is it about? (2 sentences max)
2. Is the project open source? Link to the repo if you can find one.
3. Is the creator an indie dev, small team, or large company?
4. Is the content good and useful, or is it hype?
5. Would sharing this make a developer tools account look like it has good taste?

End with a single line: SCORE: N/10 (where N is your quality + relevance rating)"""


def parse_gemini_response(text: str) -> dict:
    """Parse Gemini's response into structured data."""
    score = 0.0
    score_match = re.search(r"SCORE:\s*(\d+(?:\.\d+)?)\s*/\s*10", text, re.IGNORECASE)
    if score_match:
        score = float(score_match.group(1))

    return {
        "score": score,
        "analysis": text.strip(),
    }


async def run_gemini_check(candidate: CurationCandidate) -> dict:
    """Run Gemini CLI deep-dive on a candidate. Returns parsed result dict."""
    prompt = build_gemini_prompt(candidate)

    try:
        process = await asyncio.create_subprocess_exec(
            "gemini", "-p", prompt, "--output-format", "text",
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        stdout, stderr = await asyncio.wait_for(
            process.communicate(), timeout=60
        )
    except asyncio.TimeoutError:
        log.warning("Gemini CLI timed out for %s", candidate.url)
        return {"score": 0.0, "analysis": "Gemini timed out"}
    except FileNotFoundError:
        log.error("Gemini CLI not found")
        return {"score": 0.0, "analysis": "Gemini CLI not found"}
    except Exception:
        log.exception("Gemini check failed for %s", candidate.url)
        return {"score": 0.0, "analysis": "Gemini check failed"}

    if process.returncode != 0:
        log.warning("Gemini CLI returned %d for %s", process.returncode, candidate.url)
        return {"score": 0.0, "analysis": f"Gemini error: {stderr.decode()[:200]}"}

    text = stdout.decode().strip()
    return parse_gemini_response(text)


# --- Stage 4: Sonnet final judgment + draft ---

def build_sonnet_prompt(
    candidate: CurationCandidate,
    haiku_result: dict,
    gemini_result: dict,
    platform: str = "twitter",
) -> str:
    """Build the prompt for Sonnet stage 4 final judgment."""
    from worker.content import PLATFORM_LIMITS
    char_limit = PLATFORM_LIMITS.get(platform, 280)

    return f"""You are drafting a social media post for a developer tools curation account (@KeyJawn).

This account shares interesting dev tools, terminal projects, and indie developer work. The voice is developer-to-developer: short sentences, no hype, no exclamation marks, no hashtag spam, no emoji strings.

Content to evaluate:
Title: {candidate.title}
Author: {candidate.author}
Source: {candidate.source}
URL: {candidate.url}

Previous evaluation:
- Haiku assessment: {haiku_result.get('reasoning', 'N/A')[:300]}
- Gemini analysis: {gemini_result.get('analysis', 'N/A')[:500]}
- Gemini score: {gemini_result.get('score', 0)}/10

Decision: Should we share this? Answer SHARE or SKIP on the first line, with a brief reason.

If SHARE: Write a {platform} post (max {char_limit} chars) that:
- Adds a brief take or context (not just a repost)
- Credits the creator when appropriate
- Puts the link at the end
- Uses the voice rules above (no hype, dev-to-dev, short)

Format your response as:
DECISION: SHARE or SKIP
REASONING: [why]
DRAFT: [post text, only if SHARE]"""


def parse_sonnet_response(text: str) -> dict:
    """Parse Sonnet's response into structured data."""
    lines = text.strip().split("\n")

    decision = "skip"
    reasoning = ""
    draft = ""

    in_draft = False
    draft_lines = []
    for line in lines:
        upper = line.upper().strip()
        if upper.startswith("DECISION:"):
            val = line.split(":", 1)[1].strip().upper()
            decision = "share" if "SHARE" in val else "skip"
            in_draft = False
        elif upper.startswith("REASONING:"):
            reasoning = line.split(":", 1)[1].strip()
            in_draft = False
        elif upper.startswith("DRAFT:"):
            draft_lines.append(line.split(":", 1)[1].strip())
            in_draft = True
        elif in_draft:
            draft_lines.append(line)

    if draft_lines:
        draft = "\n".join(draft_lines).strip()

    return {
        "pass": decision == "share",
        "reasoning": reasoning,
        "draft": draft,
    }


async def run_sonnet_check(
    candidate: CurationCandidate,
    haiku_result: dict,
    gemini_result: dict,
    api_key: str,
    platform: str = "twitter",
) -> dict:
    """Run Sonnet final judgment on a candidate. Returns parsed result dict."""
    try:
        import anthropic
        client = anthropic.AsyncAnthropic(api_key=api_key)
        prompt = build_sonnet_prompt(candidate, haiku_result, gemini_result, platform)

        message = await client.messages.create(
            model="claude-sonnet-4-5-20250929",
            max_tokens=500,
            messages=[{"role": "user", "content": prompt}],
        )
        response_text = message.content[0].text
        return parse_sonnet_response(response_text)
    except Exception:
        log.exception("Sonnet check failed for %s", candidate.url)
        return {"pass": False, "reasoning": "Sonnet check failed", "draft": ""}
```

**Step 3: Run tests**

Run: ssh officejawn "cd /home/jamditis/projects/keyjawn/worker && venv/bin/python -m pytest tests/test_curation.py -v -k 'haiku or gemini or sonnet'"
Expected: All PASSED

**Step 4: Commit**

```bash
git add worker/curation/evaluate.py tests/test_curation.py
git commit -m "feat(curation): add AI evaluation stages (Haiku, Gemini, Sonnet)"
```

---

### Task 8: Pipeline orchestrator

**Files:**
- Create: worker/curation/pipeline.py
- Test: tests/test_curation.py

**Step 1: Write failing test**

Add to tests/test_curation.py:
```python
from worker.curation.pipeline import CurationPipeline


def test_pipeline_keyword_filter():
    pipeline = CurationPipeline(anthropic_key="test", db=None)

    good = CurationCandidate(
        source="youtube", url="http://good.com", author="dev",
        title="Open source terminal keyboard for Android",
        description="CLI tool for mobile devs",
    )
    bad = CurationCandidate(
        source="youtube", url="http://bad.com", author="dev",
        title="Best photo filters",
        description="Instagram editing app",
    )

    passed = pipeline._keyword_filter([good, bad])
    assert len(passed) == 1
    assert passed[0].url == "http://good.com"
    assert passed[0].keyword_score >= 0.3
```

**Step 2: Implement pipeline**

Create worker/curation/pipeline.py:
```python
"""Four-stage evaluation pipeline for curation candidates."""

from __future__ import annotations

import logging
from typing import Optional

from worker.curation.boost import BOOST_SCORE, is_boosted
from worker.curation.evaluate import (
    run_gemini_check,
    run_haiku_check,
    run_sonnet_check,
)
from worker.curation.keywords import score_keywords
from worker.curation.models import CurationCandidate
from worker.db import Database

log = logging.getLogger(__name__)

KEYWORD_THRESHOLD = 0.3
GEMINI_THRESHOLD = 7.0


class CurationPipeline:
    def __init__(self, anthropic_key: str, db: Optional[Database] = None):
        self.anthropic_key = anthropic_key
        self.db = db

    def _keyword_filter(self, candidates: list[CurationCandidate]) -> list[CurationCandidate]:
        """Stage 1: Local keyword scoring. Drop candidates below threshold."""
        passed = []
        for c in candidates:
            score = score_keywords(c)
            if is_boosted(c.source, c.author):
                score = min(score + BOOST_SCORE, 1.0)
            c.keyword_score = score
            if score >= KEYWORD_THRESHOLD:
                passed.append(c)

        log.info("Keyword filter: %d/%d passed (threshold %.1f)",
                 len(passed), len(candidates), KEYWORD_THRESHOLD)
        return passed

    async def _haiku_filter(self, candidates: list[CurationCandidate]) -> list[CurationCandidate]:
        """Stage 2: Haiku quick-check. Drop candidates that fail."""
        passed = []
        for c in candidates:
            result = await run_haiku_check(c, self.anthropic_key)
            c.haiku_pass = result.get("pass", False)
            c.haiku_reasoning = result.get("reasoning", "")

            if result.get("is_oss"):
                c.keyword_score = min(c.keyword_score + 0.25, 1.0)
            if result.get("is_indie"):
                c.keyword_score = min(c.keyword_score + 0.20, 1.0)
            if result.get("is_corporate"):
                c.keyword_score = max(c.keyword_score - 0.15, 0.0)

            if c.haiku_pass:
                passed.append(c)

        log.info("Haiku filter: %d/%d passed", len(passed), len(candidates))
        return passed

    async def _gemini_evaluate(self, candidates: list[CurationCandidate]) -> list[CurationCandidate]:
        """Stage 3: Gemini deep-dive. Score and filter by quality threshold."""
        passed = []
        for c in candidates:
            result = await run_gemini_check(c)
            c.gemini_score = result.get("score", 0.0)
            c.gemini_analysis = result.get("analysis", "")

            if c.gemini_score >= GEMINI_THRESHOLD:
                passed.append(c)

        log.info("Gemini evaluate: %d/%d passed (threshold %.0f/10)",
                 len(passed), len(candidates), GEMINI_THRESHOLD)
        return passed

    async def _sonnet_judge(
        self, candidates: list[CurationCandidate], platform: str = "twitter"
    ) -> list[CurationCandidate]:
        """Stage 4: Sonnet judgment + draft post. Final filter."""
        passed = []
        for c in candidates:
            result = await run_sonnet_check(
                c, {"reasoning": c.haiku_reasoning},
                {"analysis": c.gemini_analysis, "score": c.gemini_score},
                self.anthropic_key, platform,
            )
            c.sonnet_pass = result.get("pass", False)
            c.sonnet_draft = result.get("draft", "")
            c.sonnet_reasoning = result.get("reasoning", "")

            c.final_score = round(
                (c.keyword_score * 0.3) + ((c.gemini_score / 10.0) * 0.7),
                2,
            )

            if c.sonnet_pass and c.sonnet_draft:
                passed.append(c)

        log.info("Sonnet judge: %d/%d passed", len(passed), len(candidates))
        return passed

    async def evaluate(
        self, candidates: list[CurationCandidate], platform: str = "twitter"
    ) -> list[CurationCandidate]:
        """Run all four pipeline stages. Returns approved candidates with drafts."""
        log.info("Pipeline starting with %d candidates", len(candidates))

        stage1 = self._keyword_filter(candidates)
        if not stage1:
            return []

        stage2 = await self._haiku_filter(stage1[:100])
        if not stage2:
            return []

        stage3 = await self._gemini_evaluate(stage2[:20])
        if not stage3:
            return []

        stage4 = await self._sonnet_judge(stage3[:5], platform)
        stage4.sort(key=lambda c: c.final_score, reverse=True)

        log.info("Pipeline complete: %d candidates approved", len(stage4))

        if self.db:
            for c in stage4:
                db_id = c.metadata.get("db_id", "")
                if db_id:
                    await self.db.update_curation_evaluation(
                        db_id,
                        keyword_score=c.keyword_score,
                        haiku_pass=c.haiku_pass,
                        haiku_reasoning=c.haiku_reasoning,
                        gemini_score=c.gemini_score,
                        gemini_analysis=c.gemini_analysis,
                        sonnet_pass=c.sonnet_pass,
                        sonnet_draft=c.sonnet_draft,
                        sonnet_reasoning=c.sonnet_reasoning,
                        final_score=c.final_score,
                        status="approved",
                    )

        return stage4
```

**Step 3: Run tests**

Run: ssh officejawn "cd /home/jamditis/projects/keyjawn/worker && venv/bin/python -m pytest tests/test_curation.py -v -k 'pipeline'"
Expected: All PASSED

**Step 4: Commit**

```bash
git add worker/curation/pipeline.py tests/test_curation.py
git commit -m "feat(curation): add four-stage evaluation pipeline orchestrator"
```

---

### Task 9: Curation monitor and config

**Files:**
- Create: worker/curation/monitor.py
- Modify: worker/config.py
- Test: tests/test_curation.py

**Step 1: Write failing tests for CurationConfig**

Add to tests/test_curation.py:
```python
from worker.config import CurationConfig


def test_curation_config_defaults():
    config = CurationConfig()
    assert config.max_curated_shares_per_day == 2
    assert config.youtube_api_key == ""
    assert config.scan_interval_hours == 6


def test_curation_config_custom():
    config = CurationConfig(
        youtube_api_key="yt-key",
        max_curated_shares_per_day=3,
    )
    assert config.youtube_api_key == "yt-key"
    assert config.max_curated_shares_per_day == 3
```

**Step 2: Add CurationConfig to config.py**

Add after RedisConfig in worker/config.py:

```python
@dataclass(frozen=True)
class CurationConfig:
    youtube_api_key: str = ""
    twitch_client_id: str = ""
    twitch_client_secret: str = ""
    anthropic_api_key: str = ""
    google_alert_urls: tuple = ()
    max_curated_shares_per_day: int = 2
    scan_interval_hours: int = 6
    twitch_scan_interval_hours: int = 8
```

Add to Config dataclass: `curation: CurationConfig = field(default_factory=CurationConfig)`

Update from_pass() to load curation keys (with try/except for missing keys):
```python
        try:
            yt_key = _pass_get("claude/api/youtube")
        except subprocess.CalledProcessError:
            yt_key = ""
        try:
            twitch_creds = _pass_get("claude/api/twitch").split("\n")
            twitch_id = twitch_creds[0]
            twitch_secret = twitch_creds[1] if len(twitch_creds) > 1 else ""
        except subprocess.CalledProcessError:
            twitch_id = ""
            twitch_secret = ""
        try:
            anthropic_key = _pass_get("claude/api/anthropic")
        except subprocess.CalledProcessError:
            anthropic_key = ""
```

Add to cls() return: `curation=CurationConfig(youtube_api_key=yt_key, twitch_client_id=twitch_id, twitch_client_secret=twitch_secret, anthropic_api_key=anthropic_key)`

Update for_testing(): `curation=CurationConfig(anthropic_api_key="test-anthropic-key")`

**Step 3: Implement curation monitor**

Create worker/curation/monitor.py:
```python
"""Curation monitor: orchestrates source scanning and pipeline evaluation."""

from __future__ import annotations

import json
import logging
from typing import Optional

from worker.config import CurationConfig
from worker.curation.models import CurationCandidate
from worker.curation.pipeline import CurationPipeline
from worker.curation.sources.news import NewsSource
from worker.curation.sources.twitch import TwitchSource
from worker.curation.sources.youtube import YouTubeSource
from worker.db import Database

log = logging.getLogger(__name__)


class CurationMonitor:
    def __init__(self, config: CurationConfig, db: Database):
        self.config = config
        self.db = db

        self.youtube: Optional[YouTubeSource] = None
        self.news: Optional[NewsSource] = None
        self.twitch: Optional[TwitchSource] = None
        self.pipeline: Optional[CurationPipeline] = None

        if config.youtube_api_key:
            self.youtube = YouTubeSource(config.youtube_api_key)
        self.news = NewsSource(list(config.google_alert_urls))
        if config.twitch_client_id and config.twitch_client_secret:
            self.twitch = TwitchSource(config.twitch_client_id, config.twitch_client_secret)
        if config.anthropic_api_key:
            self.pipeline = CurationPipeline(config.anthropic_api_key, db)

    async def scan_sources(self, include_twitch: bool = False) -> list[CurationCandidate]:
        """Scan all configured sources and return candidates."""
        all_candidates = []

        if self.youtube:
            try:
                yt_results = await self.youtube.scan()
                all_candidates.extend(yt_results)
            except Exception:
                log.exception("YouTube scan failed")

        if self.news:
            try:
                news_results = await self.news.scan()
                all_candidates.extend(news_results)
            except Exception:
                log.exception("News scan failed")

        if include_twitch and self.twitch:
            try:
                twitch_results = await self.twitch.scan()
                all_candidates.extend(twitch_results)
            except Exception:
                log.exception("Twitch scan failed")

        log.info("Source scan: %d total candidates", len(all_candidates))
        return all_candidates

    async def store_candidates(self, candidates: list[CurationCandidate]) -> int:
        """Store candidates in DB, deduplicating by URL. Returns count stored."""
        stored = 0
        for c in candidates:
            cid = await self.db.insert_curation_candidate(
                source=c.source,
                url=c.url,
                title=c.title,
                author=c.author,
                description=c.description,
                published_at=c.published.isoformat() if c.published else None,
                metadata=json.dumps(c.metadata) if c.metadata else None,
            )
            if cid:
                c.metadata["db_id"] = cid
                stored += 1
        return stored

    async def run_evaluation(self, platform: str = "twitter") -> list[CurationCandidate]:
        """Evaluate all new candidates through the pipeline."""
        if not self.pipeline:
            log.warning("No Anthropic API key configured, skipping evaluation")
            return []

        new_rows = await self.db.get_new_curations(limit=50)
        candidates = []
        for row in new_rows:
            c = CurationCandidate(
                source=row["source"],
                url=row["url"],
                title=row["title"],
                description=row.get("description", ""),
                author=row.get("author", ""),
                metadata={"db_id": row["id"]},
            )
            candidates.append(c)

        if not candidates:
            log.info("No new candidates to evaluate")
            return []

        approved = await self.pipeline.evaluate(candidates, platform)
        log.info("Evaluation complete: %d approved from %d candidates",
                 len(approved), len(candidates))
        return approved

    async def scan_and_evaluate(
        self, include_twitch: bool = False, platform: str = "twitter"
    ) -> int:
        """Full cycle: scan, store, evaluate. Returns count of approved."""
        candidates = await self.scan_sources(include_twitch)
        stored = await self.store_candidates(candidates)
        log.info("Stored %d new candidates (out of %d scanned)", stored, len(candidates))

        approved = await self.run_evaluation(platform)
        return len(approved)
```

**Step 4: Run all tests**

Run: ssh officejawn "cd /home/jamditis/projects/keyjawn/worker && venv/bin/python -m pytest tests/test_curation.py -v"
Expected: All PASSED

**Step 5: Commit**

```bash
git add worker/curation/monitor.py worker/config.py tests/test_curation.py
git commit -m "feat(curation): add curation monitor and config"
```

---

### Task 10: Telegram message format for curated shares

**Files:**
- Modify: worker/telegram.py
- Test: tests/test_curation.py

**Step 1: Write failing test**

Add to tests/test_curation.py:
```python
from worker.telegram import format_curation_message


def test_format_curation_message():
    msg = format_curation_message(
        action_id="abc123",
        source="YouTube - Dreams of Code",
        title="Building a terminal file manager in Rust",
        score=0.87,
        reasoning="Open source Rust CLI tool, solo dev, well-produced",
        draft="Solid walkthrough of building a terminal file manager in Rust.",
        platform="twitter",
    )
    assert "[CURATE]" in msg
    assert "Dreams of Code" in msg
    assert "0.87" in msg
    assert "terminal file manager" in msg
    assert "Draft" in msg
```

**Step 2: Add format_curation_message to telegram.py**

Add to worker/telegram.py:
```python
def format_curation_message(
    action_id: str,
    source: str,
    title: str,
    score: float,
    reasoning: str,
    draft: str,
    platform: str,
) -> str:
    """Format a curation share approval message."""
    lines = [
        "<b>[CURATE] Share recommendation</b>",
        "",
        f"<b>Source:</b> {_escape_html(source)}",
        f"<b>Title:</b> {_escape_html(title)}",
        f"<b>Score:</b> {score:.2f}",
        "",
        f"<b>AI reasoning:</b> {_escape_html(reasoning)}",
        "",
        f"<b>Draft post ({platform}):</b>",
        f"<pre>{_escape_html(draft)}</pre>",
    ]
    return "\n".join(lines)
```

**Step 3: Run tests**

Run: ssh officejawn "cd /home/jamditis/projects/keyjawn/worker && venv/bin/python -m pytest tests/test_curation.py -v -k 'curation_message'"
Expected: PASSED

**Step 4: Commit**

```bash
git add worker/telegram.py tests/test_curation.py
git commit -m "feat(curation): add Telegram curation approval message format"
```

---

### Task 11: Action picker and runner integration

**Files:**
- Modify: worker/executor.py
- Modify: worker/runner.py
- Test: tests/test_curation.py

**Step 1: Write failing test**

Add to tests/test_curation.py:
```python
from worker.executor import ActionPicker, EscalationTier


def test_curation_share_needs_approval():
    tier = ActionPicker.get_escalation_tier("curated_share", "twitter")
    assert tier == EscalationTier.BUTTONS
    tier = ActionPicker.get_escalation_tier("curated_share", "bluesky")
    assert tier == EscalationTier.BUTTONS
```

**Step 2: Update executor.py pick_actions**

Add after the findings block in pick_actions() (around line 100):

```python
        # 3. Fill curation slots from approved curations (separate budget)
        curation_remaining = self.config.curation.max_curated_shares_per_day
        curation_today = await self.db.get_daily_curation_count()
        curation_remaining -= curation_today

        if curation_remaining > 0:
            curations = await self.db.get_approved_curations(
                limit=curation_remaining
            )
            for curation in curations:
                actions.append({
                    "source": "curation",
                    "curation_id": curation["id"],
                    "action_type": "curated_share",
                    "platform": "twitter",
                    "content": curation["sonnet_draft"] or curation["title"],
                    "curation_url": curation["url"],
                    "curation_title": curation["title"],
                    "curation_source": curation["source"],
                    "curation_score": curation["final_score"],
                    "curation_reasoning": curation["sonnet_reasoning"] or "",
                    "tier": self.get_escalation_tier("curated_share", "twitter"),
                })
```

Note: curated shares are NOT counted against the self-promo remaining budget. They have their own separate budget via max_curated_shares_per_day.

**Step 3: Update runner.py**

Add to __init__: `self.curation_monitor = None`

Add to start(), after existing client init:
```python
        from worker.curation.monitor import CurationMonitor
        self.curation_monitor = CurationMonitor(self.config.curation, self.db)
```

Add new method:
```python
    async def run_curation_scan(self, include_twitch: bool = False):
        """Run one curation scan + evaluation cycle."""
        if not self.curation_monitor:
            return
        logger.info("starting curation scan")
        approved = await self.curation_monitor.scan_and_evaluate(
            include_twitch=include_twitch
        )
        logger.info("curation scan done: %d new candidates approved", approved)
```

In _execute_with_approval, add special handling for curated shares (use format_curation_message instead of format_escalation_message when action_type is curated_share).

**Step 4: Run full test suite**

Run: ssh officejawn "cd /home/jamditis/projects/keyjawn/worker && venv/bin/python -m pytest tests/ -v"
Expected: All PASSED

**Step 5: Commit**

```bash
git add worker/executor.py worker/runner.py tests/test_curation.py
git commit -m "feat(curation): integrate curation into action picker and runner"
```

---

### Task 12: Management commands and scheduler

**Files:**
- Modify: worker/manage.py
- Modify: worker/main.py

**Step 1: Add curation management commands to manage.py**

Add a `curation-scan` subcommand that loads config, creates DB and curation monitor, runs scan_and_evaluate(), and prints results.

Add a `curation-status` subcommand that shows candidate counts by status, today's curation budget usage, and most recent approved candidates.

**Step 2: Add scheduler jobs for curation to main.py**

Add APScheduler interval jobs:
- Every 6 hours: run_curation_scan() for YouTube + News
- Every 8 hours: run_curation_scan(include_twitch=True) for Twitch

**Step 3: Run full test suite**

Run: ssh officejawn "cd /home/jamditis/projects/keyjawn/worker && venv/bin/python -m pytest tests/ -v"
Expected: All PASSED

**Step 4: Commit**

```bash
git add worker/manage.py worker/main.py
git commit -m "feat(curation): add management commands and scheduler jobs"
```

---

### Task 13: API key setup and integration test

**Step 1: Create YouTube Data API key**

Go to Google Cloud Console (project houseofjawn):
1. Enable YouTube Data API v3
2. Create an API key (restrict to YouTube Data API v3 only)
3. Store: pass insert claude/api/youtube

**Step 2: Create Twitch application**

Go to dev.twitch.tv/console:
1. Register a new application
2. Get Client ID and Client Secret
3. Store: pass insert -m claude/api/twitch (client_id line 1, client_secret line 2)

**Step 3: Verify Anthropic API key exists**

Run: pass show claude/api/anthropic | head -c 10

**Step 4: Copy keys to officejawn**

Store the new keys in officejawn pass store.

**Step 5: Integration test**

Run curation-scan on officejawn:
```bash
ssh officejawn "cd /home/jamditis/projects/keyjawn/worker && venv/bin/python -m worker.manage curation-scan"
```

Verify:
- YouTube returns results (if API key is set)
- News RSS feeds parse correctly
- Twitch returns clips (if keys are set)
- Keyword filter drops irrelevant content
- Pipeline logs show progression through stages

**Step 6: Restart worker**

```bash
ssh officejawn "sudo systemctl restart keyjawn-worker"
```

**Step 7: Final commit and push**

```bash
git add -A
git commit -m "feat(curation): complete content curation pipeline"
git push
```
