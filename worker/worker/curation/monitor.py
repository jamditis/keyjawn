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
