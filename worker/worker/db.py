"""SQLite storage for keyjawn-worker findings, actions, calendar, and metrics."""

from datetime import datetime, timezone
from typing import Optional
from uuid import uuid4

import aiosqlite

SCHEMA = """
CREATE TABLE IF NOT EXISTS findings (
    id TEXT PRIMARY KEY,
    platform TEXT NOT NULL,
    source_url TEXT NOT NULL,
    source_user TEXT NOT NULL,
    content TEXT NOT NULL,
    relevance_score REAL NOT NULL,
    status TEXT NOT NULL DEFAULT 'queued',
    found_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS actions (
    id TEXT PRIMARY KEY,
    action_type TEXT NOT NULL,
    platform TEXT NOT NULL,
    content TEXT NOT NULL,
    status TEXT NOT NULL,
    finding_id TEXT,
    post_url TEXT,
    acted_at TEXT NOT NULL,
    approval_decision TEXT,
    approval_timestamp TEXT,
    FOREIGN KEY (finding_id) REFERENCES findings(id)
);

CREATE TABLE IF NOT EXISTS calendar (
    id TEXT PRIMARY KEY,
    scheduled_date TEXT NOT NULL,
    pillar TEXT NOT NULL,
    platform TEXT NOT NULL,
    content_draft TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'planned'
);

CREATE TABLE IF NOT EXISTS metrics (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    platform TEXT NOT NULL,
    metric_type TEXT NOT NULL,
    value REAL NOT NULL,
    recorded_at TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_findings_status ON findings(status);
CREATE INDEX IF NOT EXISTS idx_actions_acted_date ON actions(date(acted_at));
CREATE INDEX IF NOT EXISTS idx_calendar_scheduled_date ON calendar(scheduled_date);

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

CREATE TABLE IF NOT EXISTS engagement_opportunities (
    id TEXT PRIMARY KEY,
    platform TEXT NOT NULL,
    post_id TEXT NOT NULL,
    post_url TEXT NOT NULL,
    author TEXT NOT NULL,
    text TEXT,
    opportunity_type TEXT NOT NULL,
    status TEXT DEFAULT 'pending',
    created_at TEXT NOT NULL,
    acted_at TEXT
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_engagement_post ON engagement_opportunities(platform, post_id);
"""


def _new_id() -> str:
    return uuid4().hex[:12]


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


class Database:
    def __init__(self, db_path: str):
        self.db_path = db_path
        self._db: Optional[aiosqlite.Connection] = None

    async def init(self):
        self._db = await aiosqlite.connect(self.db_path)
        self._db.row_factory = aiosqlite.Row
        await self._db.executescript(SCHEMA)
        await self._db.commit()

    async def close(self):
        if self._db:
            await self._db.close()
            self._db = None

    async def list_tables(self) -> list[str]:
        cursor = await self._db.execute(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' ORDER BY name"
        )
        rows = await cursor.fetchall()
        return [row["name"] for row in rows]

    # -- findings --

    async def queue_finding(
        self,
        platform: str,
        source_url: str,
        source_user: str,
        content: str,
        relevance_score: float,
    ) -> str:
        finding_id = _new_id()
        await self._db.execute(
            """INSERT INTO findings (id, platform, source_url, source_user, content, relevance_score, status, found_at)
               VALUES (?, ?, ?, ?, ?, ?, 'queued', ?)""",
            (finding_id, platform, source_url, source_user, content, relevance_score, _now()),
        )
        await self._db.commit()
        return finding_id

    async def get_finding(self, finding_id: str) -> Optional[dict]:
        cursor = await self._db.execute("SELECT * FROM findings WHERE id = ?", (finding_id,))
        row = await cursor.fetchone()
        return dict(row) if row else None

    async def update_finding_status(self, finding_id: str, status: str):
        await self._db.execute(
            "UPDATE findings SET status = ? WHERE id = ?", (status, finding_id)
        )
        await self._db.commit()

    async def get_queued_findings(self, limit: int = 10) -> list[dict]:
        cursor = await self._db.execute(
            "SELECT * FROM findings WHERE status = 'queued' ORDER BY relevance_score DESC, found_at ASC LIMIT ?",
            (limit,),
        )
        rows = await cursor.fetchall()
        return [dict(row) for row in rows]

    # -- actions --

    async def log_action(
        self,
        action_type: str,
        platform: str,
        content: str,
        status: str,
        finding_id: Optional[str] = None,
        post_url: Optional[str] = None,
    ) -> str:
        action_id = _new_id()
        await self._db.execute(
            """INSERT INTO actions (id, action_type, platform, content, status, finding_id, post_url, acted_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
            (action_id, action_type, platform, content, status, finding_id, post_url, _now()),
        )
        await self._db.commit()
        return action_id

    async def get_action(self, action_id: str) -> Optional[dict]:
        cursor = await self._db.execute("SELECT * FROM actions WHERE id = ?", (action_id,))
        row = await cursor.fetchone()
        return dict(row) if row else None

    async def get_daily_action_count(self, platform: Optional[str] = None) -> int:
        today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
        if platform:
            cursor = await self._db.execute(
                "SELECT COUNT(*) FROM actions WHERE status = 'posted' AND date(acted_at) = ? AND platform = ?",
                (today, platform),
            )
        else:
            cursor = await self._db.execute(
                "SELECT COUNT(*) FROM actions WHERE status = 'posted' AND date(acted_at) = ?",
                (today,),
            )
        row = await cursor.fetchone()
        return row[0]

    # -- calendar --

    async def add_calendar_entry(
        self,
        scheduled_date: str,
        pillar: str,
        platform: str,
        content_draft: str,
        status: str = "planned",
    ) -> str:
        entry_id = _new_id()
        await self._db.execute(
            """INSERT INTO calendar (id, scheduled_date, pillar, platform, content_draft, status)
               VALUES (?, ?, ?, ?, ?, ?)""",
            (entry_id, scheduled_date, pillar, platform, content_draft, status),
        )
        await self._db.commit()
        return entry_id

    async def get_calendar_entries(self, date_str: str) -> list[dict]:
        cursor = await self._db.execute(
            "SELECT * FROM calendar WHERE scheduled_date = ?", (date_str,)
        )
        rows = await cursor.fetchall()
        return [dict(row) for row in rows]

    # -- metrics --

    async def record_metric(self, platform: str, metric_type: str, value: float):
        await self._db.execute(
            "INSERT INTO metrics (platform, metric_type, value, recorded_at) VALUES (?, ?, ?, ?)",
            (platform, metric_type, value, _now()),
        )
        await self._db.commit()

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
        today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
        cursor = await self._db.execute(
            "SELECT COUNT(*) FROM curation_candidates WHERE status = 'posted' AND date(posted_at) = ?",
            (today,),
        )
        row = await cursor.fetchone()
        return row[0]

    # -- engagement --

    async def insert_engagement(
        self,
        platform: str,
        post_id: str,
        post_url: str,
        author: str,
        text: str,
        opportunity_type: str,
    ) -> str | None:
        """Insert engagement opportunity. Returns ID or None if already exists."""
        cursor = await self._db.execute(
            "SELECT 1 FROM engagement_opportunities WHERE platform = ? AND post_id = ?",
            (platform, post_id),
        )
        if await cursor.fetchone():
            return None
        eid = _new_id()
        await self._db.execute(
            """INSERT INTO engagement_opportunities
               (id, platform, post_id, post_url, author, text, opportunity_type, status, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, 'pending', ?)""",
            (eid, platform, post_id, post_url, author, text, opportunity_type, _now()),
        )
        await self._db.commit()
        return eid

    async def get_pending_engagements(
        self, platform: str = None, limit: int = 10
    ) -> list[dict]:
        """Get pending engagement opportunities."""
        if platform:
            cursor = await self._db.execute(
                "SELECT * FROM engagement_opportunities WHERE status = 'pending' AND platform = ? ORDER BY created_at ASC LIMIT ?",
                (platform, limit),
            )
        else:
            cursor = await self._db.execute(
                "SELECT * FROM engagement_opportunities WHERE status = 'pending' ORDER BY created_at ASC LIMIT ?",
                (limit,),
            )
        return [dict(row) for row in await cursor.fetchall()]

    async def mark_engagement_done(self, eid: str, status: str = "done"):
        await self._db.execute(
            "UPDATE engagement_opportunities SET status = ?, acted_at = ? WHERE id = ?",
            (status, _now(), eid),
        )
        await self._db.commit()
