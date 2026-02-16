"""Approval flow: send Telegram prompts, wait for decisions via Redis pub/sub."""

from __future__ import annotations

import asyncio
import json
import logging

import httpx

from worker.config import Config
from worker.db import Database
from worker.telegram import format_escalation_message, build_approval_keyboard

log = logging.getLogger(__name__)

DECISION_STATUS_MAP = {
    "approve": "approved",
    "deny": "denied",
    "backlog": "backlogged",
    "rethink": "pending_rethink",
}


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
        context: str | None = None,
    ) -> str:
        """Send a Telegram approval prompt and wait for a decision.

        Returns the decision string (approve/deny/backlog/rethink).
        If Telegram isn't configured, auto-approves.
        On timeout, returns "backlog".
        """
        token = self.config.telegram.bot_token
        chat_id = self.config.telegram.chat_id

        if not token or not chat_id:
            log.warning("Telegram not configured, auto-approving action %s", action_id)
            return "approve"

        text = format_escalation_message(
            action_id=action_id,
            action_type=action_type,
            platform=platform,
            context=context,
            draft=draft,
        )
        keyboard = build_approval_keyboard(action_id)

        url = f"https://api.telegram.org/bot{token}/sendMessage"
        payload = {
            "chat_id": chat_id,
            "text": text,
            "parse_mode": "HTML",
            "reply_markup": {"inline_keyboard": keyboard},
        }

        async with httpx.AsyncClient() as client:
            resp = await client.post(url, json=payload)
            if resp.status_code != 200:
                log.error(
                    "Telegram sendMessage failed (%d): %s",
                    resp.status_code,
                    resp.text,
                )

        loop = asyncio.get_event_loop()
        future = loop.create_future()
        self._pending[action_id] = future

        try:
            decision = await asyncio.wait_for(
                future, timeout=self.config.approval_timeout_seconds
            )
            return decision
        except asyncio.TimeoutError:
            log.warning("Approval timeout for action %s, backlogging", action_id)
            self._pending.pop(action_id, None)
            await self.db._db.execute(
                "UPDATE actions SET status = ?, approval_decision = ?, approval_timestamp = datetime('now') WHERE id = ?",
                ("backlogged", "timeout", action_id),
            )
            await self.db._db.commit()
            return "backlog"

    async def process_decision(self, message: str) -> str:
        """Process a decision received via Redis pub/sub.

        Expects JSON: {"action_id": str, "decision": str, "timestamp": str}
        Updates the DB and resolves any pending future.
        Returns the decision string.
        """
        data = json.loads(message)
        action_id = data["action_id"]
        decision = data["decision"]
        timestamp = data["timestamp"]

        status = DECISION_STATUS_MAP.get(decision, decision)

        await self.db._db.execute(
            "UPDATE actions SET status = ?, approval_decision = ?, approval_timestamp = ? WHERE id = ?",
            (status, decision, timestamp, action_id),
        )
        await self.db._db.commit()

        future = self._pending.pop(action_id, None)
        if future is not None and not future.done():
            future.set_result(decision)

        return decision
