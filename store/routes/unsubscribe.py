import hashlib
import hmac
import logging
import os

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import HTMLResponse

from db import get_db

log = logging.getLogger("keyjawn-store")
router = APIRouter()

UNSUBSCRIBE_SECRET = os.environ.get("UNSUBSCRIBE_SECRET", "keyjawn-unsub-default")


def make_unsubscribe_token(email: str) -> str:
    """HMAC token so users can only unsubscribe themselves."""
    return hmac.new(
        UNSUBSCRIBE_SECRET.encode(), email.lower().encode(), hashlib.sha256
    ).hexdigest()[:16]


def make_unsubscribe_url(email: str) -> str:
    token = make_unsubscribe_token(email)
    base = os.environ.get("BASE_URL", "https://keyjawn-store.amditis.tech")
    return f"{base}/unsubscribe?email={email}&token={token}"


@router.get("/unsubscribe", response_class=HTMLResponse)
async def unsubscribe(email: str = "", token: str = ""):
    if not email or not token:
        raise HTTPException(400, "Missing email or token")

    expected = make_unsubscribe_token(email)
    if not hmac.compare_digest(token, expected):
        raise HTTPException(400, "Invalid unsubscribe link")

    conn = get_db()
    conn.execute("UPDATE users SET unsubscribed = 1 WHERE email = ?", (email,))
    conn.commit()
    conn.close()

    log.info("unsubscribed: %s", email)

    return """<!DOCTYPE html>
<html>
<head><meta charset="utf-8"><title>Unsubscribed</title></head>
<body style="margin:0; padding:0; background:#f4f4f7; font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;">
  <table width="100%" cellpadding="0" cellspacing="0" style="background:#f4f4f7; padding:64px 0;">
    <tr><td align="center">
      <table width="540" cellpadding="0" cellspacing="0" style="background:#ffffff; border-radius:8px; overflow:hidden;">
        <tr><td style="background:#1B1B1F; padding:24px 32px;">
          <span style="color:#6cf2a8; font-size:22px; font-weight:700;">KeyJawn</span>
        </td></tr>
        <tr><td style="padding:32px;">
          <p style="margin:0 0 16px; font-size:16px; color:#1a1a1a; line-height:1.5;">
            You've been unsubscribed from KeyJawn update emails.
          </p>
          <p style="margin:0; font-size:14px; color:#555; line-height:1.5;">
            You can still download your purchased version anytime at
            <a href="https://keyjawn.amditis.tech" style="color:#1a73e8;">keyjawn.amditis.tech</a>.
          </p>
        </td></tr>
      </table>
    </td></tr>
  </table>
</body>
</html>"""
