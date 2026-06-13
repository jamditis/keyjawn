import logging
from datetime import datetime, timezone
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, EmailStr
from db import get_db
from email_sender import send_download_email

log = logging.getLogger("keyjawn-store")
router = APIRouter()

_GENERIC_OK = {"status": "ok", "message": "If that email is registered, you'll receive a download link shortly."}


class DownloadRequest(BaseModel):
    email: EmailStr


@router.post("/api/download", status_code=202)
async def download(req: DownloadRequest):
    conn = get_db()
    user = conn.execute("SELECT * FROM users WHERE email = ?", (req.email,)).fetchone()

    if not user:
        conn.close()
        log.info("download requested for unknown email (not disclosed to caller)")
        return _GENERIC_OK

    # Rate limit: 5 download emails per day
    today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    count = conn.execute("""
        SELECT COUNT(*) FROM downloads
        WHERE user_id = ? AND downloaded_at >= ?
    """, (user["id"], today)).fetchone()[0]
    if count >= 5:
        conn.close()
        raise HTTPException(429, "Download limit reached. Try again tomorrow.")

    # Log download and send link via email
    conn.execute("INSERT INTO downloads (user_id, version) VALUES (?, (SELECT version FROM releases ORDER BY released_at DESC LIMIT 1))", (user["id"],))
    conn.execute("UPDATE users SET download_count = download_count + 1, last_download_at = datetime('now') WHERE id = ?", (user["id"],))
    conn.commit()
    conn.close()

    send_download_email(req.email)
    return _GENERIC_OK
