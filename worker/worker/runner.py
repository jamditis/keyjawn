"""Main runner -- orchestrates monitor and action loops."""

import asyncio
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
from worker.platforms.producthunt import ProductHuntClient
from worker.platforms.twitter import TwitterClient

logger = logging.getLogger(__name__)


class WorkerRunner:
    def __init__(self, config: Config):
        self.config = config
        self.db: Database = None
        self.twitter: TwitterClient = None
        self.bluesky: BlueskyClient = None
        self.producthunt: ProductHuntClient = None
        self.monitor: Monitor = None
        self.picker: ActionPicker = None
        self.approvals: ApprovalManager = None
        self.curation_monitor = None
        self._redis_sub: aioredis.Redis = None

    async def start(self):
        """Initialize all components."""
        self.db = Database(self.config.db_path)
        await self.db.init()

        self.twitter = TwitterClient(self.config.twitter)
        self.bluesky = BlueskyClient(self.config.bluesky)
        if self.config.producthunt.developer_token:
            self.producthunt = ProductHuntClient(
                self.config.producthunt.developer_token
            )
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

        from worker.curation.monitor import CurationMonitor
        self.curation_monitor = CurationMonitor(self.config.curation, self.db)

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
            self.twitter, self.bluesky, self.producthunt
        )
        logger.info("monitor scan done: %d new findings queued", queued)

    async def run_curation_scan(self, include_twitch: bool = False):
        """Run one curation scan + evaluation cycle."""
        if not self.curation_monitor:
            return
        logger.info("starting curation scan")
        approved = await self.curation_monitor.scan_and_evaluate(
            include_twitch=include_twitch
        )
        logger.info("curation scan done: %d new candidates approved", approved)

    async def run_action_session(self):
        """Run one action session (evening window)."""
        logger.info("starting action session")
        actions = await self.picker.pick_actions()

        if not actions:
            logger.info("no actions to take")
            return

        for action in actions:
            if action["tier"] == EscalationTier.AUTO:
                await self._execute_auto(action)
            else:
                await self._execute_with_approval(action)

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
                    logger.warning("generated content has issues: %s", errors)
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

        post_url = await self._post_to_platform(
            action["platform"], content, action["action_type"],
        )

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

        action_id = await self.db.log_action(
            action_type=action["action_type"],
            platform=action["platform"],
            content=content,
            status="pending_approval",
            finding_id=action.get("finding_id"),
        )

        # Use curation-specific message format for curated shares
        if action["action_type"] == "curated_share":
            from worker.telegram import format_curation_message
            decision = await self.approvals.request_approval(
                action_id=action_id,
                action_type=action["action_type"],
                platform=action["platform"],
                draft=content,
                context=None,
                message_override=format_curation_message(
                    action_id=action_id,
                    source=f"{action.get('curation_source', '')} - {action.get('curation_title', '')}",
                    title=action.get("curation_title", ""),
                    score=action.get("curation_score", 0.0),
                    reasoning=action.get("curation_reasoning", ""),
                    draft=content,
                    platform=action["platform"],
                ),
            )
        else:
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
            await self.db._db.execute(
                "UPDATE actions SET status = ?, post_url = ? WHERE id = ?",
                ("posted" if post_url else "failed", post_url, action_id),
            )
            await self.db._db.commit()

    async def _post_to_platform(
        self, platform: str, content: str, action_type: str,
        in_reply_to: str = None,
    ) -> str:
        """Post content to a platform. Returns URL or None."""
        if platform == "twitter":
            if action_type == "reply" and in_reply_to:
                tweet_id = in_reply_to.split("/")[-1]
                return await self.twitter.reply(content, tweet_id)
            return await self.twitter.post(content)
        elif platform == "bluesky":
            return await self.bluesky.post(content)
        else:
            logger.warning("no posting support for %s", platform)
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
                    logger.error("decision processing error: %s", e)
