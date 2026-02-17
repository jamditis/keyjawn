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


async def weekly_report(config):
    """Generate a weekly metrics report: actions taken, approval rates, platform breakdown."""
    from datetime import date, timedelta
    from worker.db import Database

    db = Database(config.db_path)
    await db.init()

    today = date.today()
    week_ago = (today - timedelta(days=7)).isoformat()

    # Actions this week by status
    cursor = await db._db.execute(
        "SELECT status, COUNT(*) as cnt FROM actions WHERE acted_at >= ? GROUP BY status ORDER BY cnt DESC",
        (week_ago,),
    )
    rows = await cursor.fetchall()
    total_actions = sum(dict(r)["cnt"] for r in rows)
    log.info("=== Weekly report (%s to %s) ===", week_ago, today.isoformat())
    log.info("actions: %d total", total_actions)
    for r in rows:
        d = dict(r)
        log.info("  %s: %d", d["status"], d["cnt"])

    # Actions by platform
    cursor = await db._db.execute(
        "SELECT platform, COUNT(*) as cnt FROM actions WHERE acted_at >= ? AND status = 'posted' GROUP BY platform",
        (week_ago,),
    )
    rows = await cursor.fetchall()
    log.info("posted by platform:")
    for r in rows:
        d = dict(r)
        log.info("  %s: %d", d["platform"], d["cnt"])

    # Approval rates
    cursor = await db._db.execute(
        "SELECT approval_decision, COUNT(*) as cnt FROM actions WHERE acted_at >= ? AND approval_decision IS NOT NULL GROUP BY approval_decision",
        (week_ago,),
    )
    rows = await cursor.fetchall()
    if rows:
        log.info("approval decisions:")
        for r in rows:
            d = dict(r)
            log.info("  %s: %d", d["approval_decision"], d["cnt"])

    # Findings queued this week
    cursor = await db._db.execute(
        "SELECT COUNT(*) FROM findings WHERE found_at >= ?", (week_ago,)
    )
    row = await cursor.fetchone()
    log.info("findings queued: %d", row[0])

    # Calendar completion rate
    cursor = await db._db.execute(
        "SELECT status, COUNT(*) as cnt FROM calendar WHERE scheduled_date >= ? AND scheduled_date <= ? GROUP BY status",
        (week_ago, today.isoformat()),
    )
    rows = await cursor.fetchall()
    if rows:
        log.info("calendar entries:")
        for r in rows:
            d = dict(r)
            log.info("  %s: %d", d["status"], d["cnt"])

    # Try to get store stats via localhost API (if reachable via SSH tunnel or direct)
    try:
        import httpx
        async with httpx.AsyncClient(timeout=5) as client:
            resp = await client.get("http://100.122.208.15:5060/api/health")
            if resp.status_code == 200:
                log.info("store API: reachable")
    except Exception:
        log.info("store API: not reachable from this machine")

    await db.close()


async def curation_scan(config):
    """Run a one-shot curation scan and evaluation."""
    from worker.curation.monitor import CurationMonitor
    from worker.db import Database

    db = Database(config.db_path)
    await db.init()

    monitor = CurationMonitor(config.curation, db)

    log.info("scanning sources...")
    candidates = await monitor.scan_sources(include_twitch=True)
    log.info("found %d candidates", len(candidates))

    stored = await monitor.store_candidates(candidates)
    log.info("stored %d new (deduped from %d)", stored, len(candidates))

    log.info("running evaluation pipeline...")
    approved = await monitor.run_evaluation()
    log.info("approved %d candidates", len(approved))

    for c in approved:
        log.info(
            "  [%.2f] %s | %s | %s",
            c.final_score,
            c.source,
            c.title[:60],
            c.sonnet_draft[:80] if c.sonnet_draft else "(no draft)",
        )

    await db.close()


async def curation_status(config):
    """Show curation pipeline status."""
    from worker.db import Database

    db = Database(config.db_path)
    await db.init()

    # Count by status
    cursor = await db._db.execute(
        "SELECT status, COUNT(*) as cnt FROM curation_candidates GROUP BY status ORDER BY cnt DESC"
    )
    rows = await cursor.fetchall()
    log.info("=== Curation status ===")
    total = sum(dict(r)["cnt"] for r in rows)
    log.info("total candidates: %d", total)
    for r in rows:
        d = dict(r)
        log.info("  %s: %d", d["status"], d["cnt"])

    # Today's budget
    curation_today = await db.get_daily_curation_count()
    budget = config.curation.max_curated_shares_per_day
    log.info("today: %d/%d curated shares posted", curation_today, budget)

    # Recent approved
    approved = await db.get_approved_curations(limit=5)
    if approved:
        log.info("pending approved:")
        for a in approved:
            log.info(
                "  [%.2f] %s | %s",
                a.get("final_score", 0),
                a["source"],
                a["title"][:60],
            )

    await db.close()


def main():
    parser = argparse.ArgumentParser(description="keyjawn-worker management commands")
    sub = parser.add_subparsers(dest="command")

    sub.add_parser("smoke-test", help="Test the full Telegram approval flow")
    sub.add_parser("generate-calendar", help="Generate this week's content calendar")
    sub.add_parser("status", help="Show worker DB status")
    sub.add_parser("weekly-report", help="Generate weekly metrics report")
    sub.add_parser("curation-scan", help="Run one-shot curation scan and evaluation")
    sub.add_parser("curation-status", help="Show curation pipeline status")
    sub.add_parser("discovery-scan", help="Run on-platform discovery scan")

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
    elif args.command == "weekly-report":
        asyncio.run(weekly_report(config))
    elif args.command == "curation-scan":
        asyncio.run(curation_scan(config))
    elif args.command == "curation-status":
        asyncio.run(curation_status(config))
    elif args.command == "discovery-scan":
        async def _scan():
            from worker.runner import WorkerRunner
            runner = WorkerRunner(config)
            await runner.start()
            await runner.run_discovery_scan()
            await runner.stop()
        asyncio.run(_scan())


if __name__ == "__main__":
    main()
