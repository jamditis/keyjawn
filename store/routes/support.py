import logging
from fastapi import APIRouter, BackgroundTasks
from pydantic import BaseModel, EmailStr
from typing import Optional
from db import get_db, normalize_email
from telegram import send_telegram_alert
from email_sender import send_ticket_confirmation

log = logging.getLogger("keyjawn-store")
router = APIRouter()

_GENERIC_OK = {
    "status": "ok",
    "message": "If that email is registered, we'll follow up shortly.",
}


class SupportRequest(BaseModel):
    email: EmailStr
    subject: str
    body: str
    device_model: Optional[str] = None
    android_version: Optional[str] = None
    app_version: Optional[str] = None

@router.post("/api/support", status_code=202)
async def create_ticket(req: SupportRequest, background_tasks: BackgroundTasks):
    email = normalize_email(req.email)
    conn = get_db()
    user = conn.execute(
        "SELECT * FROM users WHERE email = ? COLLATE NOCASE", (email,)
    ).fetchone()

    if not user:
        conn.close()
        log.info("support ticket from unknown email (not disclosed to caller)")
        return _GENERIC_OK

    cursor = conn.execute("""
        INSERT INTO tickets (user_id, subject, body, device_model, android_version, app_version)
        VALUES (?, ?, ?, ?, ?, ?)
    """, (user["id"], req.subject, req.body, req.device_model, req.android_version, req.app_version))
    ticket_id = cursor.lastrowid
    conn.commit()
    conn.close()

    background_tasks.add_task(
        send_telegram_alert,
        f"New keyjawn support ticket #{ticket_id} from {user['email']}: {req.subject}",
    )
    background_tasks.add_task(
        send_ticket_confirmation, user["email"], req.subject, ticket_id
    )

    return _GENERIC_OK
