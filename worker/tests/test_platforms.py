import pytest

from worker.config import TwitterConfig
from worker.platforms.twitter import TwitterClient, SEARCH_KEYWORDS


def _make_config() -> TwitterConfig:
    return TwitterConfig(
        username="test-user",
        email="test@example.com",
        password="test-password",
        cookies_path="/tmp/test-cookies.json",
    )


def test_twitter_client_init():
    config = _make_config()
    client = TwitterClient(config)
    assert client.config is config
    assert client._client is None


def test_twitter_search_query_builder():
    client = TwitterClient(_make_config())
    terms = ["mobile SSH keyboard", "CLI keyboard android"]
    query = client.build_search_query(terms)
    assert '"mobile SSH keyboard"' in query
    assert '"CLI keyboard android"' in query
    assert " OR " in query
    assert "-from:KeyJawn" in query
    assert "-is:retweet" in query
    assert "lang:en" in query


def test_twitter_search_query_single_term():
    client = TwitterClient(_make_config())
    query = client.build_search_query(["one term"])
    assert query == '"one term" -from:KeyJawn -is:retweet lang:en'


def test_twitter_post_too_long():
    client = TwitterClient(_make_config())
    text = "x" * 281
    with pytest.raises(ValueError, match="exceeds 280"):
        client.validate_post(text)


def test_twitter_post_valid():
    client = TwitterClient(_make_config())
    # Should not raise for exactly 280 characters.
    client.validate_post("x" * 280)
    # Should not raise for shorter text.
    client.validate_post("hello")


def test_search_keywords_defined():
    assert isinstance(SEARCH_KEYWORDS, list)
    assert len(SEARCH_KEYWORDS) > 0
    # All entries should be non-empty strings.
    for kw in SEARCH_KEYWORDS:
        assert isinstance(kw, str)
        assert len(kw) > 0


# --- Bluesky tests ---

from worker.config import BlueskyConfig
from worker.platforms.bluesky import BlueskyClient, SEARCH_KEYWORDS as BSKY_SEARCH_KEYWORDS


def _make_bluesky_config() -> BlueskyConfig:
    return BlueskyConfig(
        handle="keyjawn.bsky.social",
        app_password="test-app-password",
    )


def test_bluesky_client_init():
    config = _make_bluesky_config()
    client = BlueskyClient(config)
    assert client.config == config
    assert client._client is None


def test_bluesky_post_too_long():
    client = BlueskyClient(_make_bluesky_config())
    long_text = "x" * 301
    with pytest.raises(ValueError, match="exceeds 300"):
        client.validate_post(long_text)


def test_bluesky_post_valid():
    client = BlueskyClient(_make_bluesky_config())
    client.validate_post("x" * 300)  # should not raise
    client.validate_post("hello")  # should not raise


def test_bluesky_search_terms():
    assert len(BSKY_SEARCH_KEYWORDS) > 0
    assert any("SSH" in kw or "CLI" in kw for kw in BSKY_SEARCH_KEYWORDS)


def test_bluesky_post_url():
    from worker.platforms.bluesky import _post_url
    url = _post_url("keyjawn.bsky.social", "at://did:plc:abc123/app.bsky.feed.post/xyz789")
    assert url == "https://bsky.app/profile/keyjawn.bsky.social/post/xyz789"


# --- Product Hunt tests ---

from worker.platforms.producthunt import (
    ProductHuntClient,
    MONITOR_TOPICS,
    RELEVANCE_KEYWORDS,
)


def test_producthunt_client_init():
    client = ProductHuntClient("test-token")
    assert client.token == "test-token"
    assert "Bearer test-token" in client._headers["Authorization"]


def test_producthunt_relevance_scoring():
    client = ProductHuntClient("test-token")

    # High relevance: multiple keyword hits
    post = {"name": "TermKey", "tagline": "A CLI keyboard for Android developers", "topics": []}
    score = client.score_relevance(post)
    assert score >= 0.7

    # Medium relevance: single keyword
    post = {"name": "DevApp", "tagline": "A new terminal for your Mac", "topics": []}
    score = client.score_relevance(post)
    assert score >= 0.4

    # Low relevance: no keywords
    post = {"name": "PhotoFilter", "tagline": "Beautiful photo filters for Instagram", "topics": []}
    score = client.score_relevance(post)
    assert score < 0.4


def test_producthunt_topic_boost():
    client = ProductHuntClient("test-token")

    # Keyword + relevant topic should boost score
    post = {
        "name": "MobileDev",
        "tagline": "Mobile coding environment",
        "topics": ["developer-tools"],
    }
    score = client.score_relevance(post)
    assert score >= 0.6  # keyword match + topic boost


def test_producthunt_constants():
    assert len(MONITOR_TOPICS) > 0
    assert "developer-tools" in MONITOR_TOPICS
    assert len(RELEVANCE_KEYWORDS) > 0
    assert "keyboard" in RELEVANCE_KEYWORDS
    assert "cli" in RELEVANCE_KEYWORDS
