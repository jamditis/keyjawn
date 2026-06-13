"""Social-scroller platform client — browser-based feed monitoring via CDP.

Runs social-scroller.py locally (on officejawn, where DISPLAY=:99 and
the CDP browser live) and parses JSON output into findings for the monitor.

Two capabilities:
  1. search(query, platform) — keyword search on a specific platform
  2. scan_feeds(platforms) — scroll open feed tabs and extract visible posts
"""

from __future__ import annotations

import asyncio
import json
import logging

from worker.config import SocialScrollerConfig

log = logging.getLogger(__name__)


class SocialScrollerClient:
    def __init__(self, config: SocialScrollerConfig):
        self.config = config

    async def _run_cmd(self, cmd: str, timeout: int = 120) -> str | None:
        """Run a social-scroller command, locally or via SSH.

        When ssh_host is empty, runs directly on the local machine (the
        worker and the DISPLAY=:99 virtual desktop are both on officejawn).
        When ssh_host is set, wraps the command in ssh.
        """
        if self.config.ssh_host:
            # Use double quotes for SSH wrapper so inner single quotes (from
            # _shell_quote) pass through correctly
            full_cmd = f'ssh {self.config.ssh_host} "{cmd}"'
        else:
            full_cmd = cmd
        try:
            proc = await asyncio.create_subprocess_shell(
                full_cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
            )
            stdout, stderr = await asyncio.wait_for(
                proc.communicate(), timeout=timeout,
            )
            if proc.returncode != 0:
                log.warning(
                    "social-scroller exited %d: %s",
                    proc.returncode,
                    stderr.decode().strip()[:200],
                )
                return None
            return stdout.decode()
        except asyncio.TimeoutError:
            log.warning("social-scroller timed out after %ds", timeout)
            proc.kill()
            return None
        except Exception:
            log.exception("social-scroller run failed")
            return None

    def _parse_posts(self, raw_json: str | None) -> list[dict]:
        """Parse JSON stdout from social-scroller into findings format."""
        if not raw_json or not raw_json.strip():
            return []
        try:
            posts = json.loads(raw_json)
        except json.JSONDecodeError:
            log.warning("failed to parse social-scroller JSON output")
            return []

        findings = []
        for post in posts:
            text = post.get("text", "")
            author = post.get("username", "")
            platform = post.get("platform", "")
            link = post.get("link", "")

            # Skip empty posts
            if not text and not author:
                continue

            findings.append({
                "url": link or post.get("id", ""),
                "text": text,
                "author": author,
                "platform": platform,
            })

        return findings

    async def search(
        self, query: str, platform: str, duration: int | None = None,
    ) -> list[dict]:
        """Search a platform for a query via browser.

        Args:
            query: Search terms.
            platform: Platform name (twitter, bluesky, etc).
            duration: Seconds to scroll results.

        Returns findings in {url, text, author, platform} format.
        """
        dur = duration or self.config.scroll_duration
        # Global flags (--json, --no-screenshots, -d) must come BEFORE subcommand
        cmd = (
            f"DISPLAY=:99 python3 {self.config.script_path} "
            f"--json --no-screenshots --no-all-captures -d {dur} "
            f"search -p {platform} -q {_shell_quote(query)}"
        )
        # Search takes longer: scroll time + page load + extraction
        raw = await self._run_cmd(cmd, timeout=dur + 60)
        return self._parse_posts(raw)

    async def search_with_strategy(self) -> list[dict]:
        """Run platform-specific search strategies.

        Searches configured platforms with their keywords.
        Returns an empty list if no search_platforms are configured.
        """
        all_findings = []

        for platform in self.config.search_platforms:
            keywords = self.config.platform_keywords.get(platform, [])
            if not keywords:
                continue

            for keyword in keywords[:3]:  # cap at 3 to limit time
                try:
                    results = await self.search(keyword, platform)
                    all_findings.extend(results)
                    log.info(
                        "%s search '%s': %d posts",
                        platform, keyword, len(results),
                    )
                except Exception:
                    log.exception(
                        "%s search failed for '%s'", platform, keyword,
                    )

        return all_findings

    async def scan_feeds(
        self, platforms: list[str] | None = None,
        duration: int | None = None,
    ) -> list[dict]:
        """Scroll open feed tabs and extract posts.

        Returns findings in {url, text, author, platform} format.
        """
        dur = duration or self.config.scroll_duration
        feeds = platforms or list(self.config.feed_platforms)
        feeds_arg = ",".join(feeds)
        # Global flags must come BEFORE subcommand
        cmd = (
            f"DISPLAY=:99 python3 {self.config.script_path} "
            f"--json --no-screenshots --no-all-captures -d {dur} "
            f"scroll --feeds {feeds_arg}"
        )
        # Multiple feeds: each takes `dur` seconds
        timeout = (dur * len(feeds)) + 60
        raw = await self._run_cmd(cmd, timeout=timeout)
        return self._parse_posts(raw)


def _shell_quote(s: str) -> str:
    """Escape a string for use inside single-quoted SSH commands."""
    # Replace single quotes with '\'' (end quote, escaped quote, start quote)
    return "'" + s.replace("'", "'\\''") + "'"
