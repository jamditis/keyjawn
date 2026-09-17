"""Weekly content calendar generator.

Distributes content pillars across weekdays and platforms,
producing a week of planned entries in the database.
"""

import itertools
from datetime import date, timedelta

PILLARS = ["awareness", "demo", "engagement", "social_proof", "behind_scenes"]

TOPICS = {
    "awareness": [
        "Android: standard keyboards failing at CLI tasks",
        "Android: autocorrect mangling shell commands",
        "Android: missing Esc, Tab, and Ctrl on mobile",
        "Android: the pain of arrow keys on touch keyboards",
        "Android: auto-capitalization, double-space period, and key-specific haptics",
        "iOS: terminal commands execute on a configured remote SSH server",
    ],
    "demo": [
        "Android: voice input composing a CLI prompt",
        "Android: terminal key row in action",
        "Android full: SCP image upload during a remote session",
        "Android: slash command shortcuts",
        "Android: hold and drag the spacebar to move the cursor",
        "Android: adaptive Enter key for Go, Send, Search, Next, and Done",
        "iOS: remote SSH terminal and companion keyboard in a supported text field",
    ],
    "engagement": [
        "Reply to mobile SSH conversations",
        "Help someone with their CLI workflow",
        "Share tips for phone-based development",
    ],
    "social_proof": [
        "Download/purchase milestone update",
        "Share user feedback or support resolution",
        "GitHub activity update",
        "iOS App Store status: approved; manual developer release pending",
    ],
    "behind_scenes": [
        "What Joe is working on next",
        "Open source philosophy and $4 pricing",
        "Dev update on upcoming features",
        "How iOS keeps remote command execution separate from the device",
        "Where iOS supports third-party keyboards and where it uses the system keyboard",
    ],
}

PLATFORMS = ["twitter", "bluesky"]


async def generate_weekly_calendar(config, db, start_date: date) -> int:
    """Generate a week of content calendar entries starting from start_date (a Monday).

    Skips weekends. Rotates through pillars and platforms. Adds a bonus
    engagement entry on even day offsets.

    Returns the total number of entries created.
    """
    pillar_cycle = itertools.cycle(PILLARS)
    platform_cycle = itertools.cycle(PLATFORMS)
    topic_cycles = {p: itertools.cycle(TOPICS[p]) for p in PILLARS}

    count = 0

    for day_offset in range(7):
        current_date = start_date + timedelta(days=day_offset)

        # Skip weekends
        if current_date.weekday() >= 5:
            continue

        pillar = next(pillar_cycle)
        platform = next(platform_cycle)
        topic = next(topic_cycles[pillar])

        await db.add_calendar_entry(
            scheduled_date=current_date.isoformat(),
            pillar=pillar,
            platform=platform,
            content_draft=topic,
            status="planned",
        )
        count += 1

        # Bonus engagement entry on even day offsets
        if day_offset % 2 == 0:
            engagement_topic = next(topic_cycles["engagement"])
            engagement_platform = next(platform_cycle)
            await db.add_calendar_entry(
                scheduled_date=current_date.isoformat(),
                pillar="engagement",
                platform=engagement_platform,
                content_draft=engagement_topic,
                status="planned",
            )
            count += 1

    return count
