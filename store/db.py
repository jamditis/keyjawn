import sqlite3
import os

DB_PATH = os.path.join(os.path.dirname(__file__), "keyjawn-store.db")


def normalize_email(email: str) -> str:
    """Use one email identity for storage, tokens, and account lookups."""
    return str(email).strip().lower()


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
            email TEXT COLLATE NOCASE UNIQUE NOT NULL,
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
            changelog TEXT,
            released_at TEXT NOT NULL DEFAULT (datetime('now'))
        );
    """)
    try:
        conn.execute(
            "CREATE UNIQUE INDEX IF NOT EXISTS users_email_nocase "
            "ON users(email COLLATE NOCASE)"
        )
    except sqlite3.IntegrityError as error:
        conn.rollback()
        conn.close()
        raise RuntimeError(
            "users contains case-variant email collisions; resolve them before startup"
        ) from error
    # Add changelog column if missing (existing DBs)
    try:
        conn.execute("ALTER TABLE releases ADD COLUMN changelog TEXT")
    except sqlite3.OperationalError:
        pass  # column already exists
    # Add unsubscribed column if missing (existing DBs)
    try:
        conn.execute("ALTER TABLE users ADD COLUMN unsubscribed INTEGER NOT NULL DEFAULT 0")
    except sqlite3.OperationalError:
        pass  # column already exists
    conn.commit()
    conn.close()
