/**
 * KeyJawn web PoC — WebSocket SSH relay
 *
 * Serves the static frontend and proxies WebSocket connections to SSH targets.
 * Credentials never flow through the browser — auth is handled server-side
 * using SSH agent or key files configured via hosts.json.
 */

const express = require('express');
const { WebSocketServer } = require('ws');
const { Client } = require('ssh2');
const http = require('http');
const path = require('path');
const fs = require('fs');

const PORT = parseInt(process.env.PORT || '5062', 10);
const MAX_CONNECTIONS = parseInt(process.env.MAX_CONNECTIONS || '10', 10);

// Load preset hosts from hosts.json or KEYJAWN_HOSTS env var
function loadHosts() {
  const hostsFile = path.join(__dirname, 'hosts.json');
  if (process.env.KEYJAWN_HOSTS) {
    try {
      return JSON.parse(process.env.KEYJAWN_HOSTS);
    } catch {
      console.error('Invalid KEYJAWN_HOSTS JSON');
    }
  }
  if (fs.existsSync(hostsFile)) {
    return JSON.parse(fs.readFileSync(hostsFile, 'utf8'));
  }
  // Default: connect to localhost as the current unix user via agent
  return [
    {
      id: 'local',
      label: 'localhost',
      host: '127.0.0.1',
      port: 22,
      username: process.env.USER || 'pi',
      auth: 'agent',
    },
  ];
}

const HOSTS = loadHosts();

const app = express();
app.use(express.static(path.join(__dirname, 'public')));

// Expose host list to the frontend (labels only, no credentials)
app.get('/api/hosts', (req, res) => {
  res.json(HOSTS.map(({ id, label }) => ({ id, label })));
});

const server = http.createServer(app);
const wss = new WebSocketServer({ server });

let connectionCount = 0;

wss.on('connection', (ws) => {
  if (connectionCount >= MAX_CONNECTIONS) {
    ws.close(1013, 'Too many connections');
    return;
  }
  connectionCount++;

  const ssh = new Client();
  let sshStream = null;

  const send = (obj) => {
    if (ws.readyState === ws.OPEN) ws.send(JSON.stringify(obj));
  };

  ws.on('message', (data, isBinary) => {
    if (isBinary && sshStream) {
      sshStream.write(data);
      return;
    }

    let msg;
    try {
      msg = JSON.parse(data);
    } catch {
      return;
    }

    if (msg.type === 'connect') {
      const host = HOSTS.find((h) => h.id === msg.hostId);
      if (!host) {
        send({ type: 'error', message: 'Unknown host' });
        ws.close();
        return;
      }

      const authConfig = {
        host: host.host,
        port: host.port || 22,
        username: host.username,
        readyTimeout: 15000,
        keepaliveInterval: 30000,
      };

      if (host.auth === 'agent') {
        authConfig.agent = process.env.SSH_AUTH_SOCK;
        if (!authConfig.agent) {
          send({ type: 'error', message: 'No SSH agent socket (SSH_AUTH_SOCK not set)' });
          ws.close();
          return;
        }
      } else if (host.auth === 'key') {
        try {
          authConfig.privateKey = fs.readFileSync(host.keyPath);
          if (host.passphrase) authConfig.passphrase = host.passphrase;
        } catch {
          send({ type: 'error', message: 'Cannot read key file' });
          ws.close();
          return;
        }
      } else if (host.auth === 'password') {
        authConfig.password = host.password;
      }

      ssh.connect(authConfig);

    } else if (msg.type === 'data' && sshStream) {
      sshStream.write(msg.data);

    } else if (msg.type === 'resize' && sshStream) {
      sshStream.setWindow(msg.rows, msg.cols, 0, 0);
    }
  });

  ssh.on('ready', () => {
    ssh.shell({ term: 'xterm-256color', cols: 80, rows: 24 }, (err, stream) => {
      if (err) {
        send({ type: 'error', message: err.message });
        ws.close();
        return;
      }

      sshStream = stream;
      send({ type: 'connected' });

      stream.on('data', (chunk) => {
        if (ws.readyState === ws.OPEN) ws.send(chunk); // raw bytes, xterm handles VT100
      });

      stream.stderr.on('data', (chunk) => {
        if (ws.readyState === ws.OPEN) ws.send(chunk);
      });

      stream.on('close', () => {
        send({ type: 'disconnected' });
        ws.close();
      });
    });
  });

  ssh.on('error', (err) => {
    send({ type: 'error', message: err.message });
    ws.close();
  });

  ws.on('close', () => {
    connectionCount = Math.max(0, connectionCount - 1);
    ssh.end();
  });

  ws.on('error', () => {
    connectionCount = Math.max(0, connectionCount - 1);
    ssh.end();
  });
});

server.listen(PORT, '127.0.0.1', () => {
  console.log(`KeyJawn web PoC listening on 127.0.0.1:${PORT}`);
  console.log(`Loaded ${HOSTS.length} host(s): ${HOSTS.map((h) => h.label).join(', ')}`);
});
