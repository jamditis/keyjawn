import hmac
import os
from collections.abc import Mapping


def load_admin_token(environment: Mapping[str, str] = os.environ) -> str:
    """Load the required admin token or stop service startup."""
    token = environment.get("ADMIN_TOKEN", "")
    if not token:
        raise RuntimeError("ADMIN_TOKEN env var must be set before starting the store service")
    return token


ADMIN_TOKEN = load_admin_token()


def admin_token_matches(candidate: str) -> bool:
    """Compare a submitted admin token without early-exit timing differences."""
    return bool(candidate) and hmac.compare_digest(
        candidate.encode("utf-8"), ADMIN_TOKEN.encode("utf-8")
    )
