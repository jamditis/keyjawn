#!/bin/bash
# Pull secrets from pass and start the store service.
# Used by systemd instead of EnvironmentFile.

PASS="/usr/bin/pass"

export STRIPE_API_KEY="$($PASS show claude/services/keyjawn-stripe-api-key)"
export STRIPE_WEBHOOK_SECRET="$($PASS show claude/services/keyjawn-stripe-webhook-secret)"
export ADMIN_TOKEN="$($PASS show claude/services/keyjawn-admin-token)"
export GMAIL_APP_PASSWORD="$($PASS show claude/services/gmail-keyjawn | tr -d ' ')"
export TELEGRAM_BOT_TOKEN="$($PASS show claude/tokens/telegram-bot)"
export TELEGRAM_CHAT_ID="743339387"
export R2_ACCESS_KEY_ID="$($PASS show claude/api/cloudflare-r2-access-key-id)"
export R2_SECRET_ACCESS_KEY="$($PASS show claude/api/cloudflare-r2-secret-access-key)"
export R2_ACCOUNT_ID="3d4b1d36109e30866bb7516502224b2c"

exec /home/jamditis/projects/keyjawn/store/venv/bin/gunicorn \
    -b 127.0.0.1:5060 -w 2 -k uvicorn.workers.UvicornWorker app:app
