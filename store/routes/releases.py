import os
import logging
from fastapi import APIRouter, HTTPException, Header
from pydantic import BaseModel
from typing import Optional
from db import get_db
from email_sender import send_update_email
from telegram import send_telegram_alert

log = logging.getLogger("keyjawn-store")
router = APIRouter()


def require_admin(authorization: Optional[str] = Header(None)):
    admin_token = os.environ.get("ADMIN_TOKEN", "")
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Missing or invalid token")
    token = authorization.split(" ", 1)[1]
    if token != admin_token:
        raise HTTPException(401, "Invalid token")


class ReleaseRequest(BaseModel):
    version: str
    r2_key: str
    file_size: Optional[int] = None
    sha256: Optional[str] = None
    changelog: Optional[str] = None


@router.post("/api/releases")
async def register_release(req: ReleaseRequest, authorization: Optional[str] = Header(None)):
    require_admin(authorization)
    conn = get_db()
    conn.execute("""
        INSERT INTO releases (version, r2_key, file_size, sha256, changelog)
        VALUES (?, ?, ?, ?, ?)
        ON CONFLICT(version) DO UPDATE SET r2_key=?, file_size=?, sha256=?, changelog=?
    """, (req.version, req.r2_key, req.file_size, req.sha256, req.changelog,
          req.r2_key, req.file_size, req.sha256, req.changelog))
    conn.commit()
    conn.close()
    return {"version": req.version, "status": "registered"}


@router.post("/api/releases/{version}/notify")
async def notify_purchasers(version: str, authorization: Optional[str] = Header(None)):
    """Send update emails to all purchasers for a new release."""
    require_admin(authorization)

    conn = get_db()
    release = conn.execute("SELECT * FROM releases WHERE version = ?", (version,)).fetchone()
    if not release:
        conn.close()
        raise HTTPException(404, f"Release {version} not found")

    changelog = release["changelog"] or ""
    users = conn.execute("SELECT email FROM users WHERE unsubscribed = 0").fetchall()
    conn.close()

    sent = 0
    failed = 0
    for user in users:
        try:
            send_update_email(user["email"], version, changelog)
            sent += 1
        except Exception as e:
            log.error(f"Failed to notify {user['email']}: {e}")
            failed += 1

    send_telegram_alert(f"KeyJawn v{version} update emails: {sent} sent, {failed} failed")
    return {"version": version, "sent": sent, "failed": failed}
