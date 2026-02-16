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
