# KeyJawn store implementation plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a standalone FastAPI service for Stripe payments, paid user tracking, gated APK downloads, priority support, and an admin dashboard — replacing Google Play Billing.

**Architecture:** A Python FastAPI service (`keyjawn-store`) on houseofjawn:5060 with SQLite for data, Stripe webhooks for payment processing, signed R2 URLs for APK delivery, Jinja2+htmx for the admin UI, and Resend for transactional email. The Astro website gets new download/thanks/support pages. The Android app drops the Play Billing dependency and replaces `BillingManager` with a simple flavor-based feature gate.

**Tech Stack:** Python 3, FastAPI, SQLite, Stripe API, Cloudflare R2, Resend, Jinja2, htmx, Astro (website), Kotlin (app changes)

---

## Task 1: Scaffold the store service

**Files:**
- Create: `store/requirements.txt`
- Create: `store/app.py`
- Create: `store/db.py`
- Create: `store/.env.example`

**Step 1: Create the directory and requirements file**

```bash
mkdir -p store/templates/admin/partials store/static
```

```
# store/requirements.txt
fastapi==0.115.0
uvicorn[standard]==0.30.0
stripe==10.0.0
jinja2==3.1.4
python-multipart==0.0.9
resend==2.0.0
httpx==0.27.0
```

**Step 2: Create the database module**

```python
# store/db.py
import sqlite3
import os

DB_PATH = os.path.join(os.path.dirname(__file__), "keyjawn-store.db")

def get_db():
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL")
    conn.execute("PRAGMA foreign_keys=ON")
    return conn

def init_db():
    conn = get_db()
    conn.executescript("""
        CREATE TABLE IF NOT EXISTS users (
            id INTEGER PRIMARY KEY,
            email TEXT UNIQUE NOT NULL,
            stripe_customer_id TEXT,
            stripe_payment_intent TEXT,
            amount_cents INTEGER NOT NULL DEFAULT 400,
            purchased_at TEXT NOT NULL DEFAULT (datetime('now')),
            download_count INTEGER NOT NULL DEFAULT 0,
            last_download_at TEXT,
            notes TEXT
        );

        CREATE TABLE IF NOT EXISTS downloads (
            id INTEGER PRIMARY KEY,
            user_id INTEGER NOT NULL REFERENCES users(id),
            version TEXT NOT NULL,
            ip_address TEXT,
            user_agent TEXT,
            downloaded_at TEXT NOT NULL DEFAULT (datetime('now'))
        );

        CREATE TABLE IF NOT EXISTS tickets (
            id INTEGER PRIMARY KEY,
            user_id INTEGER NOT NULL REFERENCES users(id),
            subject TEXT NOT NULL,
            body TEXT NOT NULL,
            device_model TEXT,
            android_version TEXT,
            app_version TEXT,
            status TEXT NOT NULL DEFAULT 'open',
            created_at TEXT NOT NULL DEFAULT (datetime('now')),
            updated_at TEXT
        );

        CREATE TABLE IF NOT EXISTS releases (
            id INTEGER PRIMARY KEY,
            version TEXT UNIQUE NOT NULL,
            r2_key TEXT NOT NULL,
            file_size INTEGER,
            sha256 TEXT,
            released_at TEXT NOT NULL DEFAULT (datetime('now'))
        );
    """)
    conn.close()
```

**Step 3: Create the main app**

```python
# store/app.py
import os
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from db import init_db

app = FastAPI(title="keyjawn-store", docs_url=None, redoc_url=None)
app.mount("/static", StaticFiles(directory="static"), name="static")

init_db()

@app.get("/api/health")
async def health():
    return {"status": "ok", "service": "keyjawn-store"}
```

**Step 4: Create .env.example**

```
STRIPE_API_KEY=sk_live_...
STRIPE_WEBHOOK_SECRET=whsec_...
ADMIN_TOKEN=changeme
RESEND_API_KEY=re_...
R2_ACCOUNT_ID=...
R2_ACCESS_KEY_ID=...
R2_SECRET_ACCESS_KEY=...
R2_BUCKET=pi-transfer
TELEGRAM_BOT_TOKEN=...
TELEGRAM_CHAT_ID=...
```

**Step 5: Verify the app starts**

Run: `cd store && pip install -r requirements.txt && python -c "from db import init_db; init_db(); print('DB OK')"`
Expected: `DB OK` and `keyjawn-store.db` file created

**Step 6: Commit**

```bash
git add store/
git commit -m "feat: scaffold keyjawn-store service with FastAPI and SQLite"
```

---

## Task 2: Stripe webhook endpoint

**Files:**
- Create: `store/routes/webhook.py`
- Create: `store/email_sender.py`
- Modify: `store/app.py` (register router)

**Step 1: Write the failing test**

```python
# store/tests/test_webhook.py
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
```

**Step 2: Run test — expected FAIL**

Run: `cd store && python -m pytest tests/test_webhook.py -v`
Expected: ImportError (routes/webhook.py doesn't exist)

**Step 3: Implement the webhook route**

```python
# store/routes/__init__.py
```

```python
# store/routes/webhook.py
import os
import stripe
from fastapi import APIRouter, Request, HTTPException
from db import get_db
from email_sender import send_download_email

router = APIRouter()
stripe.api_key = os.environ.get("STRIPE_API_KEY", "")
WEBHOOK_SECRET = os.environ.get("STRIPE_WEBHOOK_SECRET", "")

@router.post("/webhook/stripe")
async def stripe_webhook(request: Request):
    payload = await request.body()
    sig = request.headers.get("stripe-signature")
    if not sig:
        raise HTTPException(400, "Missing stripe-signature header")

    try:
        event = stripe.Webhook.construct_event(payload, sig, WEBHOOK_SECRET)
    except (stripe.error.SignatureVerificationError, ValueError):
        raise HTTPException(400, "Invalid signature")

    if event["type"] == "checkout.session.completed":
        session = event["data"]["object"]
        email = session["customer_details"]["email"]
        customer_id = session.get("customer")
        payment_intent = session.get("payment_intent")
        amount = session.get("amount_total", 400)

        conn = get_db()
        conn.execute("""
            INSERT INTO users (email, stripe_customer_id, stripe_payment_intent, amount_cents)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(email) DO NOTHING
        """, [email, customer_id, payment_intent, amount])
        conn.commit()
        conn.close()

        send_download_email(email)

    return {"status": "ok"}
```

```python
# store/email_sender.py
import os
import resend
import logging

log = logging.getLogger("keyjawn-store")
resend.api_key = os.environ.get("RESEND_API_KEY", "")
FROM_EMAIL = "KeyJawn <support@keyjawn.amditis.tech>"

def send_download_email(to_email: str):
    try:
        resend.Emails.send({
            "from": FROM_EMAIL,
            "to": [to_email],
            "bcc": ["jamditis@gmail.com"],
            "subject": "KeyJawn full version -- your download link",
            "html": f"""
                <p>Thanks for buying KeyJawn.</p>
                <p>Download the latest full version here:<br>
                <a href="https://keyjawn.amditis.tech/download">keyjawn.amditis.tech/download</a></p>
                <p>Enter <strong>{to_email}</strong> on the download page to get the APK.</p>
                <p>For updates, just visit the same page -- it always has the latest version.</p>
                <p>-- KeyJawn</p>
            """
        })
    except Exception as e:
        log.error(f"Failed to send download email to {to_email}: {e}")

def send_ticket_confirmation(to_email: str, subject: str, ticket_id: int):
    try:
        resend.Emails.send({
            "from": FROM_EMAIL,
            "to": [to_email],
            "bcc": ["jamditis@gmail.com"],
            "subject": f"Re: {subject} [#{ticket_id}]",
            "html": f"""
                <p>Got your support request (ticket #{ticket_id}). Looking into it.</p>
                <p>-- KeyJawn support</p>
            """
        })
    except Exception as e:
        log.error(f"Failed to send ticket confirmation to {to_email}: {e}")
```

**Step 4: Register the router in app.py**

Add to `store/app.py`:

```python
from routes.webhook import router as webhook_router
app.include_router(webhook_router)
```

**Step 5: Run test — expected PASS**

Run: `cd store && python -m pytest tests/test_webhook.py -v`

**Step 6: Commit**

```bash
git add store/
git commit -m "feat: add Stripe webhook endpoint with user creation and email"
```

---

## Task 3: Download endpoint

**Files:**
- Create: `store/routes/download.py`
- Create: `store/r2.py`
- Modify: `store/app.py` (register router)

**Step 1: Write the failing test**

```python
# store/tests/test_download.py
import pytest
from fastapi.testclient import TestClient
from unittest.mock import patch

def setup_test_user():
    from db import get_db, init_db
    init_db()
    conn = get_db()
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
```

**Step 2: Run test — expected FAIL**

**Step 3: Implement R2 signed URL generation**

```python
# store/r2.py
import os
import boto3
from botocore.config import Config

R2_ACCOUNT_ID = os.environ.get("R2_ACCOUNT_ID", "")
R2_ACCESS_KEY = os.environ.get("R2_ACCESS_KEY_ID", "")
R2_SECRET_KEY = os.environ.get("R2_SECRET_ACCESS_KEY", "")
R2_BUCKET = os.environ.get("R2_BUCKET", "pi-transfer")

def get_r2_client():
    return boto3.client(
        "s3",
        endpoint_url=f"https://{R2_ACCOUNT_ID}.r2.cloudflarestorage.com",
        aws_access_key_id=R2_ACCESS_KEY,
        aws_secret_access_key=R2_SECRET_KEY,
        config=Config(signature_version="s3v4"),
        region_name="auto",
    )

def generate_signed_url(r2_key: str, expires_in: int = 3600) -> str:
    client = get_r2_client()
    return client.generate_presigned_url(
        "get_object",
        Params={"Bucket": R2_BUCKET, "Key": r2_key},
        ExpiresIn=expires_in,
    )
```

**Step 4: Implement the download route**

```python
# store/routes/download.py
from datetime import datetime, timedelta
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, EmailStr
from db import get_db
from r2 import generate_signed_url

router = APIRouter()

class DownloadRequest(BaseModel):
    email: EmailStr

@router.post("/api/download")
async def download(req: DownloadRequest):
    conn = get_db()

    user = conn.execute("SELECT * FROM users WHERE email = ?", (req.email,)).fetchone()
    if not user:
        conn.close()
        raise HTTPException(404, "Email not found. Check that you used the same email as your purchase.")

    # Rate limit: 5 downloads per day
    today = datetime.utcnow().strftime("%Y-%m-%d")
    count = conn.execute("""
        SELECT COUNT(*) FROM downloads
        WHERE user_id = ? AND downloaded_at >= ?
    """, (user["id"], today)).fetchone()[0]
    if count >= 5:
        conn.close()
        raise HTTPException(429, "Download limit reached. Try again tomorrow.")

    # Get latest release
    release = conn.execute(
        "SELECT * FROM releases ORDER BY released_at DESC LIMIT 1"
    ).fetchone()
    if not release:
        conn.close()
        raise HTTPException(503, "No releases available yet.")

    # Generate signed URL
    url = generate_signed_url(release["r2_key"])

    # Log download
    conn.execute("""
        INSERT INTO downloads (user_id, version) VALUES (?, ?)
    """, (user["id"], release["version"]))
    conn.execute("""
        UPDATE users SET download_count = download_count + 1, last_download_at = datetime('now')
        WHERE id = ?
    """, (user["id"],))
    conn.commit()
    conn.close()

    return {
        "url": url,
        "version": release["version"],
        "file_size": release["file_size"],
    }
```

**Step 5: Register router in app.py**

```python
from routes.download import router as download_router
app.include_router(download_router)
```

**Step 6: Run test — expected PASS**

**Step 7: Commit**

```bash
git add store/
git commit -m "feat: add gated download endpoint with R2 signed URLs"
```

---

## Task 4: Support ticket endpoint

**Files:**
- Create: `store/routes/support.py`
- Create: `store/telegram.py`
- Modify: `store/app.py` (register router)

**Step 1: Write the failing test**

```python
# store/tests/test_support.py
from fastapi.testclient import TestClient
from unittest.mock import patch

def setup_test_user():
    from db import get_db, init_db
    init_db()
    conn = get_db()
    conn.execute("DELETE FROM users")
    conn.execute("DELETE FROM tickets")
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
```

**Step 2: Run test — expected FAIL**

**Step 3: Implement telegram notification helper**

```python
# store/telegram.py
import os
import httpx
import html
import logging

log = logging.getLogger("keyjawn-store")
BOT_TOKEN = os.environ.get("TELEGRAM_BOT_TOKEN", "")
CHAT_ID = os.environ.get("TELEGRAM_CHAT_ID", "")

def send_telegram_alert(message: str):
    if not BOT_TOKEN or not CHAT_ID:
        log.warning("Telegram not configured, skipping alert")
        return
    try:
        escaped = html.escape(message)
        httpx.post(
            f"https://api.telegram.org/bot{BOT_TOKEN}/sendMessage",
            json={"chat_id": CHAT_ID, "text": escaped, "parse_mode": "HTML"},
            timeout=10,
        )
    except Exception as e:
        log.error(f"Telegram alert failed: {e}")
```

**Step 4: Implement support route**

```python
# store/routes/support.py
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, EmailStr
from typing import Optional
from db import get_db
from telegram import send_telegram_alert
from email_sender import send_ticket_confirmation

router = APIRouter()

class SupportRequest(BaseModel):
    email: EmailStr
    subject: str
    body: str
    device_model: Optional[str] = None
    android_version: Optional[str] = None
    app_version: Optional[str] = None

@router.post("/api/support")
async def create_ticket(req: SupportRequest):
    conn = get_db()
    user = conn.execute("SELECT * FROM users WHERE email = ?", (req.email,)).fetchone()
    if not user:
        conn.close()
        raise HTTPException(403, "Email not found. Priority support is for paid users. For general help, use GitHub Issues.")

    cursor = conn.execute("""
        INSERT INTO tickets (user_id, subject, body, device_model, android_version, app_version)
        VALUES (?, ?, ?, ?, ?, ?)
    """, (user["id"], req.subject, req.body, req.device_model, req.android_version, req.app_version))
    ticket_id = cursor.lastrowid
    conn.commit()
    conn.close()

    send_telegram_alert(f"New keyjawn support ticket #{ticket_id} from {req.email}: {req.subject}")
    send_ticket_confirmation(req.email, req.subject, ticket_id)

    return {"ticket_id": ticket_id, "status": "open"}
```

**Step 5: Register router, run tests, commit**

```bash
git add store/
git commit -m "feat: add priority support ticket endpoint with Telegram alerts"
```

---

## Task 5: Release registration endpoint

**Files:**
- Create: `store/routes/releases.py`
- Modify: `store/app.py` (register router)

**Step 1: Write the failing test**

```python
# store/tests/test_releases.py
from fastapi.testclient import TestClient

def test_register_release_requires_auth():
    from app import app
    client = TestClient(app)
    resp = client.post("/api/releases", json={"version": "0.2.0", "r2_key": "test"})
    assert resp.status_code == 401

def test_register_release_with_valid_token():
    import os
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
```

**Step 2: Implement**

```python
# store/routes/releases.py
import os
from fastapi import APIRouter, HTTPException, Header
from pydantic import BaseModel
from typing import Optional
from db import get_db

router = APIRouter()
ADMIN_TOKEN = os.environ.get("ADMIN_TOKEN", "")

def require_admin(authorization: Optional[str] = Header(None)):
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Missing or invalid token")
    token = authorization.split(" ", 1)[1]
    if token != ADMIN_TOKEN:
        raise HTTPException(401, "Invalid token")

class ReleaseRequest(BaseModel):
    version: str
    r2_key: str
    file_size: Optional[int] = None
    sha256: Optional[str] = None

@router.post("/api/releases")
async def register_release(req: ReleaseRequest, authorization: Optional[str] = Header(None)):
    require_admin(authorization)
    conn = get_db()
    conn.execute("""
        INSERT INTO releases (version, r2_key, file_size, sha256)
        VALUES (?, ?, ?, ?)
        ON CONFLICT(version) DO UPDATE SET r2_key=?, file_size=?, sha256=?
    """, (req.version, req.r2_key, req.file_size, req.sha256, req.r2_key, req.file_size, req.sha256))
    conn.commit()
    conn.close()
    return {"version": req.version, "status": "registered"}
```

**Step 3: Register router, run tests, commit**

```bash
git add store/
git commit -m "feat: add release registration endpoint for CI uploads"
```

---

## Task 6: Admin dashboard

**Files:**
- Create: `store/routes/admin.py`
- Create: `store/templates/admin/base.html`
- Create: `store/templates/admin/dashboard.html`
- Create: `store/templates/admin/users.html`
- Create: `store/templates/admin/tickets.html`
- Create: `store/templates/admin/releases.html`
- Create: `store/templates/admin/partials/stats.html`
- Create: `store/templates/admin/partials/recent_purchases.html`
- Create: `store/templates/admin/partials/ticket_rows.html`
- Create: `store/static/style.css`
- Modify: `store/app.py` (register router, add Jinja2)

Pattern: Follow notify-service's admin UI pattern. Jinja2 templates, htmx for partial updates, dark theme, token auth via cookie.

**Step 1: Set up Jinja2 in app.py**

```python
from fastapi.templating import Jinja2Templates
templates = Jinja2Templates(directory="templates")
```

**Step 2: Create admin router with cookie-based auth**

```python
# store/routes/admin.py
import os
from fastapi import APIRouter, Request, HTTPException, Query
from fastapi.responses import RedirectResponse, HTMLResponse
from fastapi.templating import Jinja2Templates
from db import get_db

router = APIRouter(prefix="/admin")
templates = Jinja2Templates(directory="templates")
ADMIN_TOKEN = os.environ.get("ADMIN_TOKEN", "")

def check_auth(request: Request):
    token = request.cookies.get("admin_token")
    if token != ADMIN_TOKEN:
        raise HTTPException(401, "Unauthorized")

@router.get("")
async def dashboard(request: Request, token: str = Query(None)):
    if token and token == ADMIN_TOKEN:
        response = RedirectResponse("/admin", status_code=302)
        response.set_cookie("admin_token", token, httponly=True, samesite="strict")
        return response
    check_auth(request)
    conn = get_db()
    stats = {
        "total_users": conn.execute("SELECT COUNT(*) FROM users").fetchone()[0],
        "total_revenue": conn.execute("SELECT COALESCE(SUM(amount_cents), 0) FROM users").fetchone()[0],
        "total_downloads": conn.execute("SELECT COALESCE(SUM(download_count), 0) FROM users").fetchone()[0],
        "open_tickets": conn.execute("SELECT COUNT(*) FROM tickets WHERE status IN ('open','in_progress')").fetchone()[0],
    }
    recent = conn.execute("SELECT * FROM users ORDER BY purchased_at DESC LIMIT 10").fetchall()
    conn.close()
    return templates.TemplateResponse("admin/dashboard.html", {
        "request": request, "stats": stats, "recent": recent, "active_page": "dashboard"
    })

@router.get("/users")
async def users_page(request: Request, q: str = ""):
    check_auth(request)
    conn = get_db()
    if q:
        users = conn.execute("SELECT * FROM users WHERE email LIKE ? ORDER BY purchased_at DESC", (f"%{q}%",)).fetchall()
    else:
        users = conn.execute("SELECT * FROM users ORDER BY purchased_at DESC").fetchall()
    conn.close()
    return templates.TemplateResponse("admin/users.html", {
        "request": request, "users": users, "query": q, "active_page": "users"
    })

@router.get("/tickets")
async def tickets_page(request: Request):
    check_auth(request)
    conn = get_db()
    tickets = conn.execute("""
        SELECT t.*, u.email FROM tickets t
        JOIN users u ON t.user_id = u.id
        ORDER BY CASE t.status WHEN 'open' THEN 0 WHEN 'in_progress' THEN 1 ELSE 2 END, t.created_at DESC
    """).fetchall()
    conn.close()
    return templates.TemplateResponse("admin/tickets.html", {
        "request": request, "tickets": tickets, "active_page": "tickets"
    })

@router.post("/tickets/{ticket_id}/status")
async def update_ticket_status(request: Request, ticket_id: int):
    check_auth(request)
    form = await request.form()
    new_status = form.get("status")
    if new_status not in ("open", "in_progress", "resolved", "closed"):
        raise HTTPException(400, "Invalid status")
    conn = get_db()
    conn.execute("UPDATE tickets SET status = ?, updated_at = datetime('now') WHERE id = ?", (new_status, ticket_id))
    conn.commit()
    conn.close()
    return RedirectResponse("/admin/tickets", status_code=303)

@router.get("/releases")
async def releases_page(request: Request):
    check_auth(request)
    conn = get_db()
    releases = conn.execute("SELECT * FROM releases ORDER BY released_at DESC").fetchall()
    conn.close()
    return templates.TemplateResponse("admin/releases.html", {
        "request": request, "releases": releases, "active_page": "releases"
    })
```

**Step 3: Create templates**

Follow the notify-service pattern: `base.html` with nav, child templates extend it, dark theme CSS.

`store/templates/admin/base.html`:
```html
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>{% block title %}Admin{% endblock %} - keyjawn-store</title>
    <link rel="icon" type="image/svg+xml" href="data:image/svg+xml,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 100 100'><text y='.9em' font-size='90'>&#x2328;</text></svg>">
    <link rel="stylesheet" href="/static/style.css">
    <script src="https://unpkg.com/htmx.org@2.0.4"></script>
</head>
<body>
    <nav>
        <div class="container">
            <a href="/admin" class="brand">keyjawn-store</a>
            <a href="/admin" class="{{ 'active' if active_page == 'dashboard' }}">Dashboard</a>
            <a href="/admin/users" class="{{ 'active' if active_page == 'users' }}">Users</a>
            <a href="/admin/tickets" class="{{ 'active' if active_page == 'tickets' }}">Tickets</a>
            <a href="/admin/releases" class="{{ 'active' if active_page == 'releases' }}">Releases</a>
        </div>
    </nav>
    <main class="container">
        {% block content %}{% endblock %}
    </main>
</body>
</html>
```

**Step 4: Create `store/static/style.css`**

Reuse notify-service's dark theme CSS (the full CSS from `/home/jamditis/projects/stash/raspberry-pi/services/notify-service/static/admin/style.css`). Copy it directly — same color scheme, same card/table/stats patterns.

**Step 5: Create dashboard, users, tickets, releases templates**

Each template extends `base.html` and renders the data passed from the route. Tables with htmx for partial refresh where useful.

**Step 6: Register admin router, test manually, commit**

```bash
git add store/
git commit -m "feat: add htmx admin dashboard with users, tickets, and releases pages"
```

---

## Task 7: Systemd service and Cloudflare tunnel

**Files:**
- Create: `store/keyjawn-store.service`
- Modify: `/etc/cloudflared/config.yml` (add tunnel ingress)

**Step 1: Create the systemd service file**

```ini
# store/keyjawn-store.service
[Unit]
Description=KeyJawn store service
After=network.target

[Service]
Type=simple
User=jamditis
WorkingDirectory=/home/jamditis/projects/keyjawn/store
EnvironmentFile=/home/jamditis/projects/keyjawn/store/.env
ExecStart=/home/jamditis/projects/keyjawn/store/venv/bin/uvicorn app:app --host 127.0.0.1 --port 5060
Restart=always
RestartSec=5

[Install]
WantedBy=multi-user.target
```

**Step 2: Create virtualenv and install dependencies**

```bash
cd /home/jamditis/projects/keyjawn/store
python3 -m venv venv
venv/bin/pip install -r requirements.txt
```

**Step 3: Create .env from secrets**

```bash
cat > /home/jamditis/projects/keyjawn/store/.env << 'EOF'
STRIPE_API_KEY=$(pass show claude/services/stripe-api-key)
STRIPE_WEBHOOK_SECRET=$(pass show claude/services/stripe-webhook-secret)
ADMIN_TOKEN=$(pass show claude/tokens/keyjawn-store-admin)
RESEND_API_KEY=$(pass show claude/services/resend-api-key)
TELEGRAM_BOT_TOKEN=$(pass show claude/tokens/telegram-bot)
TELEGRAM_CHAT_ID=<joe's chat id>
R2_ACCOUNT_ID=<from wrangler config>
R2_ACCESS_KEY_ID=<from R2 dashboard>
R2_SECRET_ACCESS_KEY=<from R2 dashboard>
R2_BUCKET=pi-transfer
EOF
```

Note: Stripe API key and webhook secret need to be created in Stripe first. Resend API key needs Resend account setup. Store these in `pass` before creating `.env`.

**Step 4: Install and start the service**

```bash
sudo cp store/keyjawn-store.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable keyjawn-store
sudo systemctl start keyjawn-store
sudo systemctl status keyjawn-store
```

**Step 5: Add Cloudflare tunnel ingress**

Add before the `service: http_status:404` catch-all in `/etc/cloudflared/config.yml`:

```yaml
  - hostname: store.keyjawn.amditis.tech
    service: http://127.0.0.1:5060
```

Then add the DNS record in Cloudflare:
```bash
cloudflared tunnel route dns houseofjawn store.keyjawn.amditis.tech
```

Restart tunnel:
```bash
sudo systemctl restart cloudflared
```

**Step 6: Verify health endpoint**

```bash
curl https://store.keyjawn.amditis.tech/api/health
```

Expected: `{"status":"ok","service":"keyjawn-store"}`

**Step 7: Commit**

```bash
git add store/keyjawn-store.service
git commit -m "infra: add systemd service and Cloudflare tunnel config"
```

---

## Task 8: Email setup (Cloudflare Email Routing + Resend)

This task requires manual Cloudflare and Resend dashboard work. No code changes.

**Step 1: Set up Cloudflare Email Routing**

1. Go to Cloudflare dashboard > `keyjawn.amditis.tech` (or `amditis.tech` if that's the zone)
2. Email > Email Routing > Enable
3. Add routing rule: `support@keyjawn.amditis.tech` -> `jamditis@gmail.com`
4. Verify the destination email if prompted

**Step 2: Set up Resend**

1. Create Resend account at resend.com
2. Add domain `keyjawn.amditis.tech` for sending
3. Add the DNS records Resend provides (SPF, DKIM, etc.)
4. Get API key
5. Store: `pass insert claude/services/resend-api-key`

**Step 3: Test email**

```python
import resend
resend.api_key = "re_..."
resend.Emails.send({
    "from": "KeyJawn <support@keyjawn.amditis.tech>",
    "to": ["jamditis@gmail.com"],
    "subject": "Test email",
    "html": "<p>Test from keyjawn-store</p>"
})
```

---

## Task 9: Stripe setup

Manual Stripe dashboard work.

**Step 1: Create Stripe account** (if not already done)

1. Go to stripe.com, create account
2. Complete verification

**Step 2: Create the product and payment link**

1. Products > Create product: "KeyJawn full version", $4.00 USD, one-time
2. Create a Payment Link for this product
3. Set success URL: `https://keyjawn.amditis.tech/thanks`
4. Enable receipts in Settings > Email receipts

**Step 3: Configure webhook**

1. Developers > Webhooks > Add endpoint
2. URL: `https://store.keyjawn.amditis.tech/webhook/stripe`
3. Events: `checkout.session.completed`
4. Copy the webhook signing secret

**Step 4: Store credentials**

```bash
pass insert claude/services/stripe-api-key      # sk_live_...
pass insert claude/services/stripe-webhook-secret # whsec_...
```

**Step 5: Update .env with real values**

---

## Task 10: Website changes

**Files:**
- Modify: `website/src/pages/pricing.astro`
- Create: `website/src/pages/download.astro`
- Create: `website/src/pages/thanks.astro`
- Modify: `website/src/pages/support.astro`
- Modify: `website/src/components/Nav.astro`

**Step 1: Update pricing page**

In `website/src/pages/pricing.astro`:
- Change the full version CTA from `ctaText="Get on Google Play"` / `ctaLink="https://play.google.com/..."` to the Stripe Payment Link URL
- Keep the free tier pointing to Google Play (lite version)
- Update the sideload note

**Step 2: Create download page**

`website/src/pages/download.astro`:
- Simple form: email input + "Download" button
- JavaScript POSTs to `https://store.keyjawn.amditis.tech/api/download`
- On success: shows download link (the signed R2 URL) and version info
- On error: shows the error message
- Terminal-themed styling consistent with the rest of the site

**Step 3: Create thanks page**

`website/src/pages/thanks.astro`:
- "Thanks for buying KeyJawn" message
- Embeds the same download form
- "Check your email for download instructions"

**Step 4: Update support page**

In `website/src/pages/support.astro`:
- Add a "Priority support" section after the existing content
- Form fields: email, subject, description, device model, Android version, app version
- JavaScript POSTs to `https://store.keyjawn.amditis.tech/api/support`
- Show confirmation or error inline

**Step 5: Update nav**

In `website/src/components/Nav.astro`:
- Add "Download" link between existing nav items

**Step 6: Build and test locally**

```bash
cd website && npm run dev
```

**Step 7: Commit**

```bash
git add website/
git commit -m "feat: update website with download page, Stripe checkout, and priority support form"
```

---

## Task 11: CI pipeline changes

**Files:**
- Modify: `.github/workflows/build.yml`

**Step 1: Update the release job**

Current behavior: uploads both full and lite APKs + AAB to GitHub Releases.

New behavior:
- Upload **lite APK** to GitHub Releases (public)
- Upload **full APK** to R2 via wrangler
- Call store API to register the release
- Do NOT upload full APK to GitHub Releases
- Keep the AAB for Play Store submission (lite only on Play Store)

Add after the build step in the `release` job:

```yaml
      - name: Install wrangler
        run: npm install -g wrangler

      - name: Upload full APK to R2
        env:
          CLOUDFLARE_ACCOUNT_ID: ${{ secrets.CLOUDFLARE_ACCOUNT_ID }}
          CLOUDFLARE_API_TOKEN: ${{ secrets.CLOUDFLARE_API_TOKEN }}
        run: |
          VERSION=${GITHUB_REF#refs/tags/v}
          FULL_APK=$(ls app/build/outputs/apk/full/release/*.apk)
          FILE_SIZE=$(stat -c%s "$FULL_APK")
          SHA256=$(sha256sum "$FULL_APK" | cut -d' ' -f1)
          R2_KEY="keyjawn/releases/keyjawn-full-v${VERSION}.apk"

          wrangler r2 object put "pi-transfer/${R2_KEY}" --file "$FULL_APK"

          # Register with store service
          curl -X POST https://store.keyjawn.amditis.tech/api/releases \
            -H "Authorization: Bearer ${{ secrets.KEYJAWN_STORE_TOKEN }}" \
            -H "Content-Type: application/json" \
            -d "{\"version\":\"${VERSION}\",\"r2_key\":\"${R2_KEY}\",\"file_size\":${FILE_SIZE},\"sha256\":\"${SHA256}\"}"

      - name: Create GitHub release (lite only)
        uses: softprops/action-gh-release@v2
        with:
          files: |
            app/build/outputs/apk/lite/release/*.apk
          generate_release_notes: true
```

**Step 2: Add GitHub secrets**

In the repo settings > Secrets and variables > Actions:
- `CLOUDFLARE_ACCOUNT_ID`
- `CLOUDFLARE_API_TOKEN` (with R2 write permissions)
- `KEYJAWN_STORE_TOKEN` (same as `ADMIN_TOKEN`)

**Step 3: Commit**

```bash
git add .github/
git commit -m "ci: upload full APK to R2 and register release, lite-only GitHub releases"
```

---

## Task 12: App changes (remove Google Play Billing)

**Files:**
- Modify: `app/src/full/java/com/keyjawn/BillingManager.kt`
- (No changes to lite `BillingManager.kt`)
- Modify: `app/src/main/java/com/keyjawn/SettingsActivity.kt`
- Modify: `app/build.gradle.kts`

**Step 1: Replace full BillingManager with a simple stub**

Replace `app/src/full/java/com/keyjawn/BillingManager.kt` with:

```kotlin
package com.keyjawn

import android.app.Activity
import android.content.Context

class BillingManager(context: Context) {
    val isFullVersion = true  // Full flavor: all features enabled
    var onPurchaseStateChanged: (() -> Unit)? = null

    fun connect() {}
    fun launchPurchaseFlow(activity: Activity) {}
    fun destroy() {}
}
```

This keeps the same interface so `KeyJawnService` and `SettingsActivity` don't need changes to their `billingManager` references. The full flavor just always returns `true`.

**Step 2: Remove the Play Billing dependency**

In `app/build.gradle.kts`, remove:

```kotlin
"fullImplementation"("com.android.billingclient:billing-ktx:7.1.1")
```

**Step 3: Remove the upgrade button from SettingsActivity**

In `app/src/main/java/com/keyjawn/SettingsActivity.kt`, simplify `setupUpgradeButton()`:

```kotlin
private fun setupUpgradeButton() {
    val upgradeBtn = findViewById<Button>(R.id.upgrade_btn)
    upgradeBtn.visibility = View.GONE
}
```

And remove the `onPurchaseStateChanged` callback code.

**Step 4: Remove the upgrade button from the settings layout**

In `app/src/main/res/layout/activity_settings.xml`, either remove the upgrade button element entirely or leave it (it's set to GONE anyway).

**Step 5: Run tests**

```bash
./gradlew testFullDebugUnitTest
./gradlew testLiteDebugUnitTest
```

Expected: All tests pass (no test references BillingManager directly).

**Step 6: Commit**

```bash
git add app/
git commit -m "feat: remove Google Play Billing, full flavor features always enabled"
```

---

## Task 13: Update CLAUDE.md

**Files:**
- Modify: `CLAUDE.md`
- Modify: `/home/jamditis/CLAUDE.md` (root)

Add keyjawn-store service to the service table and tunnel config in both files.

```bash
git add CLAUDE.md
git commit -m "docs: add keyjawn-store to service and tunnel documentation"
```

---

## Task 14: End-to-end test

1. Start the store service: `sudo systemctl start keyjawn-store`
2. Verify health: `curl https://store.keyjawn.amditis.tech/api/health`
3. Create a test release: `curl -X POST https://store.keyjawn.amditis.tech/api/releases -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -d '{"version":"0.1.0","r2_key":"keyjawn/releases/test.apk"}'`
4. Test the Stripe webhook with Stripe CLI: `stripe listen --forward-to https://store.keyjawn.amditis.tech/webhook/stripe`
5. Make a test purchase via the Stripe Payment Link (use Stripe test mode)
6. Verify: user appears in admin dashboard
7. Verify: download email received
8. Test download: enter the test email on the download page
9. Verify: signed R2 URL returned
10. Test support: submit a ticket, verify Telegram alert
11. Verify: admin dashboard shows the ticket

---

## Sequence summary

| Task | What | Depends on |
|------|------|-----------|
| 1 | Scaffold service | Nothing |
| 2 | Stripe webhook | Task 1 |
| 3 | Download endpoint | Task 1 |
| 4 | Support tickets | Task 1 |
| 5 | Release registration | Task 1 |
| 6 | Admin dashboard | Tasks 2-5 |
| 7 | Systemd + tunnel | Task 1 |
| 8 | Email setup | Nothing (manual) |
| 9 | Stripe setup | Nothing (manual) |
| 10 | Website changes | Tasks 2-4 (needs API endpoints) |
| 11 | CI changes | Tasks 5, 7 |
| 12 | App changes | Nothing |
| 13 | Update docs | Tasks 7, 12 |
| 14 | E2E test | Everything |

Tasks 1-5 are sequential. Tasks 6-9 can be parallelized after task 5. Tasks 10-12 can be parallelized. Task 13-14 are final.
