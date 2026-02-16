# KeyJawn worker implementation plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build an autonomous marketing agent that monitors developer communities and takes 1-3 daily actions (6-9pm ET weekdays) to grow KeyJawn's profile, user count, and sales.

**Architecture:** A Python service on officejawn with two modes: a monitor loop (scans platforms for relevant conversations during business hours) and an action window (executes queued actions in the evening). Communicates with houseofjawn's Telegram bot via Redis pub/sub for approval flows. Uses Gemini CLI for content generation.

**Tech stack:** Python 3.11, aiosqlite, twikit (Twitter, no API key needed), atproto SDK (Bluesky), httpx, APScheduler, Redis pub/sub, Gemini CLI, python-telegram-bot (callback handlers on houseofjawn)

**Design doc:** `docs/plans/2026-02-16-keyjawn-worker-design.md`

---

## Prerequisites (manual — before implementation starts)

These require human action and can't be automated:

1. **Create @KeyJawn Twitter/X account** (DONE)
   - Account: @KeyJawn, email: thejawnstars@gmail.com
   - Bio: "Android keyboard built for CLI and LLM agents. AI-assisted account. Built by @jamditis"
   - Credentials stored in pass: `claude/social/twitter-keyjawn` (username\nemail\npassword)
   - Uses twikit library (no Developer API needed — authenticates with account credentials)

2. **Create KeyJawn Bluesky account**
   - Sign up at bsky.app with handle `keyjawn.bsky.social`
   - Bio: same as Twitter
   - Create an app password at bsky.app > Settings > App passwords
   - Store in pass: `pass insert claude/services/bluesky-keyjawn` (format: `handle\napp-password`)

3. **Verify Redis is running on both machines**
   - `redis-cli -a $(cat ~/.config/brain/redis.key) ping` on both houseofjawn and officejawn

---

## Task 1: Project scaffolding

**Files:**
- Create: `worker/` directory
- Create: `worker/pyproject.toml`
- Create: `worker/worker/__init__.py`
- Create: `worker/worker/config.py`
- Create: `worker/tests/__init__.py`
- Create: `worker/tests/test_config.py`

**Step 1: Create directory structure**

```bash
cd /home/jamditis/projects/keyjawn
mkdir -p worker/worker worker/tests
```

**Step 2: Write pyproject.toml**

```toml
[project]
name = "keyjawn-worker"
version = "0.1.0"
requires-python = ">=3.11"
dependencies = [
    "aiosqlite>=0.20.0",
    "httpx>=0.27.0",
    "tweepy>=4.14.0",
    "atproto>=0.0.55",
    "apscheduler>=3.10.0",
    "redis>=5.0.0",
]

[project.optional-dependencies]
dev = [
    "pytest>=8.0",
    "pytest-asyncio>=0.24.0",
]
```

**Step 3: Write config.py with environment-based configuration**

```python
"""Configuration for keyjawn-worker."""

import json
import os
import subprocess
from dataclasses import dataclass, field
from pathlib import Path


def _pass_get(key: str) -> str:
    """Read a secret from the pass store."""
    result = subprocess.run(
        ["pass", "show", key],
        capture_output=True, text=True, check=True,
    )
    return result.stdout.strip()


def _pass_get_json(key: str) -> dict:
    """Read a JSON secret from the pass store."""
    return json.loads(_pass_get(key))


@dataclass
class TwitterConfig:
    api_key: str = ""
    api_secret: str = ""
    access_token: str = ""
    access_token_secret: str = ""
    bearer_token: str = ""


@dataclass
class BlueskyConfig:
    handle: str = ""
    app_password: str = ""


@dataclass
class TelegramConfig:
    bot_token: str = ""
    chat_id: str = ""


@dataclass
class RedisConfig:
    host: str = "100.122.208.15"  # houseofjawn Tailscale IP
    port: int = 6379
    password: str = ""
    channel_prefix: str = "keyjawn-worker"


@dataclass
class Config:
    twitter: TwitterConfig = field(default_factory=TwitterConfig)
    bluesky: BlueskyConfig = field(default_factory=BlueskyConfig)
    telegram: TelegramConfig = field(default_factory=TelegramConfig)
    redis: RedisConfig = field(default_factory=RedisConfig)
    db_path: str = "worker/keyjawn-worker.db"
    monitor_interval_minutes: int = 45
    action_window_start_hour: int = 18  # 6pm ET
    action_window_end_hour: int = 21  # 9pm ET
    max_actions_per_day: int = 3
    max_posts_per_platform: int = 3
    max_escalations_per_evening: int = 5
    approval_timeout_seconds: int = 7200  # 2 hours
    max_rethinks: int = 3

    @classmethod
    def from_pass(cls) -> "Config":
        """Load configuration from the pass store."""
        twitter_data = _pass_get_json("claude/api/twitter-keyjawn")
        bsky_raw = _pass_get("claude/services/bluesky-keyjawn")
        bsky_lines = bsky_raw.split("\n")
        redis_password = Path(
            os.path.expanduser("~/.config/brain/redis.key")
        ).read_text().strip()

        return cls(
            twitter=TwitterConfig(
                api_key=twitter_data["api_key"],
                api_secret=twitter_data["api_secret"],
                access_token=twitter_data["access_token"],
                access_token_secret=twitter_data["access_token_secret"],
                bearer_token=twitter_data["bearer_token"],
            ),
            bluesky=BlueskyConfig(
                handle=bsky_lines[0],
                app_password=bsky_lines[1] if len(bsky_lines) > 1 else "",
            ),
            telegram=TelegramConfig(
                bot_token=_pass_get("claude/tokens/telegram-bot"),
                chat_id=_pass_get("claude/tokens/telegram-chat-id")
                if os.path.exists("/dev/null")  # placeholder
                else "",
            ),
            redis=RedisConfig(password=redis_password),
        )

    @classmethod
    def for_testing(cls) -> "Config":
        """Return a config suitable for tests (no external deps)."""
        return cls(db_path=":memory:")
```

**Step 4: Write test_config.py**

```python
"""Tests for configuration."""

from worker.config import Config


def test_test_config_uses_memory_db():
    config = Config.for_testing()
    assert config.db_path == ":memory:"


def test_default_schedule_values():
    config = Config.for_testing()
    assert config.action_window_start_hour == 18
    assert config.action_window_end_hour == 21
    assert config.max_actions_per_day == 3
    assert config.approval_timeout_seconds == 7200


def test_max_posts_per_platform():
    config = Config.for_testing()
    assert config.max_posts_per_platform == 3
```

**Step 5: Run tests**

```bash
cd /home/jamditis/projects/keyjawn/worker
python -m venv venv && source venv/bin/activate
pip install -e ".[dev]"
pytest tests/test_config.py -v
```

Expected: 3 tests PASS

**Step 6: Commit**

```bash
git add worker/
git commit -m "feat(worker): project scaffolding and config module"
```

---

## Task 2: Database schema

**Files:**
- Create: `worker/worker/db.py`
- Create: `worker/tests/test_db.py`

**Step 1: Write the failing test**

```python
"""Tests for database schema and operations."""

import pytest
import pytest_asyncio
import aiosqlite

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
    assert "findings" in tables
    assert "actions" in tables
    assert "calendar" in tables
    assert "metrics" in tables


@pytest.mark.asyncio
async def test_queue_finding(db):
    finding_id = await db.queue_finding(
        platform="twitter",
        source_url="https://twitter.com/user/status/123",
        source_user="devuser123",
        content="anyone know a good keyboard for SSH on mobile?",
        relevance_score=0.85,
    )
    assert finding_id is not None

    finding = await db.get_finding(finding_id)
    assert finding["platform"] == "twitter"
    assert finding["relevance_score"] == 0.85
    assert finding["status"] == "queued"


@pytest.mark.asyncio
async def test_log_action(db):
    action_id = await db.log_action(
        action_type="tweet",
        platform="twitter",
        content="KeyJawn has a permanent Esc/Tab/Ctrl row.",
        status="posted",
    )
    assert action_id is not None

    action = await db.get_action(action_id)
    assert action["action_type"] == "tweet"
    assert action["status"] == "posted"


@pytest.mark.asyncio
async def test_calendar_entry(db):
    entry_id = await db.add_calendar_entry(
        scheduled_date="2026-02-17",
        pillar="demo",
        platform="twitter",
        content_draft="Voice input demo video",
        status="planned",
    )
    assert entry_id is not None

    entries = await db.get_calendar_entries("2026-02-17")
    assert len(entries) == 1
    assert entries[0]["pillar"] == "demo"


@pytest.mark.asyncio
async def test_daily_action_count(db):
    await db.log_action(
        action_type="tweet",
        platform="twitter",
        content="post 1",
        status="posted",
    )
    await db.log_action(
        action_type="tweet",
        platform="twitter",
        content="post 2",
        status="posted",
    )
    count = await db.get_daily_action_count("twitter")
    assert count == 2

    total = await db.get_daily_action_count()
    assert total == 2


@pytest.mark.asyncio
async def test_finding_status_transitions(db):
    fid = await db.queue_finding(
        platform="bluesky",
        source_url="https://bsky.app/profile/user/post/abc",
        source_user="user.bsky.social",
        content="looking for mobile terminal keyboard",
        relevance_score=0.9,
    )
    await db.update_finding_status(fid, "approved")
    finding = await db.get_finding(fid)
    assert finding["status"] == "approved"

    await db.update_finding_status(fid, "acted")
    finding = await db.get_finding(fid)
    assert finding["status"] == "acted"
```

**Step 2: Run tests to verify they fail**

```bash
pytest tests/test_db.py -v
```

Expected: FAIL — `ModuleNotFoundError: No module named 'worker.db'`

**Step 3: Write db.py**

```python
"""SQLite database for keyjawn-worker."""

from datetime import date, datetime, timezone
from typing import Optional
from uuid import uuid4

import aiosqlite


class Database:
    def __init__(self, db_path: str):
        self.db_path = db_path
        self.conn: Optional[aiosqlite.Connection] = None

    async def init(self):
        self.conn = await aiosqlite.connect(self.db_path)
        self.conn.row_factory = aiosqlite.Row
        await self.conn.executescript(SCHEMA)
        await self.conn.commit()

    async def close(self):
        if self.conn:
            await self.conn.close()

    async def list_tables(self) -> list[str]:
        cursor = await self.conn.execute(
            "SELECT name FROM sqlite_master WHERE type='table'"
        )
        rows = await cursor.fetchall()
        return [r["name"] for r in rows]

    async def queue_finding(
        self,
        platform: str,
        source_url: str,
        source_user: str,
        content: str,
        relevance_score: float,
    ) -> str:
        finding_id = uuid4().hex[:12]
        await self.conn.execute(
            """INSERT INTO findings
               (id, platform, source_url, source_user, content,
                relevance_score, status, found_at)
               VALUES (?, ?, ?, ?, ?, ?, 'queued', ?)""",
            (finding_id, platform, source_url, source_user, content,
             relevance_score, _now()),
        )
        await self.conn.commit()
        return finding_id

    async def get_finding(self, finding_id: str) -> Optional[dict]:
        cursor = await self.conn.execute(
            "SELECT * FROM findings WHERE id = ?", (finding_id,)
        )
        row = await cursor.fetchone()
        return dict(row) if row else None

    async def update_finding_status(self, finding_id: str, status: str):
        await self.conn.execute(
            "UPDATE findings SET status = ? WHERE id = ?",
            (status, finding_id),
        )
        await self.conn.commit()

    async def get_queued_findings(
        self, limit: int = 10
    ) -> list[dict]:
        cursor = await self.conn.execute(
            """SELECT * FROM findings
               WHERE status = 'queued'
               ORDER BY relevance_score DESC, found_at ASC
               LIMIT ?""",
            (limit,),
        )
        rows = await cursor.fetchall()
        return [dict(r) for r in rows]

    async def log_action(
        self,
        action_type: str,
        platform: str,
        content: str,
        status: str,
        finding_id: Optional[str] = None,
        post_url: Optional[str] = None,
    ) -> str:
        action_id = uuid4().hex[:12]
        await self.conn.execute(
            """INSERT INTO actions
               (id, action_type, platform, content, status,
                finding_id, post_url, acted_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
            (action_id, action_type, platform, content, status,
             finding_id, post_url, _now()),
        )
        await self.conn.commit()
        return action_id

    async def get_action(self, action_id: str) -> Optional[dict]:
        cursor = await self.conn.execute(
            "SELECT * FROM actions WHERE id = ?", (action_id,)
        )
        row = await cursor.fetchone()
        return dict(row) if row else None

    async def get_daily_action_count(
        self, platform: Optional[str] = None
    ) -> int:
        today = date.today().isoformat()
        if platform:
            cursor = await self.conn.execute(
                """SELECT COUNT(*) as cnt FROM actions
                   WHERE platform = ? AND status = 'posted'
                   AND date(acted_at) = ?""",
                (platform, today),
            )
        else:
            cursor = await self.conn.execute(
                """SELECT COUNT(*) as cnt FROM actions
                   WHERE status = 'posted' AND date(acted_at) = ?""",
                (today,),
            )
        row = await cursor.fetchone()
        return row["cnt"]

    async def add_calendar_entry(
        self,
        scheduled_date: str,
        pillar: str,
        platform: str,
        content_draft: str,
        status: str = "planned",
    ) -> str:
        entry_id = uuid4().hex[:12]
        await self.conn.execute(
            """INSERT INTO calendar
               (id, scheduled_date, pillar, platform,
                content_draft, status)
               VALUES (?, ?, ?, ?, ?, ?)""",
            (entry_id, scheduled_date, pillar, platform,
             content_draft, status),
        )
        await self.conn.commit()
        return entry_id

    async def get_calendar_entries(
        self, date_str: str
    ) -> list[dict]:
        cursor = await self.conn.execute(
            """SELECT * FROM calendar
               WHERE scheduled_date = ?
               ORDER BY rowid""",
            (date_str,),
        )
        rows = await cursor.fetchall()
        return [dict(r) for r in rows]

    async def record_metric(
        self,
        platform: str,
        metric_type: str,
        value: float,
    ):
        await self.conn.execute(
            """INSERT INTO metrics
               (platform, metric_type, value, recorded_at)
               VALUES (?, ?, ?, ?)""",
            (platform, metric_type, value, _now()),
        )
        await self.conn.commit()


def _now() -> str:
    return datetime.now(timezone.utc).isoformat()


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
CREATE INDEX IF NOT EXISTS idx_actions_date ON actions(date(acted_at));
CREATE INDEX IF NOT EXISTS idx_calendar_date ON calendar(scheduled_date);
"""
```

**Step 4: Run tests**

```bash
pytest tests/test_db.py -v
```

Expected: All 6 tests PASS

**Step 5: Commit**

```bash
git add worker/worker/db.py worker/tests/test_db.py
git commit -m "feat(worker): database schema and operations"
```

---

## Task 3: Telegram approval flow — bot-side callback handlers

**Files:**
- Modify: `/home/jamditis/projects/houseofjawn-bot/bot.py` (add callback handler for `kw_` prefix)
- Create: `worker/worker/telegram.py`
- Create: `worker/tests/test_telegram.py`

This task adds the callback handler to the existing Telegram bot on houseofjawn. When Joe taps a button, the bot publishes the decision to Redis on the `keyjawn-worker:decisions` channel.

**Step 1: Write test for the message formatter**

```python
"""Tests for Telegram message formatting and approval protocol."""

from worker.telegram import format_escalation_message, parse_callback_data


def test_format_escalation_message():
    msg = format_escalation_message(
        action_id="abc123",
        action_type="Reply to conversation",
        platform="Twitter/X",
        context='@devuser asked "good keyboard for SSH?"',
        draft="KeyJawn was built for this.",
    )
    assert "[KeyJawn worker]" in msg
    assert "Reply to conversation" in msg
    assert "Twitter/X" in msg
    assert "KeyJawn was built for this." in msg


def test_format_escalation_without_context():
    msg = format_escalation_message(
        action_id="abc123",
        action_type="Original post",
        platform="Bluesky",
        context=None,
        draft="Voice input demo.",
    )
    assert "Context:" not in msg
    assert "Voice input demo." in msg


def test_parse_callback_data():
    data = parse_callback_data("kw:approve:abc123")
    assert data == {"action": "approve", "action_id": "abc123"}


def test_parse_callback_data_rethink():
    data = parse_callback_data("kw:rethink:xyz789")
    assert data == {"action": "rethink", "action_id": "xyz789"}
```

**Step 2: Run to verify failure**

```bash
pytest tests/test_telegram.py -v
```

Expected: FAIL — no module `worker.telegram`

**Step 3: Write telegram.py**

```python
"""Telegram messaging and approval protocol for keyjawn-worker."""

from typing import Optional


def format_escalation_message(
    action_id: str,
    action_type: str,
    platform: str,
    context: Optional[str],
    draft: str,
) -> str:
    """Format an escalation message for Telegram."""
    lines = [
        "<b>[KeyJawn worker] Action ready</b>",
        "",
        f"<b>Type:</b> {action_type}",
        f"<b>Platform:</b> {platform}",
    ]
    if context:
        lines.append(f"<b>Context:</b> {_escape_html(context)}")
    lines.extend([
        "",
        "<b>Draft:</b>",
        f"<pre>{_escape_html(draft)}</pre>",
    ])
    return "\n".join(lines)


def build_approval_keyboard(action_id: str) -> list[list[dict]]:
    """Build inline keyboard buttons for approval flow.

    Returns a list of button rows, each row is a list of
    {text, callback_data} dicts. The caller converts these
    to InlineKeyboardButton objects (keeps this module free
    of telegram SDK dependency for testing).
    """
    return [[
        {"text": "Approve", "callback_data": f"kw:approve:{action_id}"},
        {"text": "Deny", "callback_data": f"kw:deny:{action_id}"},
        {"text": "Backlog", "callback_data": f"kw:backlog:{action_id}"},
        {"text": "Rethink", "callback_data": f"kw:rethink:{action_id}"},
    ]]


def parse_callback_data(data: str) -> dict:
    """Parse a kw: callback data string."""
    parts = data.split(":")
    return {
        "action": parts[1],
        "action_id": parts[2],
    }


def _escape_html(text: str) -> str:
    """Escape HTML special characters for Telegram."""
    return (
        text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    )
```

**Step 4: Run tests**

```bash
pytest tests/test_telegram.py -v
```

Expected: All 4 tests PASS

**Step 5: Write the bot-side callback handler**

Add to `houseofjawn-bot/bot.py`. This handler receives button taps and publishes decisions to Redis.

Find the callback handler registration section (around line 7005) and add:

```python
# --- KeyJawn worker approval callbacks ---

async def keyjawn_worker_callback(update: Update, context: ContextTypes.DEFAULT_TYPE):
    """Handle KeyJawn worker approval button taps."""
    query = update.callback_query
    await query.answer()

    data = query.data  # e.g., "kw:approve:abc123"
    parts = data.split(":")
    if len(parts) != 3:
        return

    action = parts[1]   # approve, deny, backlog, rethink
    action_id = parts[2]

    # Publish decision to Redis for officejawn worker
    try:
        decision = json.dumps({
            "action_id": action_id,
            "decision": action,
            "timestamp": datetime.now(timezone.utc).isoformat(),
        })
        redis_password = Path(os.path.expanduser(
            "~/.config/brain/redis.key"
        )).read_text().strip()
        r = redis.Redis(
            host="localhost", port=6379,
            password=redis_password, decode_responses=True,
        )
        r.publish("keyjawn-worker:decisions", decision)
        r.close()
    except Exception as e:
        logger.error(f"redis publish failed: {e}")

    # Update the message to show the decision
    action_labels = {
        "approve": "Approved",
        "deny": "Denied",
        "backlog": "Backlogged",
        "rethink": "Rethinking...",
    }
    label = action_labels.get(action, action)
    await query.edit_message_reply_markup(reply_markup=None)
    await query.message.reply_text(
        f"KeyJawn worker: <b>{label}</b>",
        parse_mode="HTML",
    )
```

Add the handler registration near the other `CallbackQueryHandler` entries:

```python
application.add_handler(CallbackQueryHandler(keyjawn_worker_callback, pattern=r"^kw:"))
```

Add `import redis` to the bot's imports if not already present.

**Step 6: Commit**

```bash
cd /home/jamditis/projects/keyjawn
git add worker/worker/telegram.py worker/tests/test_telegram.py
git commit -m "feat(worker): telegram message formatting and approval protocol"

cd /home/jamditis/projects/houseofjawn-bot
git add bot.py
git commit -m "feat: add keyjawn worker approval callback handler"
```

---

## Task 4: Redis approval listener (worker side)

**Files:**
- Create: `worker/worker/approvals.py`
- Create: `worker/tests/test_approvals.py`

The worker sends a Telegram message with buttons, then subscribes to Redis waiting for Joe's decision.

**Step 1: Write the failing test**

```python
"""Tests for the approval listener."""

import asyncio
import json
import pytest
import pytest_asyncio

from worker.approvals import ApprovalManager
from worker.config import Config
from worker.db import Database


@pytest_asyncio.fixture
async def db():
    database = Database(":memory:")
    await database.init()
    yield database
    await database.close()


@pytest.mark.asyncio
async def test_process_decision_approve(db):
    manager = ApprovalManager(config=Config.for_testing(), db=db)

    action_id = await db.log_action(
        action_type="tweet",
        platform="twitter",
        content="test post",
        status="pending_approval",
    )

    decision_msg = json.dumps({
        "action_id": action_id,
        "decision": "approve",
        "timestamp": "2026-02-16T20:00:00+00:00",
    })
    result = await manager.process_decision(decision_msg)

    assert result == "approve"
    action = await db.get_action(action_id)
    assert action["approval_decision"] == "approve"


@pytest.mark.asyncio
async def test_process_decision_deny(db):
    manager = ApprovalManager(config=Config.for_testing(), db=db)

    action_id = await db.log_action(
        action_type="reply",
        platform="twitter",
        content="test reply",
        status="pending_approval",
    )

    decision_msg = json.dumps({
        "action_id": action_id,
        "decision": "deny",
        "timestamp": "2026-02-16T20:00:00+00:00",
    })
    result = await manager.process_decision(decision_msg)

    assert result == "deny"
    action = await db.get_action(action_id)
    assert action["approval_decision"] == "deny"
    assert action["status"] == "denied"


@pytest.mark.asyncio
async def test_process_decision_backlog(db):
    manager = ApprovalManager(config=Config.for_testing(), db=db)

    action_id = await db.log_action(
        action_type="reply",
        platform="bluesky",
        content="test",
        status="pending_approval",
    )

    decision_msg = json.dumps({
        "action_id": action_id,
        "decision": "backlog",
        "timestamp": "2026-02-16T20:00:00+00:00",
    })
    result = await manager.process_decision(decision_msg)

    assert result == "backlog"
    action = await db.get_action(action_id)
    assert action["status"] == "backlogged"
```

**Step 2: Run to verify failure**

```bash
pytest tests/test_approvals.py -v
```

Expected: FAIL — no module `worker.approvals`

**Step 3: Write approvals.py**

```python
"""Approval flow manager for keyjawn-worker.

Sends Telegram escalation messages and listens for decisions
via Redis pub/sub.
"""

import asyncio
import json
import logging
from typing import Optional

import httpx

from worker.config import Config
from worker.db import Database
from worker.telegram import (
    build_approval_keyboard,
    format_escalation_message,
)

logger = logging.getLogger(__name__)

TELEGRAM_API = "https://api.telegram.org/bot{token}"


class ApprovalManager:
    def __init__(self, config: Config, db: Database):
        self.config = config
        self.db = db
        self._pending: dict[str, asyncio.Future] = {}

    async def request_approval(
        self,
        action_id: str,
        action_type: str,
        platform: str,
        draft: str,
        context: Optional[str] = None,
    ) -> str:
        """Send escalation to Telegram and wait for decision.

        Returns: 'approve', 'deny', 'backlog', or 'rethink'.
        Returns 'backlog' on timeout.
        """
        message = format_escalation_message(
            action_id=action_id,
            action_type=action_type,
            platform=platform,
            context=context,
            draft=draft,
        )
        keyboard = build_approval_keyboard(action_id)

        await self._send_telegram(message, keyboard)

        # Wait for decision via Redis (or timeout)
        future: asyncio.Future = asyncio.get_event_loop().create_future()
        self._pending[action_id] = future

        try:
            decision = await asyncio.wait_for(
                future,
                timeout=self.config.approval_timeout_seconds,
            )
        except asyncio.TimeoutError:
            logger.info(f"approval timeout for {action_id}, backlogging")
            decision = "backlog"
            await self.db.conn.execute(
                """UPDATE actions SET status = 'backlogged',
                   approval_decision = 'timeout'
                   WHERE id = ?""",
                (action_id,),
            )
            await self.db.conn.commit()
        finally:
            self._pending.pop(action_id, None)

        return decision

    async def process_decision(self, message: str) -> str:
        """Process a decision from Redis pub/sub.

        Called by the Redis listener when a decision arrives.
        Updates the DB and resolves the pending future.
        """
        data = json.loads(message)
        action_id = data["action_id"]
        decision = data["decision"]
        timestamp = data.get("timestamp", "")

        # Update DB
        status_map = {
            "approve": "approved",
            "deny": "denied",
            "backlog": "backlogged",
            "rethink": "pending_rethink",
        }
        new_status = status_map.get(decision, decision)

        await self.db.conn.execute(
            """UPDATE actions SET approval_decision = ?,
               approval_timestamp = ?, status = ?
               WHERE id = ?""",
            (decision, timestamp, new_status, action_id),
        )
        await self.db.conn.commit()

        # Resolve the pending future if one exists
        future = self._pending.get(action_id)
        if future and not future.done():
            future.set_result(decision)

        return decision

    async def _send_telegram(
        self, text: str, keyboard: list[list[dict]]
    ):
        """Send a message with inline keyboard via Telegram Bot API."""
        token = self.config.telegram.bot_token
        chat_id = self.config.telegram.chat_id

        if not token or not chat_id:
            logger.warning("telegram not configured, skipping send")
            return

        url = f"{TELEGRAM_API.format(token=token)}/sendMessage"

        # Convert keyboard dicts to Telegram format
        inline_keyboard = [
            [{"text": btn["text"], "callback_data": btn["callback_data"]}
             for btn in row]
            for row in keyboard
        ]

        payload = {
            "chat_id": chat_id,
            "text": text,
            "parse_mode": "HTML",
            "reply_markup": {"inline_keyboard": inline_keyboard},
        }

        async with httpx.AsyncClient(timeout=30) as client:
            resp = await client.post(url, json=payload)
            if resp.status_code != 200:
                logger.error(f"telegram send failed: {resp.text}")
```

**Step 4: Run tests**

```bash
pytest tests/test_approvals.py -v
```

Expected: All 3 tests PASS

**Step 5: Commit**

```bash
git add worker/worker/approvals.py worker/tests/test_approvals.py
git commit -m "feat(worker): approval flow manager with Redis listener"
```

---

## Task 5: Twitter/X platform client

**Files:**
- Create: `worker/worker/platforms/__init__.py`
- Create: `worker/worker/platforms/twitter.py`
- Create: `worker/tests/test_platforms.py`

**Step 1: Write the failing test**

```python
"""Tests for platform clients."""

import pytest
from unittest.mock import AsyncMock, MagicMock, patch

from worker.platforms.twitter import TwitterClient
from worker.config import TwitterConfig


@pytest.fixture
def twitter_config():
    return TwitterConfig(
        api_key="test_key",
        api_secret="test_secret",
        access_token="test_token",
        access_token_secret="test_token_secret",
        bearer_token="test_bearer",
    )


def test_twitter_client_init(twitter_config):
    client = TwitterClient(twitter_config)
    assert client.config == twitter_config


def test_twitter_search_query_builder(twitter_config):
    client = TwitterClient(twitter_config)
    query = client.build_search_query([
        "mobile SSH keyboard",
        "CLI keyboard android",
        "Claude Code phone",
    ])
    assert "mobile SSH keyboard" in query
    assert "CLI keyboard android" in query
    # Should exclude own tweets
    assert "-from:KeyJawn" in query


def test_twitter_post_too_long(twitter_config):
    client = TwitterClient(twitter_config)
    long_text = "x" * 281
    with pytest.raises(ValueError, match="exceeds 280"):
        client.validate_post(long_text)


def test_twitter_post_valid(twitter_config):
    client = TwitterClient(twitter_config)
    client.validate_post("Short post")  # should not raise
```

**Step 2: Run to verify failure**

```bash
pytest tests/test_platforms.py -v
```

Expected: FAIL

**Step 3: Write twitter.py**

```python
"""Twitter/X platform client."""

import logging
from typing import Optional

import tweepy

from worker.config import TwitterConfig

logger = logging.getLogger(__name__)

# Keywords the monitor scans for
SEARCH_KEYWORDS = [
    "mobile SSH keyboard",
    "CLI keyboard android",
    "terminal keyboard phone",
    "Claude Code mobile",
    "Claude Code phone",
    "SSH from phone",
    "Gemini CLI mobile",
    "aichat mobile keyboard",
]


class TwitterClient:
    def __init__(self, config: TwitterConfig):
        self.config = config
        self._client: Optional[tweepy.Client] = None

    @property
    def client(self) -> tweepy.Client:
        if self._client is None:
            self._client = tweepy.Client(
                bearer_token=self.config.bearer_token,
                consumer_key=self.config.api_key,
                consumer_secret=self.config.api_secret,
                access_token=self.config.access_token,
                access_token_secret=self.config.access_token_secret,
            )
        return self._client

    def build_search_query(self, terms: list[str]) -> str:
        """Build a Twitter search query from keyword list."""
        joined = " OR ".join(f'"{t}"' for t in terms)
        return f"({joined}) -from:KeyJawn -is:retweet lang:en"

    def validate_post(self, text: str):
        """Validate a post before sending."""
        if len(text) > 280:
            raise ValueError(f"post length {len(text)} exceeds 280")

    async def search(
        self, query: Optional[str] = None, max_results: int = 20
    ) -> list[dict]:
        """Search recent tweets matching keywords."""
        if query is None:
            query = self.build_search_query(SEARCH_KEYWORDS)

        try:
            response = self.client.search_recent_tweets(
                query=query,
                max_results=max_results,
                tweet_fields=["author_id", "created_at", "text"],
                user_fields=["username"],
                expansions=["author_id"],
            )
        except tweepy.TweepyException as e:
            logger.error(f"twitter search failed: {e}")
            return []

        if not response.data:
            return []

        # Map author IDs to usernames
        users = {}
        if response.includes and "users" in response.includes:
            users = {u.id: u.username for u in response.includes["users"]}

        results = []
        for tweet in response.data:
            results.append({
                "id": str(tweet.id),
                "text": tweet.text,
                "author": users.get(tweet.author_id, "unknown"),
                "url": f"https://twitter.com/i/status/{tweet.id}",
                "created_at": str(tweet.created_at),
            })
        return results

    async def post(self, text: str) -> Optional[str]:
        """Post a tweet. Returns the tweet URL or None on failure."""
        self.validate_post(text)
        try:
            response = self.client.create_tweet(text=text)
            tweet_id = response.data["id"]
            url = f"https://twitter.com/KeyJawn/status/{tweet_id}"
            logger.info(f"posted tweet: {url}")
            return url
        except tweepy.TweepyException as e:
            logger.error(f"tweet failed: {e}")
            return None

    async def reply(self, text: str, in_reply_to: str) -> Optional[str]:
        """Reply to a tweet. Returns the reply URL or None."""
        self.validate_post(text)
        try:
            response = self.client.create_tweet(
                text=text, in_reply_to_tweet_id=in_reply_to,
            )
            tweet_id = response.data["id"]
            url = f"https://twitter.com/KeyJawn/status/{tweet_id}"
            logger.info(f"posted reply: {url}")
            return url
        except tweepy.TweepyException as e:
            logger.error(f"reply failed: {e}")
            return None

    async def like(self, tweet_id: str) -> bool:
        """Like a tweet."""
        try:
            self.client.like(tweet_id)
            return True
        except tweepy.TweepyException as e:
            logger.error(f"like failed: {e}")
            return False

    async def retweet(self, tweet_id: str) -> bool:
        """Retweet a tweet."""
        try:
            self.client.retweet(tweet_id)
            return True
        except tweepy.TweepyException as e:
            logger.error(f"retweet failed: {e}")
            return False
```

**Step 4: Run tests**

```bash
pytest tests/test_platforms.py -v
```

Expected: All 4 tests PASS

**Step 5: Commit**

```bash
git add worker/worker/platforms/
git commit -m "feat(worker): twitter platform client with search and post"
```

---

## Task 6: Bluesky platform client

**Files:**
- Create: `worker/worker/platforms/bluesky.py`
- Modify: `worker/tests/test_platforms.py` (add Bluesky tests)

**Step 1: Add failing tests to test_platforms.py**

```python
from worker.platforms.bluesky import BlueskyClient
from worker.config import BlueskyConfig


@pytest.fixture
def bluesky_config():
    return BlueskyConfig(
        handle="keyjawn.bsky.social",
        app_password="test-app-password",
    )


def test_bluesky_client_init(bluesky_config):
    client = BlueskyClient(bluesky_config)
    assert client.config == bluesky_config


def test_bluesky_post_too_long(bluesky_config):
    client = BlueskyClient(bluesky_config)
    long_text = "x" * 301
    with pytest.raises(ValueError, match="exceeds 300"):
        client.validate_post(long_text)


def test_bluesky_search_terms():
    from worker.platforms.bluesky import SEARCH_KEYWORDS
    assert len(SEARCH_KEYWORDS) > 0
    assert any("SSH" in kw or "CLI" in kw for kw in SEARCH_KEYWORDS)
```

**Step 2: Run to verify failure**

```bash
pytest tests/test_platforms.py -v -k bluesky
```

Expected: FAIL

**Step 3: Write bluesky.py**

```python
"""Bluesky platform client using the AT Protocol."""

import logging
from typing import Optional

from atproto import Client

from worker.config import BlueskyConfig

logger = logging.getLogger(__name__)

SEARCH_KEYWORDS = [
    "mobile SSH keyboard",
    "CLI keyboard android",
    "terminal keyboard phone",
    "Claude Code mobile",
    "SSH from phone",
]


class BlueskyClient:
    def __init__(self, config: BlueskyConfig):
        self.config = config
        self._client: Optional[Client] = None

    @property
    def client(self) -> Client:
        if self._client is None:
            self._client = Client()
            self._client.login(
                self.config.handle, self.config.app_password
            )
        return self._client

    def validate_post(self, text: str):
        """Validate a post before sending."""
        if len(text) > 300:
            raise ValueError(f"post length {len(text)} exceeds 300")

    async def search(self, query: str, limit: int = 20) -> list[dict]:
        """Search Bluesky posts."""
        try:
            response = self.client.app.bsky.feed.search_posts(
                {"q": query, "limit": limit}
            )
            results = []
            for post in response.posts:
                results.append({
                    "uri": post.uri,
                    "text": post.record.text,
                    "author": post.author.handle,
                    "url": _post_url(post.author.handle, post.uri),
                    "created_at": post.record.created_at,
                })
            return results
        except Exception as e:
            logger.error(f"bluesky search failed: {e}")
            return []

    async def post(self, text: str) -> Optional[str]:
        """Create a Bluesky post. Returns the post URL or None."""
        self.validate_post(text)
        try:
            response = self.client.send_post(text=text)
            url = _post_url(self.config.handle, response.uri)
            logger.info(f"posted to bluesky: {url}")
            return url
        except Exception as e:
            logger.error(f"bluesky post failed: {e}")
            return None

    async def reply(
        self, text: str, parent_uri: str, parent_cid: str,
        root_uri: Optional[str] = None,
        root_cid: Optional[str] = None,
    ) -> Optional[str]:
        """Reply to a Bluesky post."""
        self.validate_post(text)
        try:
            from atproto import models
            parent_ref = models.create_strong_ref(parent_uri, parent_cid)
            root_ref = (
                models.create_strong_ref(root_uri, root_cid)
                if root_uri and root_cid
                else parent_ref
            )
            response = self.client.send_post(
                text=text,
                reply_to=models.AppBskyFeedPost.ReplyRef(
                    parent=parent_ref, root=root_ref,
                ),
            )
            url = _post_url(self.config.handle, response.uri)
            logger.info(f"replied on bluesky: {url}")
            return url
        except Exception as e:
            logger.error(f"bluesky reply failed: {e}")
            return None

    async def like(self, uri: str, cid: str) -> bool:
        """Like a Bluesky post."""
        try:
            self.client.like(uri, cid)
            return True
        except Exception as e:
            logger.error(f"bluesky like failed: {e}")
            return False


def _post_url(handle: str, uri: str) -> str:
    """Convert an AT URI to a bsky.app URL."""
    # uri format: at://did:plc:xxx/app.bsky.feed.post/yyy
    parts = uri.split("/")
    rkey = parts[-1]
    return f"https://bsky.app/profile/{handle}/post/{rkey}"
```

**Step 4: Run tests**

```bash
pytest tests/test_platforms.py -v
```

Expected: All 7 tests PASS (4 Twitter + 3 Bluesky)

**Step 5: Commit**

```bash
git add worker/worker/platforms/bluesky.py worker/tests/test_platforms.py
git commit -m "feat(worker): bluesky platform client with AT Protocol"
```

---

## Task 7: Monitor loop

**Files:**
- Create: `worker/worker/monitor.py`
- Create: `worker/tests/test_monitor.py`

**Step 1: Write failing tests**

```python
"""Tests for the monitor loop."""

import pytest
import pytest_asyncio
from unittest.mock import AsyncMock, patch

from worker.monitor import Monitor
from worker.config import Config
from worker.db import Database


@pytest_asyncio.fixture
async def db():
    database = Database(":memory:")
    await database.init()
    yield database
    await database.close()


@pytest.mark.asyncio
async def test_score_finding_high_relevance():
    monitor = Monitor(config=Config.for_testing(), db=None)
    score = monitor.score_relevance(
        "anyone know a good keyboard for SSH on my phone?",
        "twitter",
    )
    assert score > 0.7


@pytest.mark.asyncio
async def test_score_finding_low_relevance():
    monitor = Monitor(config=Config.for_testing(), db=None)
    score = monitor.score_relevance(
        "just got a new mechanical keyboard for my desk",
        "twitter",
    )
    assert score < 0.3


@pytest.mark.asyncio
async def test_score_finding_medium_relevance():
    monitor = Monitor(config=Config.for_testing(), db=None)
    score = monitor.score_relevance(
        "using Claude Code is great but typing on mobile is painful",
        "twitter",
    )
    assert score > 0.4


@pytest.mark.asyncio
async def test_dedup_findings(db):
    monitor = Monitor(config=Config.for_testing(), db=db)

    findings = [
        {"url": "https://twitter.com/status/123", "text": "test",
         "author": "user1", "platform": "twitter"},
        {"url": "https://twitter.com/status/123", "text": "test",
         "author": "user1", "platform": "twitter"},  # duplicate
    ]
    queued = await monitor.queue_new_findings(findings)
    assert queued == 1  # second one deduped
```

**Step 2: Run to verify failure**

```bash
pytest tests/test_monitor.py -v
```

Expected: FAIL

**Step 3: Write monitor.py**

```python
"""Monitor loop — scans platforms for relevant conversations."""

import logging
import re
from typing import Optional

from worker.config import Config
from worker.db import Database

logger = logging.getLogger(__name__)

# High-signal phrases (exact or close match = high score)
HIGH_SIGNAL = [
    "keyboard for ssh",
    "keyboard for cli",
    "keyboard for terminal",
    "ssh on my phone",
    "ssh from phone",
    "ssh from mobile",
    "terminal on phone",
    "claude code mobile",
    "claude code phone",
    "gemini cli mobile",
    "mobile cli keyboard",
    "android terminal keyboard",
    "keyjawn",
]

# Medium-signal phrases
MEDIUM_SIGNAL = [
    "mobile ssh",
    "phone ssh",
    "typing on mobile",
    "mobile coding",
    "phone terminal",
    "shell on phone",
    "ctrl key mobile",
    "escape key phone",
    "tab key android",
]

# Context that boosts relevance
BOOSTERS = [
    "android",
    "mobile",
    "phone",
    "keyboard",
    "typing",
]


class Monitor:
    def __init__(self, config: Config, db: Optional[Database]):
        self.config = config
        self.db = db

    def score_relevance(self, text: str, platform: str) -> float:
        """Score how relevant a piece of content is to KeyJawn.

        Returns a float from 0.0 to 1.0.
        """
        text_lower = text.lower()
        score = 0.0

        # High-signal matches
        for phrase in HIGH_SIGNAL:
            if phrase in text_lower:
                score = max(score, 0.8)
                break

        # Medium-signal matches
        if score < 0.5:
            for phrase in MEDIUM_SIGNAL:
                if phrase in text_lower:
                    score = max(score, 0.5)
                    break

        # Booster check — multiple relevant terms present
        if score < 0.5:
            booster_count = sum(
                1 for b in BOOSTERS if b in text_lower
            )
            if booster_count >= 2:
                score = max(score, 0.4)

        # Question mark bonus — people asking questions are
        # higher-value targets for helpful replies
        if "?" in text and score > 0.3:
            score = min(score + 0.1, 1.0)

        return score

    async def queue_new_findings(
        self, findings: list[dict]
    ) -> int:
        """Queue findings, deduplicating by URL. Returns count queued."""
        queued = 0
        for finding in findings:
            # Check if already in DB
            cursor = await self.db.conn.execute(
                "SELECT id FROM findings WHERE source_url = ?",
                (finding["url"],),
            )
            if await cursor.fetchone():
                continue

            score = self.score_relevance(
                finding["text"], finding["platform"]
            )
            if score < 0.3:
                continue  # below threshold

            await self.db.queue_finding(
                platform=finding["platform"],
                source_url=finding["url"],
                source_user=finding["author"],
                content=finding["text"],
                relevance_score=score,
            )
            queued += 1

        return queued

    async def scan_all_platforms(self, twitter_client, bluesky_client):
        """Run a full scan across all platforms."""
        findings = []

        # Twitter
        try:
            tweets = await twitter_client.search()
            for t in tweets:
                findings.append({
                    "url": t["url"],
                    "text": t["text"],
                    "author": t["author"],
                    "platform": "twitter",
                })
        except Exception as e:
            logger.error(f"twitter scan failed: {e}")

        # Bluesky
        from worker.platforms.bluesky import SEARCH_KEYWORDS as BSKY_KW
        for kw in BSKY_KW[:3]:  # limit queries per scan
            try:
                posts = await bluesky_client.search(kw, limit=10)
                for p in posts:
                    findings.append({
                        "url": p["url"],
                        "text": p["text"],
                        "author": p["author"],
                        "platform": "bluesky",
                    })
            except Exception as e:
                logger.error(f"bluesky scan for '{kw}' failed: {e}")

        queued = await self.queue_new_findings(findings)
        logger.info(
            f"scan complete: {len(findings)} found, {queued} queued"
        )
        return queued
```

**Step 4: Run tests**

```bash
pytest tests/test_monitor.py -v
```

Expected: All 4 tests PASS

**Step 5: Commit**

```bash
git add worker/worker/monitor.py worker/tests/test_monitor.py
git commit -m "feat(worker): monitor loop with relevance scoring"
```

---

## Task 8: Content generator (Gemini CLI wrapper)

**Files:**
- Create: `worker/worker/content.py`
- Create: `worker/tests/test_content.py`

The worker uses Gemini CLI (free, 1500 req/day on AI Pro) to draft posts.

**Step 1: Write failing tests**

```python
"""Tests for content generation."""

import pytest

from worker.content import (
    build_generation_prompt,
    validate_generated_content,
    ContentRequest,
)


def test_build_prompt_original_post():
    req = ContentRequest(
        pillar="demo",
        platform="twitter",
        topic="voice input for CLI prompts",
    )
    prompt = build_generation_prompt(req)
    assert "twitter" in prompt.lower()
    assert "280" in prompt  # character limit
    assert "voice input" in prompt
    assert "demo" in prompt


def test_build_prompt_reply():
    req = ContentRequest(
        pillar="engagement",
        platform="bluesky",
        topic="reply to someone asking about mobile SSH",
        context="@user said: anyone know how to SSH from an android phone?",
    )
    prompt = build_generation_prompt(req)
    assert "300" in prompt  # Bluesky limit
    assert "reply" in prompt.lower()
    assert "@user" in prompt


def test_validate_content_too_long():
    errors = validate_generated_content(
        "x" * 281, platform="twitter"
    )
    assert any("length" in e.lower() for e in errors)


def test_validate_content_has_banned_words():
    errors = validate_generated_content(
        "This innovative keyboard seamlessly integrates with your workflow!",
        platform="twitter",
    )
    assert len(errors) > 0
    assert any("innovative" in e.lower() or "seamlessly" in e.lower()
               for e in errors)


def test_validate_content_has_hashtag_spam():
    errors = validate_generated_content(
        "Check out KeyJawn! #dev #coding #AI #keyboard #CLI",
        platform="twitter",
    )
    assert any("hashtag" in e.lower() for e in errors)


def test_validate_clean_content():
    errors = validate_generated_content(
        "KeyJawn has a permanent Esc/Tab/Ctrl row. No long-pressing.",
        platform="twitter",
    )
    assert len(errors) == 0
```

**Step 2: Run to verify failure**

```bash
pytest tests/test_content.py -v
```

Expected: FAIL

**Step 3: Write content.py**

```python
"""Content generation using Gemini CLI."""

import logging
import re
import subprocess
from dataclasses import dataclass
from typing import Optional

logger = logging.getLogger(__name__)

BANNED_WORDS = [
    "comprehensive", "sophisticated", "robust", "transformative",
    "leveraging", "seamlessly", "innovative", "cutting-edge",
    "state-of-the-art", "holistic", "synergy", "ecosystem",
    "paradigm", "empower", "game-changer", "revolutionary",
    "thrilled", "excited to announce",
]

BANNED_OPENERS = [
    "ever struggled",
    "imagine a world",
    "if you're like me",
    "are you tired",
    "check out",
    "don't miss",
]

PLATFORM_LIMITS = {
    "twitter": 280,
    "bluesky": 300,
    "reddit": 10000,
    "hn": 10000,
    "devto": 50000,
}


@dataclass
class ContentRequest:
    pillar: str  # demo, awareness, engagement, social_proof, bts
    platform: str
    topic: str
    context: Optional[str] = None


def build_generation_prompt(req: ContentRequest) -> str:
    """Build a prompt for Gemini CLI to generate content."""
    limit = PLATFORM_LIMITS.get(req.platform, 280)

    prompt_parts = [
        "Write a short social media post for the KeyJawn keyboard.",
        f"Platform: {req.platform} (max {limit} characters).",
        f"Content pillar: {req.pillar}.",
        f"Topic: {req.topic}.",
        "",
        "About KeyJawn: Android keyboard for CLI/LLM agents.",
        "Features: permanent Esc/Tab/Ctrl/arrow row, voice input,",
        "SCP image upload, slash commands. $4 one-time, free lite version.",
        "Website: keyjawn.amditis.tech",
        "",
        "Writing rules:",
        "- Write like a developer talking to another developer",
        "- Short sentences, contractions, casual tone",
        "- Max 2 sentences before getting to the point",
        "- No rhetorical questions as openers",
        "- No hashtags (or one max)",
        "- No exclamation marks",
        "- No emoji strings (one max, only if it adds meaning)",
        "- No hype words: innovative, revolutionary, game-changer, etc.",
        "- No filler: comprehensive, robust, seamless, leveraging, etc.",
        "- No fake emotion: excited, thrilled, etc.",
        "- Never trash competitors, only highlight what KeyJawn does",
        "- Links go at the end",
        "",
        f"Output ONLY the post text, nothing else. Max {limit} characters.",
    ]

    if req.context:
        prompt_parts.insert(4, f"Context (replying to): {req.context}")
        prompt_parts.insert(5, "Write a helpful reply, not a sales pitch.")

    return "\n".join(prompt_parts)


def validate_generated_content(
    text: str, platform: str
) -> list[str]:
    """Check generated content against writing rules.

    Returns a list of violations (empty = clean).
    """
    errors = []
    limit = PLATFORM_LIMITS.get(platform, 280)
    text_lower = text.lower()

    # Length check
    if len(text) > limit:
        errors.append(
            f"length {len(text)} exceeds {limit} for {platform}"
        )

    # Banned words
    for word in BANNED_WORDS:
        if word in text_lower:
            errors.append(f"banned word: '{word}'")

    # Banned openers
    for opener in BANNED_OPENERS:
        if text_lower.startswith(opener):
            errors.append(f"banned opener: '{opener}'")

    # Hashtag spam (more than 1)
    hashtags = re.findall(r"#\w+", text)
    if len(hashtags) > 1:
        errors.append(f"hashtag spam: {len(hashtags)} hashtags")

    # Exclamation spam (more than 1)
    if text.count("!") > 1:
        errors.append(f"too many exclamation marks: {text.count('!')}")

    # Emoji spam (more than 2 emoji characters)
    emoji_pattern = re.compile(
        "[\U0001f600-\U0001f64f\U0001f300-\U0001f5ff"
        "\U0001f680-\U0001f6ff\U0001f900-\U0001f9ff"
        "\U00002702-\U000027b0]+",
        flags=re.UNICODE,
    )
    emojis = emoji_pattern.findall(text)
    total_emoji = sum(len(e) for e in emojis)
    if total_emoji > 2:
        errors.append(f"too many emojis: {total_emoji}")

    return errors


async def generate_content(req: ContentRequest) -> Optional[str]:
    """Generate content using Gemini CLI.

    Returns the generated text, or None on failure.
    Runs Gemini CLI as a subprocess (free tier).
    """
    prompt = build_generation_prompt(req)

    try:
        result = subprocess.run(
            ["gemini", "-p", prompt, "--output-format", "text"],
            capture_output=True, text=True, timeout=60,
        )
        if result.returncode != 0:
            logger.error(f"gemini cli failed: {result.stderr}")
            return None

        text = result.stdout.strip()
        # Strip any quotes Gemini might wrap it in
        if text.startswith('"') and text.endswith('"'):
            text = text[1:-1]

        return text

    except subprocess.TimeoutExpired:
        logger.error("gemini cli timed out")
        return None
    except FileNotFoundError:
        logger.error("gemini cli not found")
        return None
```

**Step 4: Run tests**

```bash
pytest tests/test_content.py -v
```

Expected: All 6 tests PASS

**Step 5: Commit**

```bash
git add worker/worker/content.py worker/tests/test_content.py
git commit -m "feat(worker): content generator with Gemini CLI and validation"
```

---

## Task 9: Action picker and executor

**Files:**
- Create: `worker/worker/executor.py`
- Create: `worker/tests/test_executor.py`

This is the core decision engine: picks actions from the queue and calendar, determines which are auto-approved vs need escalation, and executes them.

**Step 1: Write failing tests**

```python
"""Tests for the action executor."""

import pytest
import pytest_asyncio

from worker.executor import ActionPicker, EscalationTier
from worker.config import Config
from worker.db import Database


@pytest_asyncio.fixture
async def db():
    database = Database(":memory:")
    await database.init()
    yield database
    await database.close()


def test_escalation_tier_original_post():
    tier = ActionPicker.get_escalation_tier(
        action_type="original_post",
        platform="twitter",
    )
    assert tier == EscalationTier.AUTO


def test_escalation_tier_reply():
    tier = ActionPicker.get_escalation_tier(
        action_type="reply",
        platform="twitter",
    )
    assert tier == EscalationTier.BUTTONS


def test_escalation_tier_reddit_anything():
    tier = ActionPicker.get_escalation_tier(
        action_type="original_post",
        platform="reddit",
    )
    assert tier == EscalationTier.BUTTONS


def test_escalation_tier_outreach_dm():
    tier = ActionPicker.get_escalation_tier(
        action_type="outreach_dm",
        platform="bluesky",
    )
    assert tier == EscalationTier.BUTTONS


def test_escalation_tier_like():
    tier = ActionPicker.get_escalation_tier(
        action_type="like",
        platform="twitter",
    )
    assert tier == EscalationTier.AUTO


@pytest.mark.asyncio
async def test_pick_actions_respects_daily_limit(db):
    config = Config.for_testing()
    config.max_actions_per_day = 2

    # Log 2 actions already done today
    await db.log_action("tweet", "twitter", "post 1", "posted")
    await db.log_action("tweet", "twitter", "post 2", "posted")

    picker = ActionPicker(config=config, db=db)
    actions = await picker.pick_actions()
    assert len(actions) == 0  # at daily limit


@pytest.mark.asyncio
async def test_pick_actions_from_calendar(db):
    config = Config.for_testing()
    from datetime import date
    today = date.today().isoformat()

    await db.add_calendar_entry(
        scheduled_date=today,
        pillar="demo",
        platform="twitter",
        content_draft="Voice input demo post",
    )

    picker = ActionPicker(config=config, db=db)
    actions = await picker.pick_actions()
    assert len(actions) >= 1
    assert actions[0]["source"] == "calendar"
```

**Step 2: Run to verify failure**

```bash
pytest tests/test_executor.py -v
```

Expected: FAIL

**Step 3: Write executor.py**

```python
"""Action picker and executor — the core decision engine."""

import logging
from datetime import date
from enum import Enum
from typing import Optional

from worker.config import Config
from worker.db import Database

logger = logging.getLogger(__name__)


class EscalationTier(Enum):
    AUTO = "auto"
    BUTTONS = "buttons"


class ActionPicker:
    def __init__(self, config: Config, db: Database):
        self.config = config
        self.db = db

    @staticmethod
    def get_escalation_tier(
        action_type: str, platform: str
    ) -> EscalationTier:
        """Determine whether an action is auto-approved or needs buttons."""
        # Reddit and HN/dev.to always need approval (draft-only)
        if platform in ("reddit", "hn", "devto"):
            return EscalationTier.BUTTONS

        # Auto-approved actions
        auto_types = {"like", "repost", "follow", "original_post"}
        if action_type in auto_types and platform in ("twitter", "bluesky"):
            return EscalationTier.AUTO

        # Everything else needs buttons
        return EscalationTier.BUTTONS

    async def pick_actions(self) -> list[dict]:
        """Pick actions for this evening's session.

        Returns a list of action dicts with:
        - source: 'calendar' or 'finding'
        - action_type: tweet, reply, like, etc.
        - platform: twitter, bluesky, etc.
        - content: draft text or description
        - finding_id: if from a finding
        - tier: EscalationTier
        """
        today = date.today().isoformat()
        total_today = await self.db.get_daily_action_count()
        remaining = self.config.max_actions_per_day - total_today

        if remaining <= 0:
            logger.info("daily action limit reached")
            return []

        actions = []

        # 1. Check calendar for today's planned content
        calendar_entries = await self.db.get_calendar_entries(today)
        for entry in calendar_entries:
            if entry["status"] != "planned":
                continue
            if len(actions) >= remaining:
                break

            tier = self.get_escalation_tier(
                "original_post", entry["platform"]
            )
            actions.append({
                "source": "calendar",
                "calendar_id": entry["id"],
                "action_type": "original_post",
                "platform": entry["platform"],
                "content": entry["content_draft"],
                "pillar": entry["pillar"],
                "tier": tier,
            })

        # 2. Fill remaining slots from high-relevance findings
        if len(actions) < remaining:
            findings = await self.db.get_queued_findings(
                limit=remaining - len(actions)
            )
            for finding in findings:
                actions.append({
                    "source": "finding",
                    "finding_id": finding["id"],
                    "action_type": "reply",
                    "platform": finding["platform"],
                    "content": finding["content"],
                    "source_url": finding["source_url"],
                    "source_user": finding["source_user"],
                    "tier": self.get_escalation_tier(
                        "reply", finding["platform"]
                    ),
                })

        return actions[:remaining]
```

**Step 4: Run tests**

```bash
pytest tests/test_executor.py -v
```

Expected: All 7 tests PASS

**Step 5: Commit**

```bash
git add worker/worker/executor.py worker/tests/test_executor.py
git commit -m "feat(worker): action picker with escalation tiers"
```

---

## Task 10: Main scheduler and entry point

**Files:**
- Create: `worker/worker/main.py`
- Create: `worker/worker/runner.py`
- Create: `worker/start.sh`

This ties everything together: the monitor loop, action picker, content generator, approval flow, and platform clients.

**Step 1: Write runner.py (the orchestrator)**

```python
"""Main runner — orchestrates monitor and action loops."""

import asyncio
import json
import logging
from datetime import datetime, timezone

import redis.asyncio as aioredis

from worker.approvals import ApprovalManager
from worker.config import Config
from worker.content import (
    ContentRequest,
    generate_content,
    validate_generated_content,
)
from worker.db import Database
from worker.executor import ActionPicker, EscalationTier
from worker.monitor import Monitor
from worker.platforms.bluesky import BlueskyClient
from worker.platforms.twitter import TwitterClient

logger = logging.getLogger(__name__)


class WorkerRunner:
    def __init__(self, config: Config):
        self.config = config
        self.db: Database = None
        self.twitter: TwitterClient = None
        self.bluesky: BlueskyClient = None
        self.monitor: Monitor = None
        self.picker: ActionPicker = None
        self.approvals: ApprovalManager = None
        self._redis_sub: aioredis.Redis = None

    async def start(self):
        """Initialize all components."""
        self.db = Database(self.config.db_path)
        await self.db.init()

        self.twitter = TwitterClient(self.config.twitter)
        self.bluesky = BlueskyClient(self.config.bluesky)
        self.monitor = Monitor(self.config, self.db)
        self.picker = ActionPicker(self.config, self.db)
        self.approvals = ApprovalManager(self.config, self.db)

        # Start Redis subscription for approval decisions
        self._redis_sub = aioredis.Redis(
            host=self.config.redis.host,
            port=self.config.redis.port,
            password=self.config.redis.password,
            decode_responses=True,
        )

        logger.info("keyjawn-worker started")

    async def stop(self):
        """Clean shutdown."""
        if self._redis_sub:
            await self._redis_sub.close()
        if self.db:
            await self.db.close()
        logger.info("keyjawn-worker stopped")

    async def run_monitor_scan(self):
        """Run one monitor scan cycle."""
        logger.info("starting monitor scan")
        queued = await self.monitor.scan_all_platforms(
            self.twitter, self.bluesky
        )
        logger.info(f"monitor scan done: {queued} new findings queued")

    async def run_action_session(self):
        """Run one action session (evening window)."""
        logger.info("starting action session")
        actions = await self.picker.pick_actions()

        if not actions:
            logger.info("no actions to take")
            return

        escalation_count = 0
        for action in actions:
            if action["tier"] == EscalationTier.AUTO:
                await self._execute_auto(action)
            else:
                if escalation_count >= self.config.max_escalations_per_evening:
                    logger.info("escalation limit reached, skipping")
                    continue
                await self._execute_with_approval(action)
                escalation_count += 1

        logger.info("action session complete")

    async def _execute_auto(self, action: dict):
        """Execute an auto-approved action."""
        content = action["content"]

        # Generate content if it's a calendar post
        if action["source"] == "calendar":
            generated = await generate_content(ContentRequest(
                pillar=action.get("pillar", "demo"),
                platform=action["platform"],
                topic=content,
            ))
            if generated:
                errors = validate_generated_content(
                    generated, action["platform"]
                )
                if errors:
                    logger.warning(
                        f"generated content has issues: {errors}"
                    )
                    # Retry once
                    generated = await generate_content(ContentRequest(
                        pillar=action.get("pillar", "demo"),
                        platform=action["platform"],
                        topic=content,
                    ))
                    if generated:
                        errors = validate_generated_content(
                            generated, action["platform"]
                        )
                if generated and not errors:
                    content = generated
                else:
                    logger.error("content generation failed, skipping")
                    return

        # Post to platform
        post_url = await self._post_to_platform(
            action["platform"], content, action["action_type"],
        )

        # Log the action
        await self.db.log_action(
            action_type=action["action_type"],
            platform=action["platform"],
            content=content,
            status="posted" if post_url else "failed",
            post_url=post_url,
            finding_id=action.get("finding_id"),
        )

    async def _execute_with_approval(self, action: dict):
        """Execute an action that needs Telegram approval."""
        content = action["content"]

        # Generate a draft for replies
        if action["action_type"] == "reply":
            generated = await generate_content(ContentRequest(
                pillar="engagement",
                platform=action["platform"],
                topic=f"reply to: {content[:100]}",
                context=f"@{action.get('source_user', 'user')} said: {content}",
            ))
            if generated:
                errors = validate_generated_content(
                    generated, action["platform"]
                )
                if not errors:
                    content = generated

        # Log as pending
        action_id = await self.db.log_action(
            action_type=action["action_type"],
            platform=action["platform"],
            content=content,
            status="pending_approval",
            finding_id=action.get("finding_id"),
        )

        # Send to Telegram and wait
        decision = await self.approvals.request_approval(
            action_id=action_id,
            action_type=action["action_type"],
            platform=action["platform"],
            draft=content,
            context=action.get("content") if action["source"] == "finding" else None,
        )

        if decision == "approve":
            post_url = await self._post_to_platform(
                action["platform"], content, action["action_type"],
                in_reply_to=action.get("source_url"),
            )
            await self.db.conn.execute(
                "UPDATE actions SET status = ?, post_url = ? WHERE id = ?",
                ("posted" if post_url else "failed", post_url, action_id),
            )
            await self.db.conn.commit()
        elif decision == "rethink":
            # Generate new content and re-escalate
            logger.info(f"rethinking action {action_id}")
            # (Rethink loop handled by the approval manager)

    async def _post_to_platform(
        self, platform: str, content: str, action_type: str,
        in_reply_to: str = None,
    ) -> str:
        """Post content to a platform. Returns URL or None."""
        if platform == "twitter":
            if action_type == "reply" and in_reply_to:
                # Extract tweet ID from URL
                tweet_id = in_reply_to.split("/")[-1]
                return await self.twitter.reply(content, tweet_id)
            return await self.twitter.post(content)
        elif platform == "bluesky":
            return await self.bluesky.post(content)
        else:
            logger.warning(f"no posting support for {platform}")
            return None

    async def listen_for_decisions(self):
        """Listen for approval decisions via Redis pub/sub."""
        pubsub = self._redis_sub.pubsub()
        await pubsub.subscribe("keyjawn-worker:decisions")

        async for message in pubsub.listen():
            if message["type"] == "message":
                try:
                    await self.approvals.process_decision(
                        message["data"]
                    )
                except Exception as e:
                    logger.error(f"decision processing error: {e}")
```

**Step 2: Write main.py (entry point)**

```python
"""Entry point for keyjawn-worker."""

import asyncio
import logging
import sys
from datetime import datetime
from zoneinfo import ZoneInfo

from apscheduler.schedulers.asyncio import AsyncIOScheduler

from worker.config import Config
from worker.runner import WorkerRunner

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(name)s %(levelname)s %(message)s",
)
logger = logging.getLogger(__name__)

ET = ZoneInfo("America/New_York")


async def main():
    config = Config.from_pass()
    runner = WorkerRunner(config)
    await runner.start()

    scheduler = AsyncIOScheduler(timezone=ET)

    # Monitor scan: every 45 minutes during business hours (9am-9pm ET, weekdays)
    scheduler.add_job(
        runner.run_monitor_scan,
        "cron",
        day_of_week="mon-fri",
        hour="9-20",
        minute="*/45",
        id="monitor_scan",
    )

    # Action session: once per evening at a random minute between 6-8pm ET
    # (APScheduler doesn't do random, so pick 7pm as the default)
    scheduler.add_job(
        runner.run_action_session,
        "cron",
        day_of_week="mon-fri",
        hour=19,
        minute=0,
        id="action_session",
    )

    scheduler.start()

    # Also start the Redis listener for approval decisions
    listener_task = asyncio.create_task(
        runner.listen_for_decisions()
    )

    logger.info("keyjawn-worker running (Ctrl+C to stop)")

    try:
        await asyncio.Event().wait()  # run forever
    except (KeyboardInterrupt, SystemExit):
        pass
    finally:
        listener_task.cancel()
        scheduler.shutdown()
        await runner.stop()


if __name__ == "__main__":
    asyncio.run(main())
```

**Step 3: Write start.sh**

```bash
#!/usr/bin/env bash
# Start keyjawn-worker
set -euo pipefail

cd "$(dirname "$0")"
source venv/bin/activate
exec python -m worker.main
```

**Step 4: Commit**

```bash
chmod +x worker/start.sh
git add worker/worker/main.py worker/worker/runner.py worker/start.sh
git commit -m "feat(worker): main scheduler and orchestrator"
```

---

## Task 11: Systemd service setup on officejawn

**Files:**
- Create: `worker/keyjawn-worker.service`

**Step 1: Write the service file**

```ini
[Unit]
Description=KeyJawn marketing worker
After=network.target redis.target

[Service]
Type=simple
User=jamditis
WorkingDirectory=/home/jamditis/projects/keyjawn/worker
ExecStart=/home/jamditis/projects/keyjawn/worker/start.sh
Restart=on-failure
RestartSec=30
Environment=PYTHONUNBUFFERED=1

[Install]
WantedBy=multi-user.target
```

**Step 2: Deploy to officejawn**

```bash
# Copy project to officejawn
rsync -avz --exclude='venv' --exclude='__pycache__' \
  /home/jamditis/projects/keyjawn/worker/ \
  officejawn:/home/jamditis/projects/keyjawn/worker/

# Set up venv on officejawn
ssh officejawn "cd ~/projects/keyjawn/worker && python3 -m venv venv && source venv/bin/activate && pip install -e '.[dev]'"

# Install service
ssh officejawn "sudo cp ~/projects/keyjawn/worker/keyjawn-worker.service /etc/systemd/system/ && sudo systemctl daemon-reload && sudo systemctl enable keyjawn-worker"
```

**Step 3: Start and verify**

```bash
ssh officejawn "sudo systemctl start keyjawn-worker && sleep 2 && systemctl is-active keyjawn-worker"
```

Expected: `active`

**Step 4: Commit**

```bash
git add worker/keyjawn-worker.service
git commit -m "feat(worker): systemd service file for officejawn"
```

---

## Task 12: Weekly content calendar generator

**Files:**
- Create: `worker/worker/calendar_gen.py`
- Create: `worker/tests/test_calendar_gen.py`

**Step 1: Write failing tests**

```python
"""Tests for weekly content calendar generation."""

import pytest
import pytest_asyncio
from datetime import date

from worker.calendar_gen import generate_weekly_calendar, PILLARS
from worker.config import Config
from worker.db import Database


@pytest_asyncio.fixture
async def db():
    database = Database(":memory:")
    await database.init()
    yield database
    await database.close()


def test_pillars_defined():
    assert len(PILLARS) == 5
    assert "demo" in PILLARS
    assert "awareness" in PILLARS
    assert "engagement" in PILLARS


@pytest.mark.asyncio
async def test_generate_calendar_creates_entries(db):
    config = Config.for_testing()
    start = date(2026, 2, 17)  # Monday

    count = await generate_weekly_calendar(config, db, start)
    assert count >= 5  # at least 1 per weekday

    # Check Monday has entries
    entries = await db.get_calendar_entries("2026-02-17")
    assert len(entries) >= 1


@pytest.mark.asyncio
async def test_generate_calendar_skips_weekends(db):
    config = Config.for_testing()
    start = date(2026, 2, 17)  # Monday

    await generate_weekly_calendar(config, db, start)

    # Saturday and Sunday should have no entries
    sat = await db.get_calendar_entries("2026-02-22")
    sun = await db.get_calendar_entries("2026-02-23")
    assert len(sat) == 0
    assert len(sun) == 0


@pytest.mark.asyncio
async def test_calendar_distributes_across_platforms(db):
    config = Config.for_testing()
    start = date(2026, 2, 17)

    await generate_weekly_calendar(config, db, start)

    # Collect all platforms used
    platforms = set()
    for i in range(5):  # Mon-Fri
        d = date(2026, 2, 17 + i).isoformat()
        entries = await db.get_calendar_entries(d)
        for e in entries:
            platforms.add(e["platform"])

    assert "twitter" in platforms
    assert "bluesky" in platforms
```

**Step 2: Run to verify failure**

```bash
pytest tests/test_calendar_gen.py -v
```

Expected: FAIL

**Step 3: Write calendar_gen.py**

```python
"""Weekly content calendar generator."""

import logging
from datetime import date, timedelta
from itertools import cycle

from worker.config import Config
from worker.db import Database

logger = logging.getLogger(__name__)

PILLARS = ["awareness", "demo", "engagement", "social_proof", "behind_scenes"]

# Topic templates per pillar
TOPICS = {
    "awareness": [
        "Standard keyboards failing at CLI tasks",
        "Autocorrect mangling shell commands",
        "Missing Esc/Tab/Ctrl on mobile",
        "The pain of arrow keys on touch keyboards",
    ],
    "demo": [
        "Voice input composing a Claude Code prompt",
        "Terminal key row in action",
        "SCP image upload mid-conversation",
        "Slash command shortcuts demo",
    ],
    "engagement": [
        "Reply to mobile SSH conversations",
        "Help someone with their CLI workflow",
        "Share tips for phone-based development",
    ],
    "social_proof": [
        "Download/purchase milestone update",
        "Share user feedback or support resolution",
        "GitHub activity update",
    ],
    "behind_scenes": [
        "What Joe is working on next",
        "Open source philosophy and $4 pricing",
        "Dev update on upcoming features",
    ],
}

PLATFORMS = ["twitter", "bluesky"]


async def generate_weekly_calendar(
    config: Config, db: Database, start_date: date
) -> int:
    """Generate a week's content calendar starting from start_date (Monday).

    Returns the number of entries created.
    """
    pillar_cycle = cycle(PILLARS)
    platform_cycle = cycle(PLATFORMS)
    topic_indices = {p: 0 for p in PILLARS}
    count = 0

    for day_offset in range(7):
        current = start_date + timedelta(days=day_offset)

        # Skip weekends
        if current.weekday() >= 5:
            continue

        pillar = next(pillar_cycle)
        platform = next(platform_cycle)

        # Pick topic
        topics = TOPICS[pillar]
        idx = topic_indices[pillar] % len(topics)
        topic = topics[idx]
        topic_indices[pillar] = idx + 1

        await db.add_calendar_entry(
            scheduled_date=current.isoformat(),
            pillar=pillar,
            platform=platform,
            content_draft=topic,
        )
        count += 1

        # Add a second entry for some days (engagement or likes)
        if day_offset % 2 == 0:
            other_platform = next(platform_cycle)
            await db.add_calendar_entry(
                scheduled_date=current.isoformat(),
                pillar="engagement",
                platform=other_platform,
                content_draft="Monitor and engage with relevant conversations",
            )
            count += 1

    logger.info(
        f"generated {count} calendar entries for week of {start_date}"
    )
    return count
```

**Step 4: Run tests**

```bash
pytest tests/test_calendar_gen.py -v
```

Expected: All 4 tests PASS

**Step 5: Commit**

```bash
git add worker/worker/calendar_gen.py worker/tests/test_calendar_gen.py
git commit -m "feat(worker): weekly content calendar generator"
```

---

## Task 13: Integration test and smoke test

**Files:**
- Create: `worker/tests/test_integration.py`

**Step 1: Write integration test**

```python
"""Integration tests — tests the full pipeline with mocked platform APIs."""

import pytest
import pytest_asyncio
from datetime import date
from unittest.mock import AsyncMock, patch, MagicMock

from worker.config import Config
from worker.db import Database
from worker.executor import ActionPicker
from worker.monitor import Monitor
from worker.content import validate_generated_content


@pytest_asyncio.fixture
async def db():
    database = Database(":memory:")
    await database.init()
    yield database
    await database.close()


@pytest.mark.asyncio
async def test_full_pipeline_monitor_to_action(db):
    """Test: monitor finds something -> queues it -> picker selects it."""
    config = Config.for_testing()
    monitor = Monitor(config, db)

    # Simulate findings from a platform scan
    findings = [
        {
            "url": "https://twitter.com/status/1",
            "text": "anyone know a good keyboard for SSH on my phone?",
            "author": "devuser",
            "platform": "twitter",
        },
    ]

    queued = await monitor.queue_new_findings(findings)
    assert queued == 1

    # Picker should select this finding
    picker = ActionPicker(config, db)
    actions = await picker.pick_actions()
    assert len(actions) == 1
    assert actions[0]["source"] == "finding"
    assert actions[0]["platform"] == "twitter"


@pytest.mark.asyncio
async def test_full_pipeline_calendar_to_action(db):
    """Test: calendar entry -> picker selects it."""
    config = Config.for_testing()
    today = date.today().isoformat()

    await db.add_calendar_entry(
        scheduled_date=today,
        pillar="demo",
        platform="twitter",
        content_draft="Voice input demo",
    )

    picker = ActionPicker(config, db)
    actions = await picker.pick_actions()
    assert len(actions) == 1
    assert actions[0]["source"] == "calendar"
    assert actions[0]["content"] == "Voice input demo"


@pytest.mark.asyncio
async def test_daily_limit_enforced(db):
    """Test: can't exceed max_actions_per_day."""
    config = Config.for_testing()
    config.max_actions_per_day = 1

    await db.log_action("tweet", "twitter", "already posted", "posted")

    picker = ActionPicker(config, db)
    actions = await picker.pick_actions()
    assert len(actions) == 0


@pytest.mark.asyncio
async def test_dedup_prevents_double_queue(db):
    """Test: same URL doesn't get queued twice."""
    config = Config.for_testing()
    monitor = Monitor(config, db)

    finding = {
        "url": "https://twitter.com/status/dup",
        "text": "keyboard for SSH on phone?",
        "author": "user1",
        "platform": "twitter",
    }

    assert await monitor.queue_new_findings([finding]) == 1
    assert await monitor.queue_new_findings([finding]) == 0  # deduped
```

**Step 2: Run all tests**

```bash
pytest tests/ -v
```

Expected: All tests PASS

**Step 3: Commit**

```bash
git add worker/tests/test_integration.py
git commit -m "test(worker): integration tests for full pipeline"
```

---

## Summary of tasks

| Task | Component | Key files |
|------|-----------|-----------|
| 1 | Project scaffolding | `worker/`, `config.py`, `pyproject.toml` |
| 2 | Database schema | `db.py` |
| 3 | Telegram approval (bot side + protocol) | `telegram.py`, bot.py mod |
| 4 | Redis approval listener | `approvals.py` |
| 5 | Twitter client | `platforms/twitter.py` |
| 6 | Bluesky client | `platforms/bluesky.py` |
| 7 | Monitor loop | `monitor.py` |
| 8 | Content generator | `content.py` |
| 9 | Action picker/executor | `executor.py` |
| 10 | Main scheduler | `main.py`, `runner.py`, `start.sh` |
| 11 | Systemd service | `keyjawn-worker.service` |
| 12 | Content calendar | `calendar_gen.py` |
| 13 | Integration tests | `test_integration.py` |

## Post-MVP additions (not in this plan)

- Reddit API monitoring (read-only, PRAW)
- HN Algolia API monitoring
- dev.to blog post drafting
- Product Hunt submission flow
- UTM parameter tracking
- Weekly metrics report generation
- Outreach DM templates
- Advanced rethink loop (multiple draft strategies)
