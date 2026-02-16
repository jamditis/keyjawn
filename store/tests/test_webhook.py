import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import json
import pytest
from unittest.mock import patch, MagicMock
from fastapi.testclient import TestClient


def test_webhook_rejects_missing_signature():
    from app import app
    client = TestClient(app)
    resp = client.post("/webhook/stripe", content=b"{}", headers={})
    assert resp.status_code == 400


def test_webhook_creates_user_on_valid_event():
    from app import app
    from db import get_db, init_db
    init_db()
    client = TestClient(app)

    fake_event = {
        "type": "checkout.session.completed",
        "data": {
            "object": {
                "customer_details": {"email": "test@example.com"},
                "customer": "cus_test123",
                "payment_intent": "pi_test123",
                "amount_total": 400
            }
        }
    }

    with patch("routes.webhook.stripe.Webhook.construct_event", return_value=fake_event):
        with patch("routes.webhook.send_download_email"):
            resp = client.post(
                "/webhook/stripe",
                content=json.dumps(fake_event),
                headers={"stripe-signature": "fake_sig"}
            )

    assert resp.status_code == 200
    conn = get_db()
    user = conn.execute("SELECT * FROM users WHERE email = ?", ("test@example.com",)).fetchone()
    conn.close()
    assert user is not None
    assert user["stripe_customer_id"] == "cus_test123"
