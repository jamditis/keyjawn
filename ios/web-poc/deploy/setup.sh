#!/usr/bin/env bash
# KeyJawn web PoC — setup script for houseofjawn
# Run this ON houseofjawn: bash setup.sh
set -euo pipefail

APP_DIR="$HOME/keyjawn-web"
SERVICE="keyjawn-web"
PORT=5062

echo "==> Setting up KeyJawn web PoC on $(hostname)"

# ── 1. Check Node.js ───────────────────────────────────────────────────────────
if ! command -v node &>/dev/null; then
  echo "Node.js not found. Installing via apt..."
  sudo apt-get update -qq
  sudo apt-get install -y nodejs npm
fi
echo "Node $(node --version), npm $(npm --version)"

# ── 2. Copy app files ─────────────────────────────────────────────────────────
mkdir -p "$APP_DIR"
# Run this from the ios/web-poc/ directory on the MacBook:
#   rsync -av --exclude node_modules . 100.122.208.15:~/keyjawn-web/
# Or from houseofjawn, pull from git:
#   cd ~/keyjawn-web && git -C /path/to/keyjawn archive HEAD ios/web-poc | tar -x --strip-components=2

# ── 3. Install dependencies ────────────────────────────────────────────────────
cd "$APP_DIR"
npm install --production

# ── 4. Configure hosts ─────────────────────────────────────────────────────────
# Create hosts.json if it doesn't exist
if [ ! -f hosts.json ]; then
  cat > hosts.json << 'JSON'
[
  {
    "id":       "local",
    "label":    "houseofjawn",
    "host":     "127.0.0.1",
    "port":     22,
    "username": "pi",
    "auth":     "agent"
  },
  {
    "id":       "officejawn",
    "label":    "officejawn",
    "host":     "100.84.214.24",
    "port":     22,
    "username": "pi",
    "auth":     "agent"
  }
]
JSON
  echo "Created hosts.json — edit to add/remove hosts"
fi

# ── 5. Create systemd service ─────────────────────────────────────────────────
SERVICE_FILE="/etc/systemd/system/${SERVICE}.service"
sudo tee "$SERVICE_FILE" > /dev/null << EOF
[Unit]
Description=KeyJawn web PoC terminal
After=network.target

[Service]
Type=simple
User=$USER
WorkingDirectory=$APP_DIR
ExecStart=$(command -v node) $APP_DIR/server.js
Restart=on-failure
RestartSec=5
Environment=PORT=$PORT
# SSH agent socket — set this to your actual socket path
Environment=SSH_AUTH_SOCK=/run/user/$(id -u)/keyring/ssh

[Install]
WantedBy=multi-user.target
EOF

sudo systemctl daemon-reload
sudo systemctl enable "$SERVICE"
sudo systemctl restart "$SERVICE"
echo "Service $SERVICE started on port $PORT"

# ── 6. Cloudflare Tunnel ───────────────────────────────────────────────────────
echo ""
echo "==> Next: add a tunnel ingress rule for port $PORT"
echo "    Edit /etc/cloudflared/config.yml and add:"
echo ""
echo "    - hostname: terminal.keyjawn.amditis.tech"
echo "      service: http://127.0.0.1:$PORT"
echo ""
echo "    Then: sudo systemctl restart cloudflared"
echo ""
echo "Done. Relay is at http://127.0.0.1:$PORT"
echo "Once tunneled: https://terminal.keyjawn.amditis.tech"
