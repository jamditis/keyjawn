import os
import sys
from urllib.parse import parse_qs, urlsplit

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

from fastapi.testclient import TestClient


def unsubscribed_value(email):
    from db import get_db

    conn = get_db()
    user = conn.execute(
        "SELECT unsubscribed FROM users WHERE email = ?", (email,)
    ).fetchone()
    conn.close()
    return user["unsubscribed"]


def test_unsubscribe_url_preserves_plus_address():
    from app import app
    from routes.unsubscribe import make_unsubscribe_url

    email = "reader+news@example.com"
    url = urlsplit(make_unsubscribe_url(email))
    query = parse_qs(url.query)

    assert query["email"] == [email]

    client = TestClient(app)
    response = client.get(f"{url.path}?{url.query}")

    assert response.status_code == 200


def test_unsubscribe_get_requires_confirmation_before_database_update():
    from app import app
    from db import get_db
    from routes.unsubscribe import make_unsubscribe_url

    stored_email = "reader@example.com"
    conn = get_db()
    conn.execute("INSERT INTO users (email) VALUES (?)", (stored_email,))
    conn.commit()
    conn.close()

    url = urlsplit(make_unsubscribe_url("Reader@Example.com"))
    client = TestClient(app)
    response = client.get(f"{url.path}?{url.query}")

    assert response.status_code == 200
    assert '<form method="post"' in response.text
    assert unsubscribed_value(stored_email) == 0


def test_unsubscribe_post_matches_mixed_case_stored_email():
    from app import app
    from db import get_db
    from routes.unsubscribe import make_unsubscribe_url

    stored_email = "Reader@Example.com"
    conn = get_db()
    conn.execute("INSERT INTO users (email) VALUES (?)", (stored_email,))
    conn.commit()
    conn.close()

    url = urlsplit(make_unsubscribe_url("reader@example.com"))
    client = TestClient(app)
    response = client.post(f"{url.path}?{url.query}")

    assert response.status_code == 200
    assert unsubscribed_value(stored_email) == 1


def test_unsubscribe_rejects_non_ascii_token():
    from app import app

    client = TestClient(app)
    for request in (client.get, client.post):
        response = request(
            "/unsubscribe",
            params={"email": "reader@example.com", "token": "é"},
        )

        assert response.status_code == 400


def test_unsubscribe_page_has_public_page_metadata():
    from app import app
    from routes.unsubscribe import make_unsubscribe_token

    email = "reader@example.com"
    client = TestClient(app)
    response = client.get(
        "/unsubscribe",
        params={"email": email, "token": make_unsubscribe_token(email)},
    )

    assert response.status_code == 200
    assert '<html lang="en">' in response.text
    assert '<meta name="viewport"' in response.text
    assert '<link rel="icon" type="image/svg+xml" href="/static/favicon.svg">' in response.text
    assert "https://keyjawn.amditis.tech/favicon.svg" not in response.text
    assert '<meta property="og:title"' in response.text
