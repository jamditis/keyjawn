import os
from fastapi import APIRouter, HTTPException, Header
from pydantic import BaseModel
from typing import Optional
from db import get_db

router = APIRouter()


def require_admin(authorization: Optional[str] = Header(None)):
    admin_token = os.environ.get("ADMIN_TOKEN", "")
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(401, "Missing or invalid token")
    token = authorization.split(" ", 1)[1]
    if token != admin_token:
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
