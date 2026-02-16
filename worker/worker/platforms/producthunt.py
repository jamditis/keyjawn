"""Product Hunt platform client using the GraphQL API v2."""

from __future__ import annotations

import logging
from typing import Optional

import httpx

log = logging.getLogger(__name__)

API_URL = "https://api.producthunt.com/v2/api/graphql"

# Topics to monitor for relevant launches
MONITOR_TOPICS = [
    "developer-tools",
    "android",
    "productivity",
    "artificial-intelligence",
    "command-line-tools",
]

# Keywords that indicate a launch we should engage with
RELEVANCE_KEYWORDS = [
    "keyboard",
    "terminal",
    "cli",
    "ssh",
    "mobile",
    "android",
    "developer tool",
    "llm",
    "ai agent",
    "coding",
]


class ProductHuntClient:
    def __init__(self, developer_token: str):
        self.token = developer_token
        self._headers = {
            "Authorization": f"Bearer {developer_token}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        }

    async def _query(self, query: str, variables: dict = None) -> Optional[dict]:
        """Execute a GraphQL query against the Product Hunt API."""
        payload = {"query": query}
        if variables:
            payload["variables"] = variables

        try:
            async with httpx.AsyncClient() as client:
                resp = await client.post(
                    API_URL, json=payload, headers=self._headers, timeout=15
                )
                if resp.status_code != 200:
                    log.error("PH API error (%d): %s", resp.status_code, resp.text[:200])
                    return None
                data = resp.json()
                if "errors" in data:
                    log.error("PH GraphQL errors: %s", data["errors"])
                    return None
                return data.get("data")
        except Exception:
            log.exception("PH API request failed")
            return None

    async def get_today_posts(self, limit: int = 20) -> list[dict]:
        """Get today's featured posts on Product Hunt."""
        query = """
        query {
            posts(order: RANKING, first: %d) {
                edges {
                    node {
                        id
                        name
                        tagline
                        url
                        votesCount
                        commentsCount
                        website
                        topics {
                            edges {
                                node {
                                    slug
                                }
                            }
                        }
                    }
                }
            }
        }
        """ % limit

        data = await self._query(query)
        if not data:
            return []

        posts = []
        for edge in data.get("posts", {}).get("edges", []):
            node = edge["node"]
            topics = [
                t["node"]["slug"]
                for t in node.get("topics", {}).get("edges", [])
            ]
            posts.append({
                "id": node["id"],
                "name": node["name"],
                "tagline": node["tagline"],
                "url": node["url"],
                "votes": node["votesCount"],
                "comments": node["commentsCount"],
                "website": node.get("website", ""),
                "topics": topics,
            })

        return posts

    async def search_posts(self, query: str, limit: int = 10) -> list[dict]:
        """Search Product Hunt posts by keyword."""
        gql = """
        query($query: String!) {
            posts(order: RANKING, first: %d, topic: $query) {
                edges {
                    node {
                        id
                        name
                        tagline
                        url
                        votesCount
                        website
                    }
                }
            }
        }
        """ % limit

        data = await self._query(gql, {"query": query})
        if not data:
            return []

        return [
            {
                "id": edge["node"]["id"],
                "name": edge["node"]["name"],
                "tagline": edge["node"]["tagline"],
                "url": edge["node"]["url"],
                "votes": edge["node"]["votesCount"],
                "website": edge["node"].get("website", ""),
            }
            for edge in data.get("posts", {}).get("edges", [])
        ]

    async def get_post_details(self, post_id: str) -> Optional[dict]:
        """Get details about a specific post (useful for tracking our own launch)."""
        query = """
        query($id: ID!) {
            post(id: $id) {
                id
                name
                tagline
                url
                votesCount
                commentsCount
                reviewsCount
                website
                createdAt
            }
        }
        """

        data = await self._query(query, {"id": post_id})
        if not data or not data.get("post"):
            return None

        node = data["post"]
        return {
            "id": node["id"],
            "name": node["name"],
            "tagline": node["tagline"],
            "url": node["url"],
            "votes": node["votesCount"],
            "comments": node["commentsCount"],
            "reviews": node.get("reviewsCount", 0),
            "website": node.get("website", ""),
            "created_at": node.get("createdAt", ""),
        }

    def score_relevance(self, post: dict) -> float:
        """Score a Product Hunt post for relevance to KeyJawn's audience."""
        text = f"{post.get('name', '')} {post.get('tagline', '')}".lower()
        topics = post.get("topics", [])

        score = 0.0

        # Check keywords in name/tagline
        keyword_hits = sum(1 for kw in RELEVANCE_KEYWORDS if kw in text)
        if keyword_hits >= 2:
            score = 0.7
        elif keyword_hits >= 1:
            score = 0.4

        # Boost for relevant topics
        relevant_topics = set(topics) & set(MONITOR_TOPICS)
        if relevant_topics:
            score = min(score + 0.2, 1.0)

        return score

    async def find_relevant_launches(self) -> list[dict]:
        """Find today's launches that are relevant to KeyJawn's audience."""
        posts = await self.get_today_posts(limit=30)

        relevant = []
        for post in posts:
            score = self.score_relevance(post)
            if score >= 0.4:
                post["relevance_score"] = score
                relevant.append(post)

        relevant.sort(key=lambda p: p["relevance_score"], reverse=True)
        return relevant
