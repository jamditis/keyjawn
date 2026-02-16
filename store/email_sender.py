import os
import logging
import smtplib
from email.mime.multipart import MIMEMultipart
from email.mime.text import MIMEText

from r2 import generate_signed_url, GITHUB_RELEASES

log = logging.getLogger("keyjawn-store")

FROM_EMAIL = "jamditis@gmail.com"
FROM_NAME = "KeyJawn"
GMAIL_APP_PASSWORD = os.environ.get("GMAIL_APP_PASSWORD", "")

PHYSICAL_ADDRESS = "55 Park Ave, Bloomfield, NJ 07003"


def _get_latest_version() -> str:
    """Get the latest release version from the DB, or fall back to 'latest'."""
    try:
        from db import get_db
        conn = get_db()
        row = conn.execute(
            "SELECT version FROM releases ORDER BY released_at DESC LIMIT 1"
        ).fetchone()
        conn.close()
        if row:
            return row["version"]
    except Exception:
        pass
    return "latest"


def _apk_url(version: str) -> str:
    """Generate a presigned R2 download URL for the full APK."""
    r2_key = f"keyjawn/releases/v{version}/app-full-release.apk"
    url = generate_signed_url(r2_key, filename=f"keyjawn-v{version}.apk")
    if url:
        return url
    return f"{GITHUB_RELEASES}/tag/v{version}"


def _unsubscribe_url(email: str) -> str:
    """Generate an unsubscribe URL for the given email."""
    try:
        from routes.unsubscribe import make_unsubscribe_url
        return make_unsubscribe_url(email)
    except Exception:
        return "https://keyjawn-store.amditis.tech/unsubscribe"


def _canspam_footer(email: str) -> str:
    """CAN-SPAM compliant footer with physical address and unsubscribe link."""
    unsub_url = _unsubscribe_url(email)
    return f"""
        <tr><td style="padding:20px 32px; border-top:1px solid #eee;">
          <p style="margin:0 0 8px; font-size:12px; color:#999; line-height:1.5;">
            KeyJawn -- a keyboard for people who use the terminal from their phone.
          </p>
          <p style="margin:0 0 8px; font-size:11px; color:#bbb; line-height:1.4;">
            {PHYSICAL_ADDRESS}
          </p>
          <p style="margin:0; font-size:11px; color:#bbb; line-height:1.4;">
            <a href="{unsub_url}" style="color:#999; text-decoration:underline;">Unsubscribe</a>
            from update emails. You can still download your purchased version anytime.
          </p>
        </td></tr>"""


def _send_email(to: str, subject: str, html: str):
    msg = MIMEMultipart("alternative")
    msg["From"] = f"{FROM_NAME} <{FROM_EMAIL}>"
    msg["To"] = to
    msg["Subject"] = subject
    msg.attach(MIMEText(html, "html"))

    try:
        with smtplib.SMTP("smtp.gmail.com", 587) as smtp:
            smtp.starttls()
            smtp.login(FROM_EMAIL, GMAIL_APP_PASSWORD)
            smtp.sendmail(FROM_EMAIL, [to, FROM_EMAIL], msg.as_string())
        log.info(f"Email sent to {to}")
    except Exception as e:
        log.error(f"Failed to send email to {to}: {e}")


def _download_email_html(version: str, to_email: str) -> str:
    url = _apk_url(version)
    return f"""\
<!DOCTYPE html>
<html>
<head><meta charset="utf-8"></head>
<body style="margin:0; padding:0; background:#f4f4f7; font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
  <table width="100%" cellpadding="0" cellspacing="0" style="background:#f4f4f7; padding:32px 0;">
    <tr><td align="center">
      <table width="540" cellpadding="0" cellspacing="0" style="background:#ffffff; border-radius:8px; overflow:hidden;">

        <!-- header -->
        <tr><td style="background:#1B1B1F; padding:24px 32px;">
          <span style="color:#6cf2a8; font-size:22px; font-weight:700; letter-spacing:0.5px;">KeyJawn</span>
          <span style="color:#6E6E78; font-size:14px; float:right; line-height:30px;">full version</span>
        </td></tr>

        <!-- body -->
        <tr><td style="padding:32px;">
          <p style="margin:0 0 16px; font-size:16px; color:#1a1a1a; line-height:1.5;">
            Thanks for the purchase. Here's your download link:
          </p>

          <!-- download button -->
          <table width="100%" cellpadding="0" cellspacing="0" style="margin:24px 0;">
            <tr><td align="center">
              <a href="{url}"
                 style="display:inline-block; background:#6cf2a8; color:#0a0a0f; text-decoration:none;
                        font-size:16px; font-weight:600; padding:14px 32px; border-radius:6px;">
                Download KeyJawn v{version}
              </a>
            </td></tr>
          </table>

          <p style="margin:0 0 8px; font-size:14px; color:#555; line-height:1.5;">
            <strong>To install:</strong> Open the APK on your Android device, or use
            <code style="background:#f0f0f3; padding:2px 5px; border-radius:3px; font-size:13px;">adb install app-full-release.apk</code>
          </p>

          <p style="margin:16px 0 0; font-size:14px; color:#555; line-height:1.5;">
            This link expires in 7 days. When new versions are released,
            you'll get an email with a fresh download link.
          </p>
        </td></tr>

        {_canspam_footer(to_email)}

      </table>
    </td></tr>
  </table>
</body>
</html>"""


def _ticket_email_html(ticket_id: int, to_email: str) -> str:
    return f"""\
<!DOCTYPE html>
<html>
<head><meta charset="utf-8"></head>
<body style="margin:0; padding:0; background:#f4f4f7; font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
  <table width="100%" cellpadding="0" cellspacing="0" style="background:#f4f4f7; padding:32px 0;">
    <tr><td align="center">
      <table width="540" cellpadding="0" cellspacing="0" style="background:#ffffff; border-radius:8px; overflow:hidden;">

        <tr><td style="background:#1B1B1F; padding:24px 32px;">
          <span style="color:#6cf2a8; font-size:22px; font-weight:700; letter-spacing:0.5px;">KeyJawn</span>
          <span style="color:#6E6E78; font-size:14px; float:right; line-height:30px;">support</span>
        </td></tr>

        <tr><td style="padding:32px;">
          <p style="margin:0 0 16px; font-size:16px; color:#1a1a1a; line-height:1.5;">
            Got your support request (ticket #{ticket_id}). Looking into it.
          </p>
          <p style="margin:0; font-size:14px; color:#555; line-height:1.5;">
            Reply to this email with any additional details.
          </p>
        </td></tr>

        {_canspam_footer(to_email)}

      </table>
    </td></tr>
  </table>
</body>
</html>"""


def _update_email_html(version: str, changelog: str = "", to_email: str = "") -> str:
    url = _apk_url(version)
    changes_html = ""
    if changelog:
        items = [f"<li style='margin:0 0 6px; color:#333;'>{line.lstrip('- ').strip()}</li>"
                 for line in changelog.strip().splitlines() if line.strip()]
        if items:
            changes_html = f"""
          <div style="background:#f8f8fb; border-radius:6px; padding:16px 20px; margin:0 0 20px;">
            <p style="margin:0 0 8px; font-size:14px; font-weight:600; color:#1a1a1a;">What's new:</p>
            <ul style="margin:0; padding:0 0 0 20px; font-size:14px; line-height:1.6;">
              {''.join(items)}
            </ul>
          </div>"""

    return f"""\
<!DOCTYPE html>
<html>
<head><meta charset="utf-8"></head>
<body style="margin:0; padding:0; background:#f4f4f7; font-family:-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;">
  <table width="100%" cellpadding="0" cellspacing="0" style="background:#f4f4f7; padding:32px 0;">
    <tr><td align="center">
      <table width="540" cellpadding="0" cellspacing="0" style="background:#ffffff; border-radius:8px; overflow:hidden;">

        <!-- header -->
        <tr><td style="background:#1B1B1F; padding:24px 32px;">
          <span style="color:#6cf2a8; font-size:22px; font-weight:700; letter-spacing:0.5px;">KeyJawn</span>
          <span style="color:#6E6E78; font-size:14px; float:right; line-height:30px;">update</span>
        </td></tr>

        <!-- body -->
        <tr><td style="padding:32px;">
          <p style="margin:0 0 20px; font-size:16px; color:#1a1a1a; line-height:1.5;">
            KeyJawn v{version} is available. Here's your download:
          </p>

          {changes_html}

          <!-- download button -->
          <table width="100%" cellpadding="0" cellspacing="0" style="margin:24px 0;">
            <tr><td align="center">
              <a href="{url}"
                 style="display:inline-block; background:#6cf2a8; color:#0a0a0f; text-decoration:none;
                        font-size:16px; font-weight:600; padding:14px 32px; border-radius:6px;">
                Download KeyJawn v{version}
              </a>
            </td></tr>
          </table>

          <p style="margin:0 0 8px; font-size:14px; color:#555; line-height:1.5;">
            <strong>To install:</strong> Open the APK on your Android device. If prompted, allow
            installation from unknown sources.
          </p>

          <p style="margin:16px 0 0; font-size:14px; color:#555; line-height:1.5;">
            This link expires in 7 days. Reply to this email if you need a new one.
          </p>
        </td></tr>

        {_canspam_footer(to_email)}

      </table>
    </td></tr>
  </table>
</body>
</html>"""


def send_download_email(to_email: str):
    version = _get_latest_version()
    _send_email(
        to_email,
        f"KeyJawn v{version} -- your download link",
        _download_email_html(version, to_email),
    )


def send_update_email(to_email: str, version: str, changelog: str = ""):
    """Send an update notification to an existing purchaser."""
    _send_email(
        to_email,
        f"KeyJawn v{version} -- what's new",
        _update_email_html(version, changelog, to_email),
    )


def send_ticket_confirmation(to_email: str, subject: str, ticket_id: int):
    _send_email(
        to_email,
        f"Re: {subject} [#{ticket_id}]",
        _ticket_email_html(ticket_id, to_email),
    )
