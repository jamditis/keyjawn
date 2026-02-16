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

exec /home/jamditis/projects/keyjawn/store/venv/bin/gunicorn \
    -b 127.0.0.1:5060 -w 2 -k uvicorn.workers.UvicornWorker app:app
