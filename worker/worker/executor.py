"""Action picker and executor -- the core decision engine."""

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
        # Curated shares always need approval
        if action_type == "curated_share":
            return EscalationTier.BUTTONS

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

        # 4. Fill engagement slots from pending engagement opportunities
        engagement_remaining = 3  # max engagement actions per session
        if engagement_remaining > 0:
            engagements = await self.db.get_pending_engagements(limit=engagement_remaining)
            for eng in engagements:
                tier = self.get_escalation_tier(
                    eng["opportunity_type"], eng["platform"]
                )
                actions.append({
                    "source": "engagement",
                    "engagement_id": eng["id"],
                    "action_type": eng["opportunity_type"],
                    "platform": eng["platform"],
                    "content": eng["text"] or "",
                    "post_id": eng["post_id"],
                    "post_url": eng["post_url"],
                    "post_author": eng["author"],
                    "tier": tier,
                })

        return actions[:remaining]
