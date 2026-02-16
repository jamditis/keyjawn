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
