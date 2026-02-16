import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from fastapi.testclient import TestClient


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
