import os
import sys
import tempfile
from pathlib import Path

import pytest


os.environ.setdefault("ADMIN_TOKEN", "testtoken")
os.environ.setdefault("STRIPE_WEBHOOK_SECRET", "test-webhook-secret")
os.environ.setdefault("UNSUBSCRIBE_SECRET", "test-unsubscribe-secret")

STORE_DIRECTORY = Path(__file__).parents[1]
sys.path.insert(0, str(STORE_DIRECTORY))

import db  # noqa: E402


# Test modules import the FastAPI application during collection. Give that
# import a disposable database before any per-test fixture can run.
_SESSION_DATABASE_DIRECTORY = tempfile.TemporaryDirectory(prefix="keyjawn-store-tests-")
db.DB_PATH = str(Path(_SESSION_DATABASE_DIRECTORY.name) / "collection.db")


@pytest.fixture(autouse=True)
def isolated_database(tmp_path, monkeypatch):
    database_path = tmp_path / "keyjawn-store.db"
    monkeypatch.setattr(db, "DB_PATH", str(database_path))
    db.init_db()
    return database_path
