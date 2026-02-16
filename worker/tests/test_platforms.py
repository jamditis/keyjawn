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
