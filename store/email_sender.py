import os
import logging

log = logging.getLogger("keyjawn-store")
FROM_EMAIL = "KeyJawn <support@keyjawn.amditis.tech>"


def send_download_email(to_email: str):
    try:
        import resend
        resend.api_key = os.environ.get("RESEND_API_KEY", "")
        resend.Emails.send({
            "from": FROM_EMAIL,
            "to": [to_email],
            "bcc": ["jamditis@gmail.com"],
            "subject": "KeyJawn full version -- your download link",
            "html": f"""
                <p>Thanks for buying KeyJawn.</p>
                <p>Download the latest full version here:<br>
                <a href="https://keyjawn.amditis.tech/download">keyjawn.amditis.tech/download</a></p>
                <p>Enter <strong>{to_email}</strong> on the download page to get the APK.</p>
                <p>For updates, just visit the same page -- it always has the latest version.</p>
                <p>-- KeyJawn</p>
            """
        })
    except Exception as e:
        log.error(f"Failed to send download email to {to_email}: {e}")


def send_ticket_confirmation(to_email: str, subject: str, ticket_id: int):
    try:
        import resend
        resend.api_key = os.environ.get("RESEND_API_KEY", "")
        resend.Emails.send({
            "from": FROM_EMAIL,
            "to": [to_email],
            "bcc": ["jamditis@gmail.com"],
            "subject": f"Re: {subject} [#{ticket_id}]",
            "html": f"""
                <p>Got your support request (ticket #{ticket_id}). Looking into it.</p>
                <p>-- KeyJawn support</p>
            """
        })
    except Exception as e:
        log.error(f"Failed to send ticket confirmation to {to_email}: {e}")
