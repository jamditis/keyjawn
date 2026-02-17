"""On-platform discovery engine for engagement opportunities.

Searches Twitter/Bluesky for content to like, repost, follow, or quote-repost.
Uses the curated account list + keyword searches.
"""

from __future__ import annotations

import logging
from dataclasses import dataclass

from worker.accounts import get_accounts_for_platform, get_all_handles
from worker.curation.keywords import POSITIVE_SIGNALS
from worker.db import Database

log = logging.getLogger(__name__)


@dataclass
class EngagementOpportunity:
    platform: str
    post_id: str
    post_url: str
    author: str
    text: str
    opportunity_type: str  # like, repost, quote_repost, follow, skip


# Subset of keywords for on-platform searches
ENGAGEMENT_KEYWORDS = [
    "terminal emulator",
    "CLI tool",
    "open source keyboard",
    "neovim",
    "tmux",
    "SSH mobile",
    "developer keyboard",
    "terminal multiplexer",
    "mobile terminal",
    "remote terminal",
    "claude code",
    "mobile coding",
    "phone terminal",
    "android keyboard",
]


def classify_engagement(
    text: str,
    author: str,
    is_curated_account: bool,
) -> str:
    """Classify what kind of engagement a post warrants.

    Returns: like, repost, quote_repost, or skip.
    """
    text_lower = text.lower()

    # Check relevance
    keyword_hits = sum(1 for kw in POSITIVE_SIGNALS if kw in text_lower)
    if keyword_hits == 0 and not is_curated_account:
        return "skip"

    # Curated accounts get more engagement
    if is_curated_account:
        if keyword_hits >= 2:
            return "repost"
        return "like"

    # Unknown accounts: like only (safer)
    if keyword_hits >= 1:
        return "like"

    return "skip"


class DiscoveryEngine:
    def __init__(self, db: Database):
        self.db = db

    async def scan_twitter(self, twitter_client) -> int:
        """Scan Twitter for engagement opportunities. Returns count found."""
        curated_handles = set(
            h.lower() for h in get_all_handles("twitter")
        )
        found = 0

        # 1. Search keywords
        for keyword in ENGAGEMENT_KEYWORDS[:5]:
            try:
                results = await twitter_client.search(
                    query=f'"{keyword}" -from:KeyJawn -is:retweet lang:en',
                    max_results=10,
                )
                for tweet in results:
                    author = tweet.get("author", "").lower()
                    is_curated = author in curated_handles
                    action = classify_engagement(
                        tweet.get("text", ""), author, is_curated
                    )
                    if action == "skip":
                        continue

                    eid = await self.db.insert_engagement(
                        platform="twitter",
                        post_id=tweet["id"],
                        post_url=tweet["url"],
                        author=tweet.get("author", ""),
                        text=tweet.get("text", "")[:500],
                        opportunity_type=action,
                    )
                    if eid:
                        found += 1
            except Exception:
                log.exception("twitter keyword search failed: %s", keyword)

        log.info("twitter discovery: %d engagement opportunities found", found)
        return found

    async def scan_bluesky(self, bluesky_client) -> int:
        """Scan Bluesky for engagement opportunities. Returns count found."""
        curated_handles = set(
            h.lower() for h in get_all_handles("bluesky")
        )
        found = 0

        for keyword in ENGAGEMENT_KEYWORDS[:3]:
            try:
                results = await bluesky_client.search(keyword, limit=10)
                for post in results:
                    author = post.get("author", "").lower()
                    is_curated = author in curated_handles
                    action = classify_engagement(
                        post.get("text", ""), author, is_curated
                    )
                    if action == "skip":
                        continue

                    eid = await self.db.insert_engagement(
                        platform="bluesky",
                        post_id=post.get("uri", ""),
                        post_url=post.get("url", ""),
                        author=post.get("author", ""),
                        text=post.get("text", "")[:500],
                        opportunity_type=action,
                    )
                    if eid:
                        found += 1
            except Exception:
                log.exception("bluesky keyword search failed: %s", keyword)

        log.info("bluesky discovery: %d engagement opportunities found", found)
        return found

    async def scan_all(self, twitter_client, bluesky_client) -> int:
        """Run discovery on all platforms. Returns total found."""
        total = 0
        total += await self.scan_twitter(twitter_client)
        total += await self.scan_bluesky(bluesky_client)
        return total
