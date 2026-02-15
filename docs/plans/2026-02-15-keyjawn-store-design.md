# KeyJawn store — payment, distribution, and support

**Date:** 2026-02-15
**Status:** Approved

## Problem

KeyJawn needs a payment system, a paid user database, and APK download gating. The current approach (Google Play Billing via in-app purchase) doesn't give the developer a server-side record of who paid. Users who pay expect quality support, and there's no way to verify a user's purchase status for priority support. The full APK is accessible on GitHub Releases to anyone.

## Solution

A standalone FastAPI service (`keyjawn-store`) running on houseofjawn that handles Stripe payments, stores paid user records, gates full APK downloads, and provides priority support ticketing.

## Decisions

| Decision | Choice |
|----------|--------|
| Play Store | Keep free (lite) listing |
| Payment | Stripe Payment Links, $4 one-time |
| User database | SQLite on houseofjawn |
| APK delivery | Email-based — enter email on download page, get latest version |
| Email sender | `support@keyjawn.amditis.tech` via Cloudflare Email Routing + Resend |
| Purchase receipts | Stripe auto-receipts + download email from keyjawn address |
| Support | GitHub Issues (public) + priority ticket system for paid users |
| APK gating | Full APK on R2, not on public GitHub Releases |
| Admin | htmx dashboard at `store.keyjawn.amditis.tech/admin` |

## Architecture

**Service:** `keyjawn-store`
**Location:** `/home/jamditis/projects/keyjawn/store/`
**Stack:** Python 3, FastAPI, SQLite, uvicorn
**Port:** 5060
**Tunnel:** `store.keyjawn.amditis.tech` via houseofjawn Cloudflare tunnel
**Systemd:** `keyjawn-store.service`

### Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/webhook/stripe` | Stripe webhook handler |
| `POST` | `/api/download` | Verify email, return signed R2 URL |
| `POST` | `/api/support` | Submit priority support ticket |
| `POST` | `/api/releases` | Register new APK version (from CI) |
| `GET` | `/api/health` | Health check |
| `GET` | `/admin` | Admin dashboard |
| `GET` | `/admin/users` | User list |
| `GET` | `/admin/tickets` | Support tickets |
| `GET` | `/admin/releases` | Release list |

Admin endpoints protected by API token. Public endpoints verify email against the purchase database.

### APK storage

Full APK stored on Cloudflare R2 (`pi-transfer` bucket). CI uploads on release tags. Download endpoint generates signed R2 URLs that expire in 1 hour.

## Database schema

Single SQLite file at `store/keyjawn-store.db`.

```sql
CREATE TABLE users (
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

CREATE TABLE downloads (
    id INTEGER PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id),
    version TEXT NOT NULL,
    ip_address TEXT,
    user_agent TEXT,
    downloaded_at TEXT NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE tickets (
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

CREATE TABLE releases (
    id INTEGER PRIMARY KEY,
    version TEXT UNIQUE NOT NULL,
    r2_key TEXT NOT NULL,
    file_size INTEGER,
    sha256 TEXT,
    released_at TEXT NOT NULL DEFAULT (datetime('now'))
);
```

## Stripe integration

### Payment flow

1. User clicks "Buy full version" on `keyjawn.amditis.tech/pricing`
2. Link goes to Stripe Payment Link (created in Stripe dashboard)
3. Stripe handles the entire checkout UI
4. After payment, Stripe redirects to `keyjawn.amditis.tech/thanks`
5. Stripe fires `checkout.session.completed` webhook to `store.keyjawn.amditis.tech/webhook/stripe`
6. Webhook creates user record in `users` table
7. Service sends download instructions email via Resend from `support@keyjawn.amditis.tech`
8. Stripe also sends its own receipt automatically

### Stripe config

- Product: "KeyJawn full version" — one-time, $4.00 USD
- Webhook event: `checkout.session.completed`
- Webhook secret: `pass show claude/services/stripe-webhook-secret`
- API key: `pass show claude/services/stripe-api-key`

### Webhook handler

```python
@app.post("/webhook/stripe")
async def stripe_webhook(request: Request):
    payload = await request.body()
    sig = request.headers.get("stripe-signature")
    event = stripe.Webhook.construct_event(payload, sig, webhook_secret)

    if event["type"] == "checkout.session.completed":
        session = event["data"]["object"]
        email = session["customer_details"]["email"]
        db.execute("""
            INSERT INTO users (email, stripe_customer_id, stripe_payment_intent, amount_cents)
            VALUES (?, ?, ?, ?)
            ON CONFLICT(email) DO NOTHING
        """, [email, session.get("customer"), session.get("payment_intent"), session["amount_total"]])
        send_download_email(email)

    return {"status": "ok"}
```

## Download flow

1. User visits `keyjawn.amditis.tech/download` (linked from thanks page and confirmation email)
2. Enters the email used at checkout
3. Form POSTs to `store.keyjawn.amditis.tech/api/download`
4. Server checks email against `users` table
5. If found: returns signed R2 URL (1-hour expiry), increments download count, logs to `downloads`
6. If not found: error message with suggestion to check email or contact support
7. Rate limit: 5 download requests per email per day

For future updates: same flow. User revisits the download page, enters email, gets the latest version.

## Email setup

**Receiving:** Cloudflare Email Routing — `support@keyjawn.amditis.tech` forwards to `jamditis@gmail.com`

**Sending:** Resend transactional email service (free tier: 100 emails/day)
- Purchase confirmation with download link
- Support ticket confirmation
- (Future) Update notifications

## Priority support

1. User visits `keyjawn.amditis.tech/support`
2. Below the public FAQ, a "Priority support" form
3. Fields: email, subject, description, device model, Android version, app version
4. POSTs to `store.keyjawn.amditis.tech/api/support`
5. Server verifies email is in `users` table
6. Creates ticket in `tickets` table
7. Sends Telegram notification to Joe (via bot API directly)
8. Sends confirmation email to user
9. If email not verified: "This email doesn't match a purchase. For general questions, use GitHub Issues."

Ticket statuses: open, in_progress, resolved, closed. Replies happen via email.

## Admin dashboard

At `store.keyjawn.amditis.tech/admin`, htmx-based, token-protected.

**Dashboard** (`/admin`): totals (users, revenue, downloads, open tickets), recent purchases, recent downloads.

**Users** (`/admin/users`): table of all paid users with email, purchase date, download count. Search by email. Click to view details (Stripe IDs, download history).

**Tickets** (`/admin/tickets`): open tickets with subject, user email, status, date. Status update dropdown. Reply link (mailto).

**Releases** (`/admin/releases`): version list with size, hash, date, download count. Manual upload fallback.

**Auth:** Bearer token in cookie, stored at `pass show claude/tokens/keyjawn-store-admin`.

## Website changes

**Pricing page** (`pricing.astro`): free tier links to Play Store (lite), full tier links to Stripe Payment Link. Update sideload note.

**New pages:**
- `download.astro` — email-verified download form
- `thanks.astro` — post-purchase landing with download form

**Support page** (`support.astro`): add priority support form below existing content.

**Nav:** add "Download" link.

## App changes

- Remove `BillingManager.kt` from both flavors
- Remove Google Play Billing Library dependency
- Full flavor: all features enabled by default (no purchase check)
- Lite flavor: no changes (stubs remain)
- No license verification in the app — the APK download is the gate

## CI changes (`.github/workflows/build.yml`)

On release tags:
1. Build both APKs
2. Upload lite APK to GitHub Releases (public)
3. Upload full APK to R2 via `wrangler r2 object put`
4. Call `store.keyjawn.amditis.tech/api/releases` to register the new version
5. Do not upload full APK to GitHub Releases

## Scope boundaries

### In scope

- keyjawn-store FastAPI service
- SQLite database
- Stripe Payment Link + webhook
- Download page with email verification
- Priority support ticketing
- Admin dashboard (htmx)
- Cloudflare Email Routing + Resend setup
- Website pricing/download/thanks/support page updates
- CI pipeline changes for R2 upload
- Remove BillingManager from the app

### Out of scope

- User accounts / login system
- License key verification in the app
- Subscription billing
- Refund automation (handle manually in Stripe dashboard)
- Multiple products / pricing tiers
- Analytics beyond what the admin dashboard shows
