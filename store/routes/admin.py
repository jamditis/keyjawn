import os
from fastapi import APIRouter, Request, HTTPException
from fastapi.responses import RedirectResponse
from fastapi.templating import Jinja2Templates
from db import get_db

router = APIRouter(prefix="/admin")
templates = Jinja2Templates(directory=os.path.join(os.path.dirname(os.path.dirname(__file__)), "templates"))

def get_admin_token():
    return os.environ.get("ADMIN_TOKEN", "")

def check_auth(request: Request):
    token = request.cookies.get("admin_token")
    if token != get_admin_token():
        raise HTTPException(302, headers={"Location": "/admin/login"})

@router.get("/login")
async def login_page(request: Request):
    return templates.TemplateResponse(request, "admin/login.html", {})

@router.post("/login")
async def login(request: Request):
    form = await request.form()
    token = form.get("token", "")
    if token != get_admin_token():
        return templates.TemplateResponse(request, "admin/login.html", {"error": "Invalid token"}, status_code=401)
    response = RedirectResponse("/admin", status_code=303)
    response.set_cookie("admin_token", token, httponly=True, samesite="strict", secure=True)
    return response

@router.post("/logout")
async def logout():
    response = RedirectResponse("/admin/login", status_code=303)
    response.delete_cookie("admin_token")
    return response

@router.get("")
async def dashboard(request: Request):
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
    return templates.TemplateResponse(request, "admin/dashboard.html", {
        "stats": stats, "recent": recent, "active_page": "dashboard"
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
    return templates.TemplateResponse(request, "admin/users.html", {
        "users": users, "query": q, "active_page": "users"
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
    return templates.TemplateResponse(request, "admin/tickets.html", {
        "tickets": tickets, "active_page": "tickets"
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
    return templates.TemplateResponse(request, "admin/releases.html", {
        "releases": releases, "active_page": "releases"
    })
