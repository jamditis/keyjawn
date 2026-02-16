import os
import logging
import stripe
from fastapi import APIRouter, Request, HTTPException
from db import get_db
from email_sender import send_download_email

log = logging.getLogger("keyjawn-store")
router = APIRouter()
stripe.api_key = os.environ.get("STRIPE_API_KEY", "")
WEBHOOK_SECRET = os.environ.get("STRIPE_WEBHOOK_SECRET", "")


@router.post("/webhook/stripe")
async def stripe_webhook(request: Request):
    payload = await request.body()
    sig = request.headers.get("stripe-signature")
    if not sig:
        raise HTTPException(400, "Missing stripe-signature header")

    try:
        event = stripe.Webhook.construct_event(payload, sig, WEBHOOK_SECRET)
    except (stripe.error.SignatureVerificationError, ValueError):
        raise HTTPException(400, "Invalid signature")

    if event["type"] == "checkout.session.completed":
        session = event["data"]["object"]
        email = session["customer_details"]["email"]
        customer_id = session.get("customer")
        payment_intent = session.get("payment_intent")
        amount = session.get("amount_total", 400)

        conn = get_db()
        conn.execute("""
            INSERT INTO users (email, stripe_customer_id, stripe_payment_intent, amount_cents)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(email) DO NOTHING
        """, [email, customer_id, payment_intent, amount])
        conn.commit()
        conn.close()

        send_download_email(email)

    return {"status": "ok"}
