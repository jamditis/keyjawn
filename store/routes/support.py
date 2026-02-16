from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, EmailStr
from typing import Optional
from db import get_db
from telegram import send_telegram_alert
from email_sender import send_ticket_confirmation

router = APIRouter()


class SupportRequest(BaseModel):
    email: EmailStr
    subject: str
    body: str
    device_model: Optional[str] = None
    android_version: Optional[str] = None
    app_version: Optional[str] = None


@router.post("/api/support")
async def create_ticket(req: SupportRequest):
    conn = get_db()
    user = conn.execute("SELECT * FROM users WHERE email = ?", (req.email,)).fetchone()
    if not user:
        conn.close()
        raise HTTPException(403, "Email not found. Priority support is for paid users. For general help, use GitHub Issues.")

    cursor = conn.execute("""
        INSERT INTO tickets (user_id, subject, body, device_model, android_version, app_version)
        VALUES (?, ?, ?, ?, ?, ?)
    """, (user["id"], req.subject, req.body, req.device_model, req.android_version, req.app_version))
    ticket_id = cursor.lastrowid
    conn.commit()
    conn.close()

    send_telegram_alert(f"New keyjawn support ticket #{ticket_id} from {req.email}: {req.subject}")
    send_ticket_confirmation(req.email, req.subject, ticket_id)

    return {"ticket_id": ticket_id, "status": "open"}
