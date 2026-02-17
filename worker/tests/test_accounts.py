"""Tests for the curated account list module."""

from worker.accounts import CURATED_ACCOUNTS, get_accounts_for_platform, get_all_handles


def test_curated_accounts_has_twitter():
    twitter_accounts = get_accounts_for_platform("twitter")
    assert len(twitter_accounts) > 0
    assert all("handle" in a for a in twitter_accounts)


def test_curated_accounts_has_bluesky():
    bsky_accounts = get_accounts_for_platform("bluesky")
    assert len(bsky_accounts) > 0


def test_curated_accounts_unknown_platform():
    assert get_accounts_for_platform("myspace") == []


def test_curated_accounts_have_tags():
    for platform, accounts in CURATED_ACCOUNTS.items():
        for a in accounts:
            assert "handle" in a
            assert "tags" in a
            assert isinstance(a["tags"], list)


def test_get_all_handles():
    handles = get_all_handles("twitter")
    assert isinstance(handles, list)
    assert all(isinstance(h, str) for h in handles)
    assert len(handles) == len(get_accounts_for_platform("twitter"))


def test_get_all_handles_empty():
    assert get_all_handles("nonexistent") == []
