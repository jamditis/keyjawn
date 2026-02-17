"""Entry point for keyjawn-worker."""

import asyncio
import logging
import sys
from zoneinfo import ZoneInfo

from apscheduler.schedulers.asyncio import AsyncIOScheduler

from worker.config import Config
from worker.runner import WorkerRunner

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(name)s %(levelname)s %(message)s",
)
logger = logging.getLogger(__name__)

ET = ZoneInfo("America/New_York")


async def main():
    config = Config.from_pass()
    runner = WorkerRunner(config)
    await runner.start()

    scheduler = AsyncIOScheduler(timezone=ET)

    # Monitor scan: every 45 minutes during business hours (9am-9pm ET, weekdays)
    scheduler.add_job(
        runner.run_monitor_scan,
        "cron",
        day_of_week="mon-fri",
        hour="9-20",
        minute="*/45",
        id="monitor_scan",
    )

    # Action session: once per evening at 7pm ET
    scheduler.add_job(
        runner.run_action_session,
        "cron",
        day_of_week="mon-fri",
        hour=19,
        minute=0,
        id="action_session",
    )

    # Curation scan: YouTube + News every 6 hours
    scheduler.add_job(
        runner.run_curation_scan,
        "interval",
        hours=config.curation.scan_interval_hours,
        id="curation_scan",
    )

    # Curation scan with Twitch: every 8 hours
    scheduler.add_job(
        lambda: runner.run_curation_scan(include_twitch=True),
        "interval",
        hours=config.curation.twitch_scan_interval_hours,
        id="curation_scan_twitch",
    )

    # On-platform discovery: daily at 5pm ET (before action session at 7pm)
    scheduler.add_job(
        runner.run_discovery_scan,
        "cron",
        day_of_week="mon-fri",
        hour=17,
        minute=0,
        id="discovery_scan",
        replace_existing=True,
    )

    scheduler.start()

    # Start Redis listener for approval decisions
    listener_task = asyncio.create_task(
        runner.listen_for_decisions()
    )

    logger.info("keyjawn-worker running (Ctrl+C to stop)")

    try:
        await asyncio.Event().wait()
    except (KeyboardInterrupt, SystemExit):
        pass
    finally:
        listener_task.cancel()
        scheduler.shutdown()
        await runner.stop()


if __name__ == "__main__":
    asyncio.run(main())
