"""Twitter platform client using twikit (no API key needed)."""

from __future__ import annotations

import logging
from typing import Optional

from twikit import Client as TwikitClient

from worker.config import TwitterConfig

log = logging.getLogger(__name__)

SEARCH_KEYWORDS = [
    "mobile SSH keyboard",
    "CLI keyboard android",
    "terminal keyboard phone",
    "Claude Code mobile",
    "Claude Code phone",
    "SSH from phone",
    "Gemini CLI mobile",
    "aichat mobile keyboard",
]


class TwitterClient:
    def __init__(self, config: TwitterConfig):
        self.config = config
        self._client: Optional[TwikitClient] = None

    async def _ensure_client(self) -> TwikitClient:
        """Lazy init: load cookies or login, then return the client."""
        if self._client is not None:
            return self._client

        client = TwikitClient(language="en-US")

        try:
            client.load_cookies(self.config.cookies_path)
            log.info("loaded twitter cookies from %s", self.config.cookies_path)
        except Exception:
            log.info("no cookies found, logging in as %s", self.config.username)
            await client.login(
                auth_info_1=self.config.username,
                auth_info_2=self.config.email,
                password=self.config.password,
            )
            client.save_cookies(self.config.cookies_path)
            log.info("saved twitter cookies to %s", self.config.cookies_path)

        self._client = client
        return client

    @staticmethod
    def build_search_query(terms: list[str]) -> str:
        """Join search terms with OR and add filters."""
        quoted = " OR ".join(f'"{t}"' for t in terms)
        return f"{quoted} -from:KeyJawn -is:retweet lang:en"

    @staticmethod
    def validate_post(text: str) -> None:
        """Raise ValueError if text exceeds 280 characters."""
        if len(text) > 280:
            raise ValueError(
                f"tweet length {len(text)} exceeds 280 character limit"
            )

    async def search(
        self,
        query: str | None = None,
        max_results: int = 20,
    ) -> list[dict]:
        """Search tweets. Uses SEARCH_KEYWORDS if no query provided."""
        if query is None:
            query = self.build_search_query(SEARCH_KEYWORDS)

        try:
            client = await self._ensure_client()
            results = await client.search_tweet(
                query, product="Latest", count=max_results
            )
            tweets = []
            for tweet in results:
                author = ""
                if tweet.user is not None:
                    author = tweet.user.screen_name
                tweets.append(
                    {
                        "id": tweet.id,
                        "text": tweet.text,
                        "author": author,
                        "url": f"https://x.com/{author}/status/{tweet.id}",
                        "created_at": tweet.created_at,
                    }
                )
            return tweets
        except Exception:
            log.exception("twitter search failed")
            return []

    async def post(self, text: str) -> Optional[str]:
        """Post a tweet. Returns the tweet URL or None on failure."""
        self.validate_post(text)
        try:
            client = await self._ensure_client()
            tweet = await client.create_tweet(text=text)
            return f"https://x.com/KeyJawn/status/{tweet.id}"
        except Exception:
            log.exception("twitter post failed")
            return None

    async def reply(self, text: str, in_reply_to: str) -> Optional[str]:
        """Reply to a tweet. Returns the reply URL or None on failure."""
        self.validate_post(text)
        try:
            client = await self._ensure_client()
            tweet = await client.create_tweet(text=text, reply_to=in_reply_to)
            return f"https://x.com/KeyJawn/status/{tweet.id}"
        except Exception:
            log.exception("twitter reply failed")
            return None

    async def like(self, tweet_id: str) -> bool:
        """Like a tweet. Returns True on success."""
        try:
            client = await self._ensure_client()
            await client.favorite_tweet(tweet_id)
            return True
        except Exception:
            log.exception("twitter like failed")
            return False

    async def retweet(self, tweet_id: str) -> bool:
        """Retweet a tweet. Returns True on success."""
        try:
            client = await self._ensure_client()
            await client.retweet(tweet_id)
            return True
        except Exception:
            log.exception("twitter retweet failed")
            return False
