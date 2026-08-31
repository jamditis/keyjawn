import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from pathlib import Path

import pytest
from fastapi.testclient import TestClient


def test_store_startup_contract_includes_required_secrets():
    store_directory = Path(__file__).parents[1]
    start_script = (store_directory / "start.sh").read_text()
    env_example = (store_directory / ".env.example").read_text()

    assert "set -euo pipefail" in start_script
    assert "export STRIPE_API_KEY STRIPE_WEBHOOK_SECRET ADMIN_TOKEN UNSUBSCRIBE_SECRET" in start_script
    assert 'export UNSUBSCRIBE_SECRET="$(' not in start_script
    assert "UNSUBSCRIBE_SECRET=" in env_example


def test_admin_token_validation_fails_closed(monkeypatch):
    from auth import load_admin_token

    with pytest.raises(RuntimeError, match="ADMIN_TOKEN"):
        load_admin_token({})


def test_admin_token_rejects_non_ascii_input():
    from auth import admin_token_matches

    assert admin_token_matches("é") is False

def test_admin_requires_auth():
    from app import app
    client = TestClient(app)
    resp = client.get("/admin", follow_redirects=False)
    assert resp.status_code == 302
    assert resp.headers["location"] == "/admin/login"

def test_admin_login_with_token():
    os.environ["ADMIN_TOKEN"] = "testtoken"
    from db import init_db
    init_db()
    from app import app
    client = TestClient(app, base_url="https://testserver")
    resp = client.post(
        "/admin/login",
        data={"token": "testtoken"},
        follow_redirects=False,
    )
    assert resp.status_code == 303
    assert resp.headers["location"] == "/admin"
    resp2 = client.get("/admin")
    assert resp2.status_code == 200
    assert "Dashboard" in resp2.text
    assert 'href="/static/favicon.svg"' in resp2.text
    assert "https://keyjawn.amditis.tech/favicon.svg" not in resp2.text


def test_admin_login_page_is_not_indexed():
    from app import app

    client = TestClient(app)
    response = client.get("/admin/login")

    assert response.status_code == 200
    assert '<meta name="robots" content="noindex, nofollow">' in response.text
    assert 'href="/static/favicon.svg"' in response.text
    assert "https://keyjawn.amditis.tech/favicon.svg" not in response.text

    favicon = client.get("/static/favicon.svg")
    assert favicon.status_code == 200
    assert favicon.headers["content-type"].startswith("image/svg+xml")
