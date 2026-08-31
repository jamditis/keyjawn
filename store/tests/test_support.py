import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import asyncio

from fastapi import BackgroundTasks
from fastapi.testclient import TestClient
from unittest.mock import patch


GENERIC_SUPPORT_RESPONSE = {
    "status": "ok",
    "message": "If that email is registered, we'll follow up shortly.",
}


def setup_test_user():
    from db import get_db, init_db
    init_db()
    conn = get_db()
    conn.execute("DELETE FROM tickets")
    conn.execute("DELETE FROM downloads")
    conn.execute("DELETE FROM users")
    conn.execute("""
        INSERT INTO users (email, stripe_customer_id, amount_cents)
        VALUES ('buyer@example.com', 'cus_123', 400)
    """)
    conn.commit()
    conn.close()


def test_support_does_not_disclose_non_purchaser():
    setup_test_user()
    from app import app
    client = TestClient(app)
    with patch("routes.support.send_telegram_alert") as send_telegram_alert:
        with patch("routes.support.send_ticket_confirmation") as send_ticket_confirmation:
            resp = client.post("/api/support", json={
                "email": "nobody@example.com",
                "subject": "Bug",
                "body": "Something broke"
            })

    assert resp.status_code == 202
    assert resp.json() == GENERIC_SUPPORT_RESPONSE
    send_telegram_alert.assert_not_called()
    send_ticket_confirmation.assert_not_called()


def test_support_creates_ticket():
    setup_test_user()
    from app import app
    client = TestClient(app)
    with patch("routes.support.send_telegram_alert"):
        with patch("routes.support.send_ticket_confirmation"):
            resp = client.post("/api/support", json={
                "email": "buyer@example.com",
                "subject": "Ctrl key bug",
                "body": "Ctrl doesn't lock on double-tap",
                "device_model": "Samsung S24 Ultra",
                "android_version": "15",
                "app_version": "0.2.0"
            })
    assert resp.status_code == 202
    assert resp.json() == GENERIC_SUPPORT_RESPONSE

    from db import get_db

    conn = get_db()
    ticket = conn.execute("SELECT id FROM tickets").fetchone()
    conn.close()
    assert ticket["id"] > 0


def test_support_matches_legacy_email_without_case_sensitivity():
    setup_test_user()
    from app import app
    from db import get_db

    conn = get_db()
    conn.execute(
        "UPDATE users SET email = ? WHERE email = ?",
        ("Buyer@Example.com", "buyer@example.com"),
    )
    conn.commit()
    conn.close()

    client = TestClient(app)
    with patch("routes.support.send_telegram_alert"):
        with patch("routes.support.send_ticket_confirmation") as confirmation:
            resp = client.post(
                "/api/support",
                json={
                    "email": "buyer@example.com",
                    "subject": "Ctrl key bug",
                    "body": "Ctrl does not lock on double-tap",
                },
            )

    assert resp.status_code == 202
    assert resp.json() == GENERIC_SUPPORT_RESPONSE

    conn = get_db()
    ticket = conn.execute("SELECT id FROM tickets").fetchone()
    conn.close()
    confirmation.assert_called_once_with(
        "Buyer@Example.com", "Ctrl key bug", ticket["id"]
    )


def test_support_schedules_notifications_after_response():
    setup_test_user()
    from routes.support import SupportRequest, create_ticket

    background_tasks = BackgroundTasks()
    request = SupportRequest(
        email="buyer@example.com",
        subject="Keyboard issue",
        body="The key did not respond",
    )
    with patch("routes.support.send_telegram_alert") as telegram:
        with patch("routes.support.send_ticket_confirmation") as confirmation:
            response = asyncio.run(create_ticket(request, background_tasks))

    assert response == GENERIC_SUPPORT_RESPONSE
    assert len(background_tasks.tasks) == 2
    telegram.assert_not_called()
    confirmation.assert_not_called()
