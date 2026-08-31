import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import asyncio

from fastapi import BackgroundTasks
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


def test_download_does_not_disclose_unknown_email():
    setup_test_user()
    from app import app
    client = TestClient(app)
    with patch("routes.download.send_download_email") as send_download_email:
        resp = client.post("/api/download", json={"email": "nobody@example.com"})

    assert resp.status_code == 202
    assert resp.json()["status"] == "ok"
    send_download_email.assert_not_called()


def test_download_emails_link_to_paid_user():
    setup_test_user()
    from app import app
    client = TestClient(app)
    with patch("routes.download.send_download_email") as send_download_email:
        resp = client.post("/api/download", json={"email": "buyer@example.com"})

    assert resp.status_code == 202
    assert resp.json()["status"] == "ok"
    send_download_email.assert_called_once_with("buyer@example.com")


def test_download_matches_legacy_email_without_case_sensitivity():
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
    with patch("routes.download.send_download_email") as send_download_email:
        resp = client.post("/api/download", json={"email": "buyer@example.com"})

    assert resp.status_code == 202
    send_download_email.assert_called_once_with("Buyer@Example.com")


def test_download_cooldown_is_uniform_and_does_not_send_twice():
    setup_test_user()
    from app import app

    client = TestClient(app)
    with patch("routes.download.send_download_email") as send_download_email:
        first = client.post("/api/download", json={"email": "buyer@example.com"})
        second = client.post("/api/download", json={"email": "buyer@example.com"})
        unknown = client.post("/api/download", json={"email": "nobody@example.com"})

    assert first.status_code == 202
    assert second.status_code == 202
    assert first.json() == second.json() == unknown.json()
    send_download_email.assert_called_once_with("buyer@example.com")


def test_download_daily_limit_is_uniform_and_does_not_schedule_an_email():
    setup_test_user()
    from app import app
    from db import get_db

    conn = get_db()
    user_id = conn.execute(
        "SELECT id FROM users WHERE email = ?", ("buyer@example.com",)
    ).fetchone()[0]
    for minute in range(5):
        conn.execute(
            """
            INSERT INTO downloads (user_id, version, downloaded_at)
            VALUES (?, ?, datetime('now', 'start of day', ?))
            """,
            (user_id, "0.2.0", f"+{minute} minutes"),
        )
    conn.commit()
    conn.close()

    client = TestClient(app)
    with patch("routes.download.send_download_email") as sender:
        limited = client.post("/api/download", json={"email": "buyer@example.com"})
        unknown = client.post("/api/download", json={"email": "nobody@example.com"})

    assert limited.status_code == 202
    assert limited.json() == unknown.json()
    sender.assert_not_called()

    conn = get_db()
    attempt_count = conn.execute(
        "SELECT COUNT(*) FROM downloads WHERE user_id = ?", (user_id,)
    ).fetchone()[0]
    conn.close()
    assert attempt_count == 5


def test_download_uses_latest_fallback_when_no_release_exists():
    setup_test_user()
    from app import app
    from db import get_db

    conn = get_db()
    conn.execute("DELETE FROM releases")
    conn.commit()
    conn.close()

    client = TestClient(app)
    with patch("routes.download.send_download_email") as send_download_email:
        response = client.post("/api/download", json={"email": "buyer@example.com"})

    assert response.status_code == 202
    send_download_email.assert_called_once_with("buyer@example.com")

    conn = get_db()
    download = conn.execute("SELECT version FROM downloads").fetchone()
    conn.close()
    assert download["version"] == "latest"


def test_download_schedules_delivery_after_response():
    setup_test_user()
    from routes.download import DownloadRequest, download

    background_tasks = BackgroundTasks()
    request = DownloadRequest(email="buyer@example.com")
    with patch("routes.download.send_download_email") as send_download_email:
        response = asyncio.run(download(request, background_tasks))

    assert response["status"] == "ok"
    assert len(background_tasks.tasks) == 1
    send_download_email.assert_not_called()


def test_failed_download_delivery_keeps_cooldown_and_attempt_record():
    setup_test_user()
    from app import app
    from db import get_db

    client = TestClient(app)
    with patch("routes.download.send_download_email", return_value=False) as sender:
        first = client.post("/api/download", json={"email": "buyer@example.com"})
        second = client.post("/api/download", json={"email": "buyer@example.com"})

    assert first.status_code == second.status_code == 202
    assert sender.call_count == 1

    conn = get_db()
    download_count = conn.execute("SELECT COUNT(*) FROM downloads").fetchone()[0]
    user_count = conn.execute(
        "SELECT download_count FROM users WHERE email = ?", ("buyer@example.com",)
    ).fetchone()[0]
    conn.close()
    assert download_count == 1
    assert user_count == 0
