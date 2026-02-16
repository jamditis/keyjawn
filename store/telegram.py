import os
import html
import logging
import httpx

log = logging.getLogger("keyjawn-store")
BOT_TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "")
CHAT_ID = os.environ.get("TELEGRAM_CHAT_ID", "")


def send_telegram_alert(message: str):
    if not BOT_TOKEN or not CHAT_ID:
        log.warning("Telegram not configured, skipping alert")
        return
    try:
        escaped = html.escape(message)
        httpx.post(
            f"https://api.telegram.org/bot{BOT_TOKEN}/sendMessage",
            json={"chat_id": CHAT_ID, "text": escaped, "parse_mode": "HTML"},
            timeout=10,
        )
    except Exception as e:
        log.error(f"Telegram alert failed: {e}")
