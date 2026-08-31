import logging
from fastapi import APIRouter, BackgroundTasks
from pydantic import BaseModel, EmailStr
from db import get_db, normalize_email
from email_sender import send_download_email

log = logging.getLogger("keyjawn-store")
router = APIRouter()

_GENERIC_OK = {"status": "ok", "message": "If that email is registered, you'll receive a download link shortly."}
_DAILY_ATTEMPT_LIMIT = 5


class DownloadRequest(BaseModel):
    email: EmailStr


def deliver_download_email(download_id: int, user_id: int, recipient: str) -> None:
    """Deliver one download email and retain the attempt for rate limiting."""
    try:
        delivered = send_download_email(recipient)
    except Exception:
        log.exception("download email raised for user %s", user_id)
        delivered = False

    conn = get_db()
    try:
        if delivered:
            conn.execute(
                """
                UPDATE users
                SET download_count = download_count + 1,
                    last_download_at = datetime('now')
                WHERE id = ?
                """,
                (user_id,),
            )
        conn.commit()
    finally:
        conn.close()


@router.post("/api/download", status_code=202)
async def download(req: DownloadRequest, background_tasks: BackgroundTasks):
    email = normalize_email(req.email)
    conn = get_db()
    try:
        user = conn.execute(
            "SELECT * FROM users WHERE email = ? COLLATE NOCASE", (email,)
        ).fetchone()

        if not user:
            log.info("download requested for unknown email (not disclosed to caller)")
            return _GENERIC_OK

        conn.execute("BEGIN IMMEDIATE")
        daily_attempts = conn.execute("""
            SELECT COUNT(*) FROM downloads
            WHERE user_id = ?
              AND downloaded_at >= datetime('now', 'start of day')
        """, (user["id"],)).fetchone()[0]
        if daily_attempts >= _DAILY_ATTEMPT_LIMIT:
            conn.rollback()
            log.info("download email suppressed by daily limit for user %s", user["id"])
            return _GENERIC_OK

        recent_request = conn.execute("""
            SELECT 1 FROM downloads
            WHERE user_id = ?
              AND downloaded_at >= datetime('now', '-10 minutes')
            LIMIT 1
        """, (user["id"],)).fetchone()
        if recent_request:
            conn.rollback()
            log.info("download email suppressed by cooldown for user %s", user["id"])
            return _GENERIC_OK

        release = conn.execute(
            "SELECT version FROM releases ORDER BY released_at DESC LIMIT 1"
        ).fetchone()
        version = release["version"] if release else "latest"
        result = conn.execute(
            "INSERT INTO downloads (user_id, version) VALUES (?, ?)",
            (user["id"], version),
        )
        conn.commit()
        download_id = result.lastrowid
        user_id = user["id"]
        recipient = user["email"]
    finally:
        conn.close()

    background_tasks.add_task(
        deliver_download_email, download_id, user_id, recipient
    )
    return _GENERIC_OK
