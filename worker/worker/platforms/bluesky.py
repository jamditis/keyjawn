"""Bluesky platform client using the AT Protocol."""

from __future__ import annotations

import logging
from typing import Optional

from atproto import Client

from worker.config import BlueskyConfig

log = logging.getLogger(__name__)

SEARCH_KEYWORDS = [
    "mobile SSH keyboard",
    "CLI keyboard android",
    "terminal keyboard phone",
    "Claude Code mobile",
    "SSH from phone",
]


class BlueskyClient:
    def __init__(self, config: BlueskyConfig):
        self.config = config
        self._client: Optional[Client] = None

    @property
    def client(self) -> Client:
        if self._client is None:
            self._client = Client()
            self._client.login(
                self.config.handle, self.config.app_password
            )
        return self._client

    def validate_post(self, text: str) -> None:
        """Raise ValueError if text exceeds 300 characters."""
        if len(text) > 300:
            raise ValueError(f"post length {len(text)} exceeds 300")

    async def search(self, query: str, limit: int = 20) -> list[dict]:
        """Search Bluesky posts."""
        try:
            response = self.client.app.bsky.feed.search_posts(
                {"q": query, "limit": limit}
            )
            results = []
            for post in response.posts:
                results.append({
                    "uri": post.uri,
                    "text": post.record.text,
                    "author": post.author.handle,
                    "url": _post_url(post.author.handle, post.uri),
                    "created_at": post.record.created_at,
                })
            return results
        except Exception:
            log.exception("bluesky search failed")
            return []

    async def post(self, text: str) -> Optional[str]:
        """Create a Bluesky post. Returns the post URL or None."""
        self.validate_post(text)
        try:
            response = self.client.send_post(text=text)
            url = _post_url(self.config.handle, response.uri)
            log.info("posted to bluesky: %s", url)
            return url
        except Exception:
            log.exception("bluesky post failed")
            return None

    async def reply(
        self,
        text: str,
        parent_uri: str,
        parent_cid: str,
        root_uri: Optional[str] = None,
        root_cid: Optional[str] = None,
    ) -> Optional[str]:
        """Reply to a Bluesky post."""
        self.validate_post(text)
        try:
            from atproto import models

            parent_ref = models.create_strong_ref(parent_uri, parent_cid)
            root_ref = (
                models.create_strong_ref(root_uri, root_cid)
                if root_uri and root_cid
                else parent_ref
            )
            response = self.client.send_post(
                text=text,
                reply_to=models.AppBskyFeedPost.ReplyRef(
                    parent=parent_ref, root=root_ref,
                ),
            )
            url = _post_url(self.config.handle, response.uri)
            log.info("replied on bluesky: %s", url)
            return url
        except Exception:
            log.exception("bluesky reply failed")
            return None

    async def like(self, uri: str, cid: str) -> bool:
        """Like a Bluesky post."""
        try:
            self.client.like(uri, cid)
            return True
        except Exception:
            log.exception("bluesky like failed")
            return False

    async def repost(self, uri: str) -> bool:
        """Repost a Bluesky post."""
        try:
            # Need to resolve the CID from the URI
            parts = uri.split("/")
            if len(parts) >= 5:
                response = self.client.app.bsky.feed.get_posts(
                    {"uris": [uri]}
                )
                if response.posts:
                    post = response.posts[0]
                    self.client.repost(post.uri, post.cid)
                    log.info("reposted on bluesky: %s", uri)
                    return True
            return False
        except Exception:
            log.exception("bluesky repost failed")
            return False


def _post_url(handle: str, uri: str) -> str:
    """Convert an AT URI to a bsky.app URL."""
    parts = uri.split("/")
    rkey = parts[-1]
    return f"https://bsky.app/profile/{handle}/post/{rkey}"
