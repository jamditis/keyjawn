"""Telegram message formatting and approval button protocol.

Produces plain data structures -- no dependency on the telegram SDK.
The caller converts these to actual Telegram API objects.
"""


def _escape_html(text: str) -> str:
    """Escape &, <, > for Telegram HTML parse mode."""
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")


def format_escalation_message(
    action_id: str,
    action_type: str,
    platform: str,
    context: str | None,
    draft: str,
) -> str:
    """Format an HTML message for the Telegram approval flow."""
    lines = [
        "<b>[KeyJawn worker] Action ready</b>",
        "",
        f"<b>Type:</b> {action_type}",
        f"<b>Platform:</b> {platform}",
    ]
    if context is not None:
        lines.append(f"<b>Context:</b> {_escape_html(context)}")
    lines.append("")
    lines.append("<b>Draft:</b>")
    lines.append(f"<pre>{_escape_html(draft)}</pre>")
    return "\n".join(lines)


def build_approval_keyboard(action_id: str) -> list[list[dict]]:
    """Return inline keyboard button rows for the approval prompt."""
    return [
        [
            {"text": "Approve", "callback_data": f"kw:approve:{action_id}"},
            {"text": "Deny", "callback_data": f"kw:deny:{action_id}"},
            {"text": "Backlog", "callback_data": f"kw:backlog:{action_id}"},
            {"text": "Rethink", "callback_data": f"kw:rethink:{action_id}"},
        ]
    ]


def format_curation_message(
    action_id: str,
    source: str,
    title: str,
    score: float,
    reasoning: str,
    draft: str,
    platform: str,
) -> str:
    """Format a curation share approval message."""
    lines = [
        "<b>[CURATE] Share recommendation</b>",
        "",
        f"<b>Source:</b> {_escape_html(source)}",
        f"<b>Title:</b> {_escape_html(title)}",
        f"<b>Score:</b> {score:.2f}",
        "",
        f"<b>AI reasoning:</b> {_escape_html(reasoning)}",
        "",
        f"<b>Draft post ({platform}):</b>",
        f"<pre>{_escape_html(draft)}</pre>",
    ]
    return "\n".join(lines)


def format_batch_curation_message(
    action_id: str,
    source: str,
    author: str,
    title: str,
    score: float,
    reasoning: str,
    drafts: dict[str, str],
    platform: str,
) -> str:
    """Format a curation share with multiple draft variants for A/B/C/D selection."""
    lines = [
        f"<b>[CURATE] {_escape_html(source)} -- {_escape_html(author)}</b>",
        f"{_escape_html(title)} (score: {score:.2f})",
        "",
    ]

    for label in sorted(drafts.keys()):
        lines.append(f"<b>{label}:</b> {_escape_html(drafts[label])}")
        lines.append("")

    if reasoning:
        lines.append(f"<i>{_escape_html(reasoning)}</i>")

    return "\n".join(lines)


def build_batch_approval_keyboard(
    action_id: str, draft_labels: list[str]
) -> list[list[dict]]:
    """Build inline keyboard with A/B/C/D draft selection + Deny/Backlog/Rethink."""
    draft_row = [
        {"text": label, "callback_data": f"kw:draft_{label}:{action_id}"}
        for label in draft_labels
    ]
    control_row = [
        {"text": "Deny", "callback_data": f"kw:deny:{action_id}"},
        {"text": "Backlog", "callback_data": f"kw:backlog:{action_id}"},
        {"text": "Rethink", "callback_data": f"kw:rethink:{action_id}"},
    ]
    return [draft_row, control_row]


def parse_callback_data(data: str) -> dict:
    """Parse a 'kw:action:id' callback string into its parts."""
    _, action, action_id = data.split(":", 2)
    return {"action": action, "action_id": action_id}
