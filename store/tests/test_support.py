import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from fastapi.testclient import TestClient
from unittest.mock import patch


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


def test_support_rejects_non_purchaser():
    setup_test_user()
    from app import app
    client = TestClient(app)
    resp = client.post("/api/support", json={
        "email": "nobody@example.com",
        "subject": "Bug",
        "body": "Something broke"
    })
    assert resp.status_code == 403


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
    assert resp.status_code == 200
    assert resp.json()["ticket_id"] > 0
