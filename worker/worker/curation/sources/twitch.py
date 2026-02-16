"""Twitch Helix API source monitor for dev-related clips."""

from __future__ import annotations

import logging
from datetime import datetime, timedelta, timezone
from typing import Optional

import httpx

from worker.curation.models import CurationCandidate

log = logging.getLogger(__name__)

API_BASE = "https://api.twitch.tv/helix"
TOKEN_URL = "https://id.twitch.tv/oauth2/token"

CATEGORIES = {
    "Science & Technology": "509670",
    "Software and Game Development": "1469308723",
}


class TwitchSource:
    def __init__(self, client_id: str, client_secret: str):
        self.client_id = client_id
        self.client_secret = client_secret
        self._access_token: Optional[str] = None

    async def _ensure_token(self) -> str:
        """Get or refresh the OAuth app access token."""
        if self._access_token:
            return self._access_token

        try:
            async with httpx.AsyncClient() as client:
                resp = await client.post(TOKEN_URL, params={
                    "client_id": self.client_id,
                    "client_secret": self.client_secret,
                    "grant_type": "client_credentials",
                })
                if resp.status_code != 200:
                    log.error("Twitch token error (%d): %s", resp.status_code, resp.text[:200])
                    return ""
                self._access_token = resp.json()["access_token"]
                return self._access_token
        except Exception:
            log.exception("Twitch token request failed")
            return ""

    def _headers(self, token: str) -> dict:
        return {
            "Client-ID": self.client_id,
            "Authorization": f"Bearer {token}",
        }

    def _parse_clip(self, clip: dict) -> CurationCandidate:
        """Parse a Twitch clip into a CurationCandidate."""
        published = None
        if clip.get("created_at"):
            try:
                published = datetime.fromisoformat(
                    clip["created_at"].replace("Z", "+00:00")
                )
            except (ValueError, TypeError):
                pass

        return CurationCandidate(
            source="twitch",
            url=clip.get("url", ""),
            title=clip.get("title", ""),
            description=clip.get("title", ""),
            author=clip.get("broadcaster_name", ""),
            published=published,
            metadata={
                "clip_id": clip.get("id", ""),
                "view_count": clip.get("view_count", 0),
                "game_id": clip.get("game_id", ""),
                "broadcaster": clip.get("broadcaster_name", ""),
            },
        )

    async def get_clips(self, game_id: str, max_results: int = 20) -> list[CurationCandidate]:
        """Get recent clips for a game/category."""
        token = await self._ensure_token()
        if not token:
            return []

        started_at = (datetime.now(timezone.utc) - timedelta(days=7)).strftime("%Y-%m-%dT%H:%M:%SZ")

        try:
            async with httpx.AsyncClient() as client:
                resp = await client.get(
                    f"{API_BASE}/clips",
                    params={
                        "game_id": game_id,
                        "first": max_results,
                        "started_at": started_at,
                    },
                    headers=self._headers(token),
                    timeout=15,
                )
                if resp.status_code != 200:
                    log.error("Twitch clips error (%d): %s", resp.status_code, resp.text[:200])
                    return []
                data = resp.json()
                return [self._parse_clip(c) for c in data.get("data", [])]
        except Exception:
            log.exception("Twitch clips request failed")
            return []

    async def scan(self) -> list[CurationCandidate]:
        """Scan all monitored categories for clips."""
        seen_urls = set()
        all_candidates = []

        for name, game_id in CATEGORIES.items():
            clips = await self.get_clips(game_id, max_results=10)
            for c in clips:
                if c.url and c.url not in seen_urls:
                    seen_urls.add(c.url)
                    all_candidates.append(c)

        log.info("Twitch scan: %d clips from %d categories", len(all_candidates), len(CATEGORIES))
        return all_candidates
