from datetime import datetime, timezone
from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, EmailStr
from db import get_db
from r2 import generate_signed_url, GITHUB_RELEASES

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
    today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
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

    version = release["version"]
    r2_key = release["r2_key"]

    # Generate presigned R2 URL, fall back to GitHub releases
    url = generate_signed_url(r2_key)
    if not url:
        url = f"{GITHUB_RELEASES}/tag/v{version}"

    # Log download
    conn.execute("""
        INSERT INTO downloads (user_id, version) VALUES (?, ?)
    """, (user["id"], version))
    conn.execute("""
        UPDATE users SET download_count = download_count + 1, last_download_at = datetime('now')
        WHERE id = ?
    """, (user["id"],))
    conn.commit()
    conn.close()

    return {
        "url": url,
        "version": version,
    }
