"""Management commands for keyjawn-worker: smoke test, calendar generation, etc."""

from __future__ import annotations

import argparse
import asyncio
import json
import logging
import sys

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(name)s %(levelname)s %(message)s",
)
log = logging.getLogger("manage")


async def smoke_test(config):
    """Send a test approval message and wait for a button tap."""
    import redis.asyncio as aioredis
    from worker.approvals import ApprovalManager
    from worker.db import Database

    db = Database(config.db_path)
    await db.init()

    approvals = ApprovalManager(config, db)

    # Start Redis listener in background
    redis_client = aioredis.Redis(
        host=config.redis.host,
        port=config.redis.port,
        password=config.redis.password,
        decode_responses=True,
    )
    pubsub = redis_client.pubsub()
    await pubsub.subscribe("keyjawn-worker:decisions")

    action_id = await db.log_action(
        action_type="smoke_test",
        platform="bluesky",
        content="[SMOKE TEST] This is a test of the approval flow. Tap a button below.",
        status="pending_approval",
    )

    log.info("sending test approval message (action_id: %s)", action_id)

    # Send the Telegram message with buttons (fire and forget, don't wait)
    import httpx
    from worker.telegram import format_escalation_message, build_approval_keyboard

    text = format_escalation_message(
        action_id=action_id,
        action_type="smoke_test",
        platform="bluesky",
        context="This is a test. Tap any button to verify the approval flow works.",
        draft="[SMOKE TEST] No content will be posted.",
    )
    keyboard = build_approval_keyboard(action_id)

    url = f"https://api.telegram.org/bot{config.telegram.bot_token}/sendMessage"
    payload = {
        "chat_id": config.telegram.chat_id,
        "text": text,
        "parse_mode": "HTML",
        "reply_markup": {"inline_keyboard": keyboard},
    }

    async with httpx.AsyncClient() as client:
        resp = await client.post(url, json=payload)
        if resp.status_code == 200:
            log.info("Telegram message sent. Waiting for button tap...")
        else:
            log.error("Telegram send failed (%d): %s", resp.status_code, resp.text)
            await db.close()
            await redis_client.close()
            return False

    # Listen for the response
    try:
        async for message in pubsub.listen():
            if message["type"] == "message":
                data = json.loads(message["data"])
                if data.get("action_id") == action_id:
                    decision = data["decision"]
                    log.info(
                        "received decision: %s (action_id: %s)",
                        decision,
                        action_id,
                    )
                    await approvals.process_decision(message["data"])
                    action = await db.get_action(action_id)
                    log.info(
                        "DB updated: status=%s, approval_decision=%s",
                        action["status"],
                        action["approval_decision"],
                    )
                    log.info("SMOKE TEST PASSED")
                    break
    except asyncio.TimeoutError:
        log.error("no response within timeout")
        await db.close()
        await redis_client.close()
        return False

    await db.close()
    await redis_client.close()
    return True


async def generate_calendar(config):
    """Generate this week's content calendar."""
    from datetime import date, timedelta
    from worker.calendar_gen import generate_weekly_calendar
    from worker.db import Database

    db = Database(config.db_path)
    await db.init()

    today = date.today()
    # Find the Monday of this week
    monday = today - timedelta(days=today.weekday())

    # Check if calendar entries already exist for this week
    existing = await db.get_calendar_entries(monday.isoformat())
    if existing:
        log.info(
            "calendar already has %d entries for %s, skipping",
            len(existing),
            monday.isoformat(),
        )
        # Show existing entries
        for entry in existing:
            log.info(
                "  %s | %s | %s | %s",
                entry["scheduled_date"],
                entry["platform"],
                entry["pillar"],
                entry["content_draft"],
            )
        await db.close()
        return

    count = await generate_weekly_calendar(config, db, monday)
    log.info("generated %d calendar entries for week of %s", count, monday)

    # Show the generated entries
    for day_offset in range(7):
        current = monday + timedelta(days=day_offset)
        if current.weekday() >= 5:
            continue
        entries = await db.get_calendar_entries(current.isoformat())
        for entry in entries:
            log.info(
                "  %s | %-8s | %-14s | %s",
                entry["scheduled_date"],
                entry["platform"],
                entry["pillar"],
                entry["content_draft"],
            )

    await db.close()


async def show_status(config):
    """Show current worker status: DB stats, calendar, recent actions."""
    from worker.db import Database

    db = Database(config.db_path)
    await db.init()

    tables = await db.list_tables()
    log.info("tables: %s", ", ".join(tables))

    # Findings
    cursor = await db._db.execute("SELECT COUNT(*) FROM findings")
    row = await cursor.fetchone()
    log.info("findings: %d total", row[0])

    cursor = await db._db.execute(
        "SELECT COUNT(*) FROM findings WHERE status = 'queued'"
    )
    row = await cursor.fetchone()
    log.info("  queued: %d", row[0])

    # Actions
    cursor = await db._db.execute("SELECT COUNT(*) FROM actions")
    row = await cursor.fetchone()
    log.info("actions: %d total", row[0])

    cursor = await db._db.execute(
        "SELECT * FROM actions ORDER BY acted_at DESC LIMIT 5"
    )
    rows = await cursor.fetchall()
    for r in rows:
        log.info(
            "  %s | %s | %s | %s | %s",
            dict(r)["id"],
            dict(r)["action_type"],
            dict(r)["platform"],
            dict(r)["status"],
            dict(r)["acted_at"][:19],
        )

    # Calendar
    cursor = await db._db.execute("SELECT COUNT(*) FROM calendar")
    row = await cursor.fetchone()
    log.info("calendar: %d total entries", row[0])

    cursor = await db._db.execute(
        "SELECT COUNT(*) FROM calendar WHERE status = 'planned'"
    )
    row = await cursor.fetchone()
    log.info("  planned: %d", row[0])

    await db.close()


def main():
    parser = argparse.ArgumentParser(description="keyjawn-worker management commands")
    sub = parser.add_subparsers(dest="command")

    sub.add_parser("smoke-test", help="Test the full Telegram approval flow")
    sub.add_parser("generate-calendar", help="Generate this week's content calendar")
    sub.add_parser("status", help="Show worker DB status")

    args = parser.parse_args()

    if not args.command:
        parser.print_help()
        sys.exit(1)

    from worker.config import Config

    config = Config.from_pass()

    if args.command == "smoke-test":
        success = asyncio.run(smoke_test(config))
        sys.exit(0 if success else 1)
    elif args.command == "generate-calendar":
        asyncio.run(generate_calendar(config))
    elif args.command == "status":
        asyncio.run(show_status(config))


if __name__ == "__main__":
    main()
