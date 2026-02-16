import sys, os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import tempfile
from unittest.mock import patch

def test_init_db_creates_tables():
    with tempfile.NamedTemporaryFile(suffix=".db", delete=False) as f:
        tmp_path = f.name
    try:
        with patch("db.DB_PATH", tmp_path):
            from db import init_db, get_db
            init_db()
            conn = get_db()
            tables = [r[0] for r in conn.execute("SELECT name FROM sqlite_master WHERE type='table'").fetchall()]
            conn.close()
            assert "users" in tables
            assert "downloads" in tables
            assert "tickets" in tables
            assert "releases" in tables
    finally:
        os.unlink(tmp_path)
