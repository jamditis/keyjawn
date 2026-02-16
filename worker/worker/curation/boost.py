"""Boost list for trusted content sources."""

from __future__ import annotations

BOOST_CHANNELS: dict[str, list[str]] = {
    "youtube": [
        "ThePrimeagen",
        "Fireship",
        "NetworkChuck",
        "TechHut",
        "Dreams of Code",
        "typecraft",
        "Chris Titus Tech",
        "Mental Outlaw",
        "Luke Smith",
        "DistroTube",
    ],
    "twitch": [
        "ThePrimeagen",
        "teaboraxofficial",
    ],
    "news_domains": [
        "github.com",
        "lobste.rs",
        "news.ycombinator.com",
        "dev.to",
    ],
}

_BOOST_LOOKUP: dict[str, set[str]] = {
    source: {name.lower() for name in names}
    for source, names in BOOST_CHANNELS.items()
}

BOOST_SCORE = 0.15


def is_boosted(source: str, author_or_domain: str) -> bool:
    """Check if a source/author is on the boost list."""
    lookup = _BOOST_LOOKUP.get(source, set())
    return author_or_domain.lower() in lookup
