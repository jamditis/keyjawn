from worker.telegram import (
    format_escalation_message,
    build_approval_keyboard,
    parse_callback_data,
    _escape_html,
)


def test_format_escalation_message():
    msg = format_escalation_message(
        action_id="abc123",
        action_type="post",
        platform="twitter",
        context="some context here",
        draft="Hello world",
    )
    assert "<b>[KeyJawn worker] Action ready</b>" in msg
    assert "<b>Type:</b> post" in msg
    assert "<b>Platform:</b> twitter" in msg
    assert "<b>Context:</b> some context here" in msg
    assert "<pre>Hello world</pre>" in msg


def test_format_escalation_without_context():
    msg = format_escalation_message(
        action_id="abc123",
        action_type="reply",
        platform="bluesky",
        context=None,
        draft="A reply",
    )
    assert "Context:" not in msg
    assert "<b>Type:</b> reply" in msg
    assert "<b>Platform:</b> bluesky" in msg
    assert "<pre>A reply</pre>" in msg


def test_parse_callback_data():
    result = parse_callback_data("kw:approve:abc123")
    assert result == {"action": "approve", "action_id": "abc123"}


def test_parse_callback_data_rethink():
    result = parse_callback_data("kw:rethink:xyz789")
    assert result == {"action": "rethink", "action_id": "xyz789"}


def test_build_approval_keyboard():
    keyboard = build_approval_keyboard("test-id-42")
    assert len(keyboard) == 1
    row = keyboard[0]
    assert len(row) == 4
    assert row[0] == {"text": "Approve", "callback_data": "kw:approve:test-id-42"}
    assert row[1] == {"text": "Deny", "callback_data": "kw:deny:test-id-42"}
    assert row[2] == {"text": "Backlog", "callback_data": "kw:backlog:test-id-42"}
    assert row[3] == {"text": "Rethink", "callback_data": "kw:rethink:test-id-42"}


def test_escape_html():
    assert _escape_html("a & b") == "a &amp; b"
    assert _escape_html("<script>") == "&lt;script&gt;"
    assert _escape_html("no special chars") == "no special chars"
    assert _escape_html("A < B & C > D") == "A &lt; B &amp; C &gt; D"
