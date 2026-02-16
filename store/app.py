import os
import logging
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from db import init_db

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(name)s %(levelname)s %(message)s")
log = logging.getLogger("keyjawn-store")

app = FastAPI(title="keyjawn-store", docs_url=None, redoc_url=None)
app.mount("/static", StaticFiles(directory=os.path.join(os.path.dirname(__file__), "static")), name="static")

init_db()

from routes.webhook import router as webhook_router
app.include_router(webhook_router)

from routes.download import router as download_router
app.include_router(download_router)

from routes.support import router as support_router
app.include_router(support_router)

from routes.releases import router as releases_router
app.include_router(releases_router)

from routes.admin import router as admin_router
app.include_router(admin_router)

from routes.unsubscribe import router as unsubscribe_router
app.include_router(unsubscribe_router)

@app.get("/api/health")
async def health():
    return {"status": "ok", "service": "keyjawn-store"}
