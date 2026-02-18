"""Monitor loop: scan platforms for relevant conversations and queue findings."""

from __future__ import annotations

import logging
from typing import Optional

from worker.config import Config
from worker.db import Database

log = logging.getLogger(__name__)

HIGH_SIGNAL = [
    "keyboard for ssh",
    "keyboard for cli",
    "keyboard for terminal",
    "ssh on my phone",
    "ssh from phone",
    "ssh from mobile",
    "terminal on phone",
    "terminal on mobile",
    "mobile terminal",
    "remote terminal",
    "claude code mobile",
    "claude code phone",
    "claude code",
    "gemini cli mobile",
    "gemini cli",
    "mobile cli keyboard",
    "android terminal keyboard",
    "android terminal",
    "keyjawn",
]

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
    "tmux",
    "terminal emulator",
    "terminal from mobile",
    "terminal recommendation",
    "coding from phone",
    "coding on phone",
    "ssh on android",
    "terminal app",
]

BOOSTERS = ["android", "mobile", "phone", "keyboard", "typing", "terminal", "cli", "ssh"]


class Monitor:
    def __init__(self, config: Config, db: Optional[Database] = None):
        self.config = config
        self.db = db

    def score_relevance(self, text: str, platform: str) -> float:
        """Score content 0.0-1.0 based on keyword relevance."""
        text_lower = text.lower()

        score = 0.0

        if any(phrase in text_lower for phrase in HIGH_SIGNAL):
            score = 0.8
        elif any(phrase in text_lower for phrase in MEDIUM_SIGNAL):
            score = 0.5
        elif sum(1 for b in BOOSTERS if b in text_lower) >= 2:
            score = 0.4

        if "?" in text and score > 0.3:
            score = min(score + 0.1, 1.0)

        return score

    async def queue_new_findings(self, findings: list[dict]) -> int:
        """Deduplicate and queue findings above the relevance threshold.

        Each finding dict has: url, text, author, platform.
        Returns count of newly queued items.
        """
        queued = 0
        for f in findings:
            url = f["url"]
            text = f["text"]
            author = f["author"]
            platform = f["platform"]

            # Dedup: skip if source_url already exists
            cursor = await self.db._db.execute(
                "SELECT 1 FROM findings WHERE source_url = ?", (url,)
            )
            if await cursor.fetchone():
                continue

            score = self.score_relevance(text, platform)
            if score < 0.3:
                continue

            await self.db.queue_finding(
                platform=platform,
                source_url=url,
                source_user=author,
                content=text,
                relevance_score=score,
            )
            queued += 1

        return queued

    async def scan_all_platforms(
        self, twitter_client, bluesky_client, producthunt_client=None,
        social_scroller_client=None,
    ) -> int:
        """Scan all platforms for relevant conversations.

        Uses API clients (Twitter, Bluesky, Product Hunt) and browser-based
        scanning via social-scroller for additional platforms.

        Returns total count of newly queued findings.
        """
        all_findings = []

        # --- API client searches ---

        # Search Twitter for all high-signal keywords
        try:
            for keyword in HIGH_SIGNAL:
                results = await twitter_client.search(keyword)
                for r in results:
                    all_findings.append({
                        "url": r.get("url", ""),
                        "text": r.get("text", ""),
                        "author": r.get("author", ""),
                        "platform": "twitter",
                    })
        except Exception:
            log.exception("twitter search failed")

        # Search Bluesky for first 3 keywords
        try:
            for keyword in HIGH_SIGNAL[:3]:
                results = await bluesky_client.search(keyword)
                for r in results:
                    all_findings.append({
                        "url": r.get("url", ""),
                        "text": r.get("text", ""),
                        "author": r.get("author", ""),
                        "platform": "bluesky",
                    })
        except Exception:
            log.exception("bluesky search failed")

        # Scan Product Hunt for relevant launches
        if producthunt_client:
            try:
                launches = await producthunt_client.find_relevant_launches()
                for launch in launches:
                    all_findings.append({
                        "url": launch.get("url", ""),
                        "text": f"{launch['name']}: {launch['tagline']}",
                        "author": "producthunt",
                        "platform": "producthunt",
                    })
            except Exception:
                log.exception("producthunt scan failed")

        # --- Browser-based scanning via social-scroller ---

        if social_scroller_client:
            # Targeted keyword searches with platform-specific strategies
            try:
                strategy_results = await social_scroller_client.search_with_strategy()
                all_findings.extend(strategy_results)
                log.info(
                    "social-scroller strategy search: %d posts",
                    len(strategy_results),
                )
            except Exception:
                log.exception("social-scroller strategy search failed")

            # Passive feed scan — scroll open tabs and extract posts
            try:
                feed_posts = await social_scroller_client.scan_feeds()
                all_findings.extend(feed_posts)
                log.info(
                    "social-scroller feed scan: %d posts extracted",
                    len(feed_posts),
                )
            except Exception:
                log.exception("social-scroller feed scan failed")

        count = await self.queue_new_findings(all_findings)
        log.info("scan complete: %d findings queued from %d candidates", count, len(all_findings))
        return count
