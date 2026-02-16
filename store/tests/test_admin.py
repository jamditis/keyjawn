import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from fastapi.testclient import TestClient

def test_admin_requires_auth():
    from app import app
    client = TestClient(app)
    resp = client.get("/admin", follow_redirects=False)
    assert resp.status_code == 401

def test_admin_login_with_token():
    os.environ["ADMIN_TOKEN"] = "testtoken"
    from db import init_db
    init_db()
    from app import app
    client = TestClient(app)
    # First request sets the cookie
    resp = client.get("/admin?token=testtoken", follow_redirects=False)
    assert resp.status_code == 302
    assert resp.headers["location"] == "/admin"
    # Follow redirect — client auto-persists cookies from the redirect response
    client.cookies.update(resp.cookies)
    resp2 = client.get("/admin")
    assert resp2.status_code == 200
    assert "Dashboard" in resp2.text
