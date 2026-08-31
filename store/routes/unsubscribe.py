import hashlib
import hmac
import logging
import os
from html import escape
from urllib.parse import urlencode

from fastapi import APIRouter, HTTPException
from fastapi.responses import HTMLResponse

from db import get_db, normalize_email

log = logging.getLogger("keyjawn-store")
router = APIRouter()

UNSUBSCRIBE_SECRET = os.environ.get("UNSUBSCRIBE_SECRET", "")
if not UNSUBSCRIBE_SECRET:
    raise RuntimeError("UNSUBSCRIBE_SECRET env var must be set before starting the store service")


def make_unsubscribe_token(email: str) -> str:
    """HMAC token so users can only unsubscribe themselves."""
    return hmac.new(
        UNSUBSCRIBE_SECRET.encode(), normalize_email(email).encode(), hashlib.sha256
    ).hexdigest()[:16]


def make_unsubscribe_url(email: str) -> str:
    normalized_email = normalize_email(email)
    query = urlencode(
        {
            "email": normalized_email,
            "token": make_unsubscribe_token(normalized_email),
        }
    )
    base = os.environ.get("BASE_URL", "https://keyjawn-store.amditis.tech")
    return f"{base}/unsubscribe?{query}"


def validated_email(email: str, token: str) -> str:
    """Validate a signed unsubscribe request and return its email identity."""
    if not email or not token:
        raise HTTPException(400, "Missing email or token")

    normalized_email = normalize_email(email)
    expected = make_unsubscribe_token(normalized_email)
    if not hmac.compare_digest(token.encode("utf-8"), expected.encode("ascii")):
        raise HTTPException(400, "Invalid unsubscribe link")
    return normalized_email


def render_unsubscribe_page(title: str, description: str, content: str) -> str:
    """Render one metadata-complete unsubscribe page."""
    safe_title = escape(title)
    safe_description = escape(description)
    return f"""<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <meta name="robots" content="noindex, nofollow">
  <meta name="description" content="{safe_description}">
  <meta property="og:title" content="{safe_title}">
  <meta property="og:description" content="{safe_description}">
  <meta property="og:type" content="website">
  <meta property="og:image" content="https://keyjawn.amditis.tech/og-image.png">
  <meta property="og:image:alt" content="KeyJawn terminal keyboard">
  <title>{safe_title}</title>
  <link rel="icon" type="image/svg+xml" href="/static/favicon.svg">
</head>
<body style="margin:0; padding:0; background:#f4f4f7; font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;">
  <table width="100%" cellpadding="0" cellspacing="0" style="background:#f4f4f7; padding:64px 0;">
    <tr><td align="center">
      <table width="540" cellpadding="0" cellspacing="0" style="background:#ffffff; border-radius:8px; overflow:hidden;">
        <tr><td style="background:#1B1B1F; padding:24px 32px;">
          <span style="color:#6cf2a8; font-size:22px; font-weight:700;">KeyJawn</span>
        </td></tr>
        <tr><td style="padding:32px;">
          {content}
        </td></tr>
      </table>
    </td></tr>
  </table>
</body>
</html>"""


@router.get("/unsubscribe", response_class=HTMLResponse)
async def confirm_unsubscribe(email: str = "", token: str = ""):
    normalized_email = validated_email(email, token)
    query = urlencode(
        {
            "email": normalized_email,
            "token": make_unsubscribe_token(normalized_email),
        }
    )
    action = escape(f"/unsubscribe?{query}", quote=True)
    content = f"""
          <p style="margin:0 0 16px; font-size:16px; color:#1a1a1a; line-height:1.5;">
            Confirm that you want to stop KeyJawn update emails.
          </p>
          <form method="post" action="{action}" style="margin:24px 0 0;">
            <button type="submit" style="background:#1B1B1F; color:#6cf2a8; border:0; border-radius:6px; padding:12px 20px; font-size:15px; font-weight:600; cursor:pointer;">
              Unsubscribe
            </button>
          </form>"""
    return render_unsubscribe_page(
        "Confirm unsubscribe",
        "Confirm that you want to stop KeyJawn update emails.",
        content,
    )


@router.post("/unsubscribe", response_class=HTMLResponse)
async def unsubscribe(email: str = "", token: str = ""):
    normalized_email = validated_email(email, token)

    conn = get_db()
    conn.execute(
        "UPDATE users SET unsubscribed = 1 WHERE email = ? COLLATE NOCASE",
        (normalized_email,),
    )
    conn.commit()
    conn.close()

    log.info("unsubscribed: %s", normalized_email)

    content = """
          <p style="margin:0 0 16px; font-size:16px; color:#1a1a1a; line-height:1.5;">
            You've been unsubscribed from KeyJawn update emails.
          </p>
          <p style="margin:0; font-size:14px; color:#555; line-height:1.5;">
            You can still download your purchased version anytime at
            <a href="https://keyjawn.amditis.tech" style="color:#1a73e8;">keyjawn.amditis.tech</a>.
          </p>"""
    return render_unsubscribe_page(
        "Unsubscribed from KeyJawn",
        "KeyJawn email unsubscribe confirmation.",
        content,
    )
