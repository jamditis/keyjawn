import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from fastapi.testclient import TestClient
from unittest.mock import patch


def test_register_release_requires_auth():
    from app import app
    client = TestClient(app)
    resp = client.post("/api/releases", json={"version": "0.2.0", "r2_key": "test"})
    assert resp.status_code == 401


def test_register_release_with_valid_token():
    os.environ["ADMIN_TOKEN"] = "testtoken"
    from db import init_db
    init_db()
    from app import app
    client = TestClient(app)
    resp = client.post(
        "/api/releases",
        json={"version": "0.2.0", "r2_key": "keyjawn/releases/keyjawn-full-v0.2.0.apk", "file_size": 5000000, "sha256": "abc123"},
        headers={"Authorization": "Bearer testtoken"}
    )
    assert resp.status_code == 200
    assert resp.json()["version"] == "0.2.0"


def test_notify_counts_actual_email_results():
    os.environ["ADMIN_TOKEN"] = "testtoken"
    from app import app
    from db import get_db

    conn = get_db()
    conn.execute(
        "INSERT INTO releases (version, r2_key) VALUES (?, ?)",
        ("0.3.0", "keyjawn/releases/v0.3.0/app.apk"),
    )
    conn.execute("INSERT INTO users (email) VALUES (?)", ("one@example.com",))
    conn.execute("INSERT INTO users (email) VALUES (?)", ("two@example.com",))
    conn.commit()
    conn.close()

    client = TestClient(app)
    with patch("routes.releases.send_update_email", side_effect=[True, False]):
        with patch("routes.releases.send_telegram_alert") as telegram:
            response = client.post(
                "/api/releases/0.3.0/notify",
                headers={"Authorization": "Bearer testtoken"},
            )

    assert response.status_code == 200
    assert response.json() == {"version": "0.3.0", "sent": 1, "failed": 1}
    telegram.assert_called_once_with(
        "KeyJawn v0.3.0 update emails: 1 sent, 1 failed"
    )
