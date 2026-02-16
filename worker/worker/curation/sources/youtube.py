"""YouTube Data API v3 source monitor."""

from __future__ import annotations

import logging
from datetime import datetime, timedelta, timezone
from typing import Optional

import httpx

from worker.curation.models import CurationCandidate

log = logging.getLogger(__name__)

API_BASE = "https://www.googleapis.com/youtube/v3"

# Each search.list call costs 100 YouTube API quota units.
# Free tier: 10,000 units/day. At 4 scans/day we budget ~2,400 units/scan.
# 7 terms × 3 results = 7 calls × 100 = 700 units/scan = 2,800/day.
#
# Most searches use videoDuration=short to target Shorts and quick demos
# (more shareable on Twitter/Bluesky). A couple use no duration filter
# to catch longer tutorials and reviews worth linking.

SEARCH_TERMS = [
    # Shorts-focused (videoDuration=short)
    {"q": "terminal CLI tools", "short": True},
    {"q": "open source android dev", "short": True},
    {"q": "coding on phone terminal", "short": True},
    {"q": "indie dev tools demo", "short": True},
    {"q": "SSH keyboard mobile", "short": True},
    # Any duration (catch longer tutorials worth linking)
    {"q": "terminal keyboard android", "short": False},
    {"q": "CLI tool walkthrough open source", "short": False},
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

    async def search(
        self, query: str, max_results: int = 3, short_only: bool = False,
    ) -> list[CurationCandidate]:
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
        if short_only:
            params["videoDuration"] = "short"

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
            results = await self.search(
                term["q"], max_results=3, short_only=term.get("short", False),
            )
            for c in results:
                if c.url not in seen_urls:
                    seen_urls.add(c.url)
                    all_candidates.append(c)

        log.info("YouTube scan: %d candidates from %d search terms", len(all_candidates), len(SEARCH_TERMS))
        return all_candidates
