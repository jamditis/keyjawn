"""Tests for the content generation module."""

from worker.content import (
    ContentRequest,
    PLATFORM_LIMITS,
    BANNED_WORDS,
    BANNED_OPENERS,
    build_generation_prompt,
    validate_generated_content,
    utm_url,
)


# --- build_generation_prompt ---


def test_build_prompt_original_post():
    req = ContentRequest(
        pillar="demo",
        platform="twitter",
        topic="voice input for terminal commands",
    )
    prompt = build_generation_prompt(req)

    assert "twitter" in prompt.lower()
    assert "280" in prompt
    assert "voice input for terminal commands" in prompt
    assert "demo" in prompt
    assert "KeyJawn" in prompt


def test_build_prompt_reply():
    req = ContentRequest(
        pillar="engagement",
        platform="bluesky",
        topic="responding to a thread about mobile dev tools",
        context="Someone said: 'I wish I had better terminal keys on my phone'",
    )
    prompt = build_generation_prompt(req)

    assert "reply" in prompt.lower()
    assert "I wish I had better terminal keys on my phone" in prompt
    assert "300" in prompt
    assert "bluesky" in prompt.lower()


# --- validate_generated_content ---


def test_validate_content_too_long():
    text = "a" * 281
    violations = validate_generated_content(text, "twitter")
    assert any("length" in v.lower() or "long" in v.lower() for v in violations)


def test_validate_content_has_banned_words():
    text = "This innovative keyboard seamlessly integrates with your workflow."
    violations = validate_generated_content(text, "twitter")
    assert len(violations) >= 2
    matched = " ".join(violations).lower()
    assert "innovative" in matched
    assert "seamlessly" in matched


def test_validate_content_has_hashtag_spam():
    text = "Check this out #android #keyboard #terminal"
    violations = validate_generated_content(text, "twitter")
    assert any("hashtag" in v.lower() for v in violations)


def test_validate_clean_content():
    text = "KeyJawn adds a permanent Esc/Tab/Ctrl row to your phone keyboard."
    violations = validate_generated_content(text, "twitter")
    assert violations == []


# --- edge cases ---


def test_validate_single_hashtag_is_ok():
    text = "Try KeyJawn for terminal keys on Android. #android"
    violations = validate_generated_content(text, "twitter")
    assert not any("hashtag" in v.lower() for v in violations)


def test_validate_exclamation_marks():
    text = "Wow! This is great! So good!"
    violations = validate_generated_content(text, "twitter")
    assert any("exclamation" in v.lower() for v in violations)


def test_validate_single_exclamation_is_ok():
    text = "KeyJawn ships with voice input!"
    violations = validate_generated_content(text, "twitter")
    assert not any("exclamation" in v.lower() for v in violations)


def test_validate_banned_opener():
    text = "Ever struggled with typing terminal commands on your phone?"
    violations = validate_generated_content(text, "twitter")
    assert any("opener" in v.lower() for v in violations)


def test_platform_limits_defined():
    assert PLATFORM_LIMITS["twitter"] == 280
    assert PLATFORM_LIMITS["bluesky"] == 300
    assert PLATFORM_LIMITS["reddit"] == 10000
    assert PLATFORM_LIMITS["hn"] == 10000
    assert PLATFORM_LIMITS["devto"] == 50000


def test_banned_words_list():
    assert "comprehensive" in BANNED_WORDS
    assert "excited to announce" in BANNED_WORDS


def test_banned_openers_list():
    assert "ever struggled" in BANNED_OPENERS
    assert "don't miss" in BANNED_OPENERS


# --- utm_url ---


def test_utm_url_twitter():
    url = utm_url("twitter")
    assert "utm_source=twitter" in url
    assert "utm_medium=social" in url
    assert "utm_campaign=worker" in url
    assert url.startswith("keyjawn.amditis.tech?")


def test_utm_url_custom_campaign():
    url = utm_url("bluesky", campaign="launch")
    assert "utm_source=bluesky" in url
    assert "utm_campaign=launch" in url


def test_build_prompt_includes_utm_url():
    req = ContentRequest(
        pillar="demo",
        platform="twitter",
        topic="voice input demo",
    )
    prompt = build_generation_prompt(req)
    assert "utm_source=twitter" in prompt
    assert "utm_medium=social" in prompt
