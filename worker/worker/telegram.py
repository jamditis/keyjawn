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


def parse_callback_data(data: str) -> dict:
    """Parse a 'kw:action:id' callback string into its parts."""
    _, action, action_id = data.split(":", 2)
    return {"action": action, "action_id": action_id}
