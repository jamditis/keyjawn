# Content curation pipeline implementation plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a content curation pipeline that discovers, evaluates, and shares interesting dev tools content from YouTube, Google News/RSS, and Twitch -- biased toward open source and indie projects.

**Architecture:** Three source monitors feed CurationCandidate objects into a two-stage evaluation: (1) local keyword filter, then (2) parallel Claude Code CLI subagents for AI evaluation. Each subagent does quick-check + deep investigation + draft writing in a single session. No API calls -- all AI runs through Claude Code CLI subscriptions. Approved candidates post to Twitter/Bluesky via the existing action system with Telegram approval. Separate daily budget from self-promo (2 curated shares/day).

**Tech stack:** httpx (HTTP, already in deps), feedparser (RSS parsing, new dep), Claude Code CLI (subprocess, no new deps), aiosqlite (existing)

**Key design decision:** NO direct Anthropic/Gemini API calls. All AI evaluation runs through CLI tools (claude -p) in subprocess calls, paid for by existing subscriptions. The worker spins up parallel Claude Code subagents for concurrent candidate evaluation.

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

Add feedparser to the dependencies list in pyproject.toml:

```toml
dependencies = [
    "aiosqlite>=0.20.0",
    "httpx>=0.27.0",
    "twikit>=2.0.0",
    "atproto>=0.0.55",
    "apscheduler>=3.10.0",
    "redis>=5.0.0",
    "feedparser>=6.0.0",
]
```

Note: NO anthropic SDK dependency. All AI evaluation runs through Claude Code CLI subprocesses.

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

Run: ssh officejawn "cd /home/jamditis/projects/keyjawn/worker && venv/bin/pip install feedparser"

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

### Task 7: AI evaluation via Claude Code CLI subprocesses

**Files:**
- Create: worker/curation/evaluate.py
- Test: tests/test_curation.py

**Key design decision:** NO API calls. All AI evaluation runs through `claude -p` (Claude Code CLI print mode) as async subprocesses. The worker spins up parallel subagents using `asyncio.gather()` for concurrent evaluation. This uses the existing Claude Code subscription -- zero marginal cost.

**Step 1: Write failing tests**

Add to tests/test_curation.py:
```python
from worker.curation.evaluate import (
    build_evaluate_prompt, parse_evaluate_response,
    build_draft_prompt, parse_draft_response,
)


def test_build_evaluate_prompt():
    c = CurationCandidate(
        source="youtube", url="http://test.com",
        title="Open source terminal tool",
        description="A CLI keyboard for Android developers",
        author="devuser",
    )
    prompt = build_evaluate_prompt(c)
    assert "Open source terminal tool" in prompt
    assert "CLI keyboard" in prompt
    assert "RELEVANT:" in prompt


def test_parse_evaluate_response_relevant():
    response = """RELEVANT: yes
REASONING: Developer tools video about a CLI keyboard for Android
OPEN_SOURCE: yes
INDIE: yes
CORPORATE: no
CLICKBAIT: no
QUALITY: 8/10"""
    result = parse_evaluate_response(response)
    assert result["relevant"] is True
    assert result["is_oss"] is True
    assert result["is_indie"] is True
    assert result["quality_score"] == 8.0


def test_parse_evaluate_response_irrelevant():
    response = """RELEVANT: no
REASONING: This is a cooking tutorial, not developer tools
QUALITY: 2/10"""
    result = parse_evaluate_response(response)
    assert result["relevant"] is False
    assert result["quality_score"] == 2.0


def test_build_draft_prompt():
    c = CurationCandidate(
        source="youtube", url="http://test.com",
        title="Terminal file manager in Rust",
        description="Building a TUI app",
        author="devuser",
    )
    evaluation = {"reasoning": "Great indie dev content", "quality_score": 8.0}
    prompt = build_draft_prompt(c, evaluation, "twitter")
    assert "Terminal file manager" in prompt
    assert "280" in prompt
    assert "DECISION:" in prompt


def test_parse_draft_response_share():
    text = """DECISION: SHARE
REASONING: High quality indie dev content about terminal tools
DRAFT: Solid Rust terminal file manager from a solo dev. Open source, clean codebase. youtube.com/watch?v=abc"""
    result = parse_draft_response(text)
    assert result["share"] is True
    assert "indie" in result["reasoning"].lower()
    assert "Solid Rust" in result["draft"]


def test_parse_draft_response_skip():
    text = """DECISION: SKIP
REASONING: Corporate product launch, not relevant enough"""
    result = parse_draft_response(text)
    assert result["share"] is False
    assert result["draft"] == ""
```

**Step 2: Implement evaluate.py (CLI-based, no API calls)**

Create worker/curation/evaluate.py:
```python
"""AI evaluation for the curation pipeline using Claude Code CLI.

No direct API calls. All AI runs through 'claude -p' subprocess calls,
paid for by existing Claude Code subscription. The worker spins up
parallel subagents via asyncio.gather() for concurrent evaluation.
"""

from __future__ import annotations

import asyncio
import logging
import re

from worker.curation.models import CurationCandidate

log = logging.getLogger(__name__)

CLI_TIMEOUT = 90  # seconds per subprocess call


async def _run_claude(prompt: str, model: str = "sonnet") -> str:
    """Run a Claude Code CLI prompt and return the response text.

    Uses 'claude -p' (print mode) for non-interactive one-shot prompts.
    No API key needed -- uses the CLI's own auth/subscription.
    """
    try:
        process = await asyncio.create_subprocess_exec(
            "claude", "-p", prompt, "--model", model,
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        stdout, stderr = await asyncio.wait_for(
            process.communicate(), timeout=CLI_TIMEOUT
        )
    except asyncio.TimeoutError:
        log.warning("claude CLI timed out after %ds", CLI_TIMEOUT)
        return ""
    except FileNotFoundError:
        log.error("claude CLI not found")
        return ""
    except Exception:
        log.exception("claude CLI failed")
        return ""

    if process.returncode != 0:
        log.warning("claude CLI returned %d: %s", process.returncode, stderr.decode()[:200])
        return ""

    return stdout.decode().strip()


# --- Evaluation prompt (quick-check + investigation in one call) ---

def build_evaluate_prompt(candidate: CurationCandidate) -> str:
    """Build a single evaluation prompt that covers relevance + quality."""
    return f"""Evaluate this content for a developer tools curation account that shares interesting CLI tools, terminal projects, and indie developer work.

Title: {candidate.title}
Author: {candidate.author}
Description: {candidate.description[:500]}
Source: {candidate.source}
URL: {candidate.url}

Answer each line exactly in this format:
RELEVANT: yes or no
REASONING: one-line explanation
OPEN_SOURCE: yes or no or unknown
INDIE: yes or no or unknown (is the creator a solo/indie dev or small team?)
CORPORATE: yes or no (is this a large company product launch?)
CLICKBAIT: yes or no
QUALITY: N/10 (overall quality and relevance score)"""


def parse_evaluate_response(text: str) -> dict:
    """Parse the evaluation response into structured data."""
    text_upper = text.upper()

    def _check(key: str) -> bool:
        pattern = rf"{key}:\s*(YES)"
        return bool(re.search(pattern, text_upper))

    def _extract_score() -> float:
        match = re.search(r"QUALITY:\s*(\d+(?:\.\d+)?)\s*/\s*10", text_upper)
        return float(match.group(1)) if match else 0.0

    reasoning = ""
    for line in text.strip().split("\n"):
        if line.upper().startswith("REASONING:"):
            reasoning = line.split(":", 1)[1].strip()
            break

    return {
        "relevant": _check("RELEVANT"),
        "reasoning": reasoning,
        "is_oss": _check("OPEN_SOURCE"),
        "is_indie": _check("INDIE"),
        "is_corporate": _check("CORPORATE"),
        "is_clickbait": _check("CLICKBAIT"),
        "quality_score": _extract_score(),
        "raw": text.strip(),
    }


# --- Draft prompt (final judgment + post writing) ---

def build_draft_prompt(
    candidate: CurationCandidate,
    evaluation: dict,
    platform: str = "twitter",
) -> str:
    """Build the draft writing prompt."""
    from worker.content import PLATFORM_LIMITS
    char_limit = PLATFORM_LIMITS.get(platform, 280)

    return f"""You are drafting a social media post for @KeyJawn, a developer tools curation account.

Voice: developer-to-developer. Short sentences. No hype. No exclamation marks. No hashtag spam. No emoji strings. Contractions are fine.

Content to share:
Title: {candidate.title}
Author: {candidate.author}
Source: {candidate.source}
URL: {candidate.url}

Evaluation: {evaluation.get('reasoning', '')}
Quality: {evaluation.get('quality_score', 0)}/10

Should we share this? Answer on the first line.

If yes, write a {platform} post (max {char_limit} chars) that adds a brief take or context, credits the creator, and puts the link at the end.

Format:
DECISION: SHARE or SKIP
REASONING: [one line why]
DRAFT: [post text, only if SHARE]"""


def parse_draft_response(text: str) -> dict:
    """Parse the draft response."""
    lines = text.strip().split("\n")

    decision = "skip"
    reasoning = ""
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

    draft = "\n".join(draft_lines).strip() if draft_lines else ""

    return {
        "share": decision == "share",
        "reasoning": reasoning,
        "draft": draft,
    }


async def evaluate_candidate(
    candidate: CurationCandidate, platform: str = "twitter"
) -> dict:
    """Full evaluation of a single candidate using Claude Code CLI.

    Runs two sequential claude -p calls:
    1. Evaluate: relevance, quality, OSS/indie classification
    2. Draft: final share/skip decision + post text (only if eval passes)

    Returns a dict with all evaluation results.
    """
    # Step 1: Evaluate
    eval_prompt = build_evaluate_prompt(candidate)
    eval_text = await _run_claude(eval_prompt, model="haiku")
    if not eval_text:
        return {"relevant": False, "reasoning": "CLI evaluation failed"}

    evaluation = parse_evaluate_response(eval_text)

    # Early exit if not relevant or low quality
    if not evaluation["relevant"] or evaluation["is_clickbait"]:
        return evaluation
    if evaluation["quality_score"] < 6.0:
        return evaluation

    # Step 2: Draft post (only for candidates that pass evaluation)
    draft_prompt = build_draft_prompt(candidate, evaluation, platform)
    draft_text = await _run_claude(draft_prompt, model="sonnet")
    if not draft_text:
        evaluation["share"] = False
        return evaluation

    draft_result = parse_draft_response(draft_text)
    evaluation.update(draft_result)
    return evaluation


async def evaluate_batch(
    candidates: list[CurationCandidate],
    platform: str = "twitter",
    max_parallel: int = 5,
) -> list[tuple[CurationCandidate, dict]]:
    """Evaluate multiple candidates in parallel using Claude Code CLI subagents.

    Spins up to max_parallel concurrent subprocess evaluations.
    Returns list of (candidate, result) tuples for candidates that pass.
    """
    semaphore = asyncio.Semaphore(max_parallel)

    async def _eval_one(c: CurationCandidate) -> tuple[CurationCandidate, dict]:
        async with semaphore:
            result = await evaluate_candidate(c, platform)
            return (c, result)

    tasks = [_eval_one(c) for c in candidates]
    results = await asyncio.gather(*tasks, return_exceptions=True)

    approved = []
    for r in results:
        if isinstance(r, Exception):
            log.error("Evaluation error: %s", r)
            continue
        candidate, result = r
        if result.get("share") and result.get("draft"):
            approved.append((candidate, result))

    log.info("Batch evaluation: %d/%d approved", len(approved), len(candidates))
    return approved
```

**Step 3: Run tests**

Run: ssh officejawn "cd /home/jamditis/projects/keyjawn/worker && venv/bin/python -m pytest tests/test_curation.py -v -k 'evaluate or draft'"
Expected: All PASSED

**Step 4: Commit**

```bash
git add worker/curation/evaluate.py tests/test_curation.py
git commit -m "feat(curation): add CLI-based AI evaluation with parallel subagents"
```

---

### Task 8: Pipeline orchestrator

**Files:**
- Create: worker/curation/pipeline.py
- Test: tests/test_curation.py

**Key design:** The pipeline has two stages: (1) local keyword filter, then (2) parallel CLI-based AI evaluation via evaluate_batch() from Task 7. No API calls.

**Step 1: Write failing test**

Add to tests/test_curation.py:
```python
from worker.curation.pipeline import CurationPipeline


def test_pipeline_keyword_filter():
    pipeline = CurationPipeline(db=None)

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
"""Two-stage evaluation pipeline for curation candidates.

Stage 1: Local keyword scoring (instant, no API calls)
Stage 2: Parallel CLI-based AI evaluation via Claude Code subprocess calls

No direct LLM API calls. All AI runs through 'claude -p' in evaluate.py.
"""

from __future__ import annotations

import logging
from typing import Optional

from worker.curation.boost import BOOST_SCORE, is_boosted
from worker.curation.evaluate import evaluate_batch
from worker.curation.keywords import score_keywords
from worker.curation.models import CurationCandidate
from worker.db import Database

log = logging.getLogger(__name__)

KEYWORD_THRESHOLD = 0.3


class CurationPipeline:
    def __init__(self, db: Optional[Database] = None, max_parallel: int = 5):
        self.db = db
        self.max_parallel = max_parallel

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

    async def evaluate(
        self, candidates: list[CurationCandidate], platform: str = "twitter"
    ) -> list[CurationCandidate]:
        """Run keyword filter then parallel CLI-based AI evaluation."""
        log.info("Pipeline starting with %d candidates", len(candidates))

        # Stage 1: keyword filter (local, instant)
        filtered = self._keyword_filter(candidates)
        if not filtered:
            return []

        # Stage 2: parallel Claude Code CLI evaluation (top 20 by keyword score)
        filtered.sort(key=lambda c: c.keyword_score, reverse=True)
        to_evaluate = filtered[:20]

        approved_pairs = await evaluate_batch(
            to_evaluate, platform=platform, max_parallel=self.max_parallel
        )

        # Update candidate fields from evaluation results
        approved = []
        for candidate, result in approved_pairs:
            candidate.haiku_pass = result.get("relevant", False)
            candidate.haiku_reasoning = result.get("reasoning", "")
            candidate.sonnet_pass = result.get("share", False)
            candidate.sonnet_draft = result.get("draft", "")
            candidate.sonnet_reasoning = result.get("reasoning", "")
            candidate.gemini_score = result.get("quality_score", 0.0)
            candidate.final_score = round(
                (candidate.keyword_score * 0.3)
                + ((result.get("quality_score", 0.0) / 10.0) * 0.7),
                2,
            )
            approved.append(candidate)

        approved.sort(key=lambda c: c.final_score, reverse=True)
        log.info("Pipeline complete: %d candidates approved", len(approved))

        # Persist evaluation results to DB
        if self.db:
            for c in approved:
                db_id = c.metadata.get("db_id", "")
                if db_id:
                    await self.db.update_curation_evaluation(
                        db_id,
                        keyword_score=c.keyword_score,
                        haiku_pass=c.haiku_pass,
                        haiku_reasoning=c.haiku_reasoning,
                        gemini_score=c.gemini_score,
                        sonnet_pass=c.sonnet_pass,
                        sonnet_draft=c.sonnet_draft,
                        sonnet_reasoning=c.sonnet_reasoning,
                        final_score=c.final_score,
                        status="approved",
                    )

        return approved
```

**Step 3: Run tests**

Run: ssh officejawn "cd /home/jamditis/projects/keyjawn/worker && venv/bin/python -m pytest tests/test_curation.py -v -k 'pipeline'"
Expected: All PASSED

**Step 4: Commit**

```bash
git add worker/curation/pipeline.py tests/test_curation.py
git commit -m "feat(curation): add two-stage evaluation pipeline (keyword + parallel CLI)"
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
    google_alert_urls: tuple = ()
    max_curated_shares_per_day: int = 2
    max_parallel_evaluations: int = 5
    scan_interval_hours: int = 6
    twitch_scan_interval_hours: int = 8
```

Note: NO anthropic_api_key field. AI evaluation runs through Claude Code CLI subprocesses (see evaluate.py).

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
```

Add to cls() return: `curation=CurationConfig(youtube_api_key=yt_key, twitch_client_id=twitch_id, twitch_client_secret=twitch_secret)`

Update for_testing(): `curation=CurationConfig()`

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

        if config.youtube_api_key:
            self.youtube = YouTubeSource(config.youtube_api_key)
        self.news = NewsSource(list(config.google_alert_urls))
        if config.twitch_client_id and config.twitch_client_secret:
            self.twitch = TwitchSource(config.twitch_client_id, config.twitch_client_secret)
        self.pipeline = CurationPipeline(
            db=db, max_parallel=config.max_parallel_evaluations
        )

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

**Step 3: Copy keys to officejawn**

Store the new keys in officejawn pass store.

**Step 4: Integration test**

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

**Step 5: Restart worker**

```bash
ssh officejawn "sudo systemctl restart keyjawn-worker"
```

**Step 6: Final commit and push**

```bash
git add -A
git commit -m "feat(curation): complete content curation pipeline"
git push
```
