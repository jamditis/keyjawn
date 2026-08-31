from pathlib import Path
import sqlite3

import db
import pytest


def test_sqlite_sidecars_are_ignored():
    repository_root = Path(__file__).parents[2]
    ignore_rules = (repository_root / ".gitignore").read_text().splitlines()

    assert "store/keyjawn-store.db-*" in ignore_rules


def test_suite_does_not_use_repository_database():
    repository_database = Path(__file__).parents[1] / "keyjawn-store.db"

    assert Path(db.DB_PATH).resolve() != repository_database.resolve()


def test_init_db_creates_tables(tmp_path, monkeypatch):
    database_path = tmp_path / "schema.db"
    monkeypatch.setattr(db, "DB_PATH", str(database_path))
    db.init_db()

    conn = db.get_db()
    tables = [
        row[0]
        for row in conn.execute(
            "SELECT name FROM sqlite_master WHERE type='table'"
        ).fetchall()
    ]
    conn.close()

    assert {"users", "downloads", "tickets", "releases"}.issubset(tables)


def test_users_email_is_unique_without_case_sensitivity(tmp_path, monkeypatch):
    database_path = tmp_path / "schema.db"
    monkeypatch.setattr(db, "DB_PATH", str(database_path))
    db.init_db()

    conn = db.get_db()
    conn.execute("INSERT INTO users (email) VALUES (?)", ("Buyer@Example.com",))
    with pytest.raises(sqlite3.IntegrityError):
        conn.execute("INSERT INTO users (email) VALUES (?)", ("buyer@example.com",))
    conn.close()


def test_init_db_fails_safely_on_legacy_case_collisions(tmp_path, monkeypatch):
    database_path = tmp_path / "legacy.db"
    conn = sqlite3.connect(database_path)
    conn.execute(
        "CREATE TABLE users (id INTEGER PRIMARY KEY, email TEXT UNIQUE NOT NULL)"
    )
    conn.execute("INSERT INTO users (email) VALUES (?)", ("Buyer@Example.com",))
    conn.execute("INSERT INTO users (email) VALUES (?)", ("buyer@example.com",))
    conn.commit()
    conn.close()
    monkeypatch.setattr(db, "DB_PATH", str(database_path))

    with pytest.raises(RuntimeError, match="case-variant"):
        db.init_db()

    conn = sqlite3.connect(database_path)
    count = conn.execute("SELECT COUNT(*) FROM users").fetchone()[0]
    conn.close()
    assert count == 2
