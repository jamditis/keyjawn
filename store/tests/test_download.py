import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import pytest
from fastapi.testclient import TestClient
from unittest.mock import patch


def setup_test_user():
    from db import get_db, init_db
    init_db()
    conn = get_db()
    conn.execute("DELETE FROM tickets")
    conn.execute("DELETE FROM downloads")
    conn.execute("DELETE FROM users")
    conn.execute("DELETE FROM releases")
    conn.execute("""
        INSERT INTO users (email, stripe_customer_id, amount_cents)
        VALUES ('buyer@example.com', 'cus_123', 400)
    """)
    conn.execute("""
        INSERT INTO releases (version, r2_key, file_size)
        VALUES ('0.2.0', 'keyjawn/releases/keyjawn-full-v0.2.0.apk', 5000000)
    """)
    conn.commit()
    conn.close()


def test_download_rejects_unknown_email():
    setup_test_user()
    from app import app
    client = TestClient(app)
    resp = client.post("/api/download", json={"email": "nobody@example.com"})
    assert resp.status_code == 404


def test_download_returns_url_for_paid_user():
    setup_test_user()
    from app import app
    client = TestClient(app)
    with patch("routes.download.generate_signed_url", return_value="https://r2.example.com/keyjawn.apk?sig=abc"):
        resp = client.post("/api/download", json={"email": "buyer@example.com"})
    assert resp.status_code == 200
    data = resp.json()
    assert "url" in data
    assert "version" in data
