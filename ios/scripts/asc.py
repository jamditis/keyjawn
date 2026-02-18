#!/usr/bin/env python3
"""
App Store Connect API helper.
Pulls credentials from pass on houseofjawn and provides a thin wrapper
around the ASC REST API for creating apps, listing builds, etc.

Usage:
    python3 asc.py create-app
    python3 asc.py list-apps
    python3 asc.py list-builds <bundle-id>
"""

import sys
import time
import subprocess
import requests
import jwt  # PyJWT + cryptography


# ── Credentials ───────────────────────────────────────────────────────────────

HOUSEOFJAWN = "100.122.208.15"

def _pass(path: str) -> str:
    result = subprocess.run(
        ["ssh", HOUSEOFJAWN, f"pass show {path}"],
        capture_output=True, text=True, check=True,
    )
    return result.stdout.strip()

def _load_creds():
    key_id    = _pass("claude/services/appstore-connect-key-id")
    issuer_id = _pass("claude/services/appstore-connect-issuer-id")
    key_pem   = _pass("claude/services/appstore-connect-api-key")
    return key_id, issuer_id, key_pem

def _token(key_id: str, issuer_id: str, key_pem: str) -> str:
    now = int(time.time())
    payload = {
        "iss": issuer_id,
        "iat": now,
        "exp": now + 1140,  # 19 minutes
        "aud": "appstoreconnect-v1",
    }
    return jwt.encode(payload, key_pem, algorithm="ES256",
                      headers={"kid": key_id})

def _session() -> tuple[requests.Session, str]:
    key_id, issuer_id, key_pem = _load_creds()
    token = _token(key_id, issuer_id, key_pem)
    s = requests.Session()
    s.headers.update({
        "Authorization": f"Bearer {token}",
        "Content-Type": "application/json",
    })
    return s, token

BASE = "https://api.appstoreconnect.apple.com/v1"


# ── Commands ──────────────────────────────────────────────────────────────────

def list_apps():
    s, _ = _session()
    r = s.get(f"{BASE}/apps")
    r.raise_for_status()
    apps = r.json().get("data", [])
    if not apps:
        print("No apps found in App Store Connect.")
        return
    for app in apps:
        attrs = app["attributes"]
        print(f"  {attrs['bundleId']}  {attrs['name']}  (id: {app['id']})")


def create_app():
    s, _ = _session()

    payload = {
        "data": {
            "type": "apps",
            "attributes": {
                "bundleId":       "com.keyjawn",
                "name":           "KeyJawn",
                "primaryLocale":  "en-US",
                "sku":            "keyjawn-ios",
            },
        }
    }

    r = s.post(f"{BASE}/apps", json=payload)
    if r.status_code == 409:
        print("App already exists — no action needed.")
        return
    r.raise_for_status()
    data = r.json()["data"]
    print(f"Created app: {data['attributes']['name']}  id={data['id']}")


def list_builds(bundle_id: str):
    s, _ = _session()
    r = s.get(f"{BASE}/builds", params={
        "filter[app.bundleId]": bundle_id,
        "sort": "-uploadedDate",
        "limit": 10,
    })
    r.raise_for_status()
    builds = r.json().get("data", [])
    if not builds:
        print(f"No builds found for {bundle_id}.")
        return
    for b in builds:
        attrs = b["attributes"]
        print(f"  v{attrs['version']}  {attrs['processingState']}  {attrs.get('uploadedDate','')[:10]}")


# ── Entry point ───────────────────────────────────────────────────────────────

COMMANDS = {
    "list-apps":    lambda _: list_apps(),
    "create-app":   lambda _: create_app(),
    "list-builds":  lambda args: list_builds(args[0] if args else "com.keyjawn"),
}

if __name__ == "__main__":
    cmd  = sys.argv[1] if len(sys.argv) > 1 else "list-apps"
    args = sys.argv[2:]
    if cmd not in COMMANDS:
        print(f"Unknown command: {cmd}. Available: {', '.join(COMMANDS)}")
        sys.exit(1)
    COMMANDS[cmd](args)
