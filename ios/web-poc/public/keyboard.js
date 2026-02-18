/**
 * KeyJawn extra row — web PoC
 *
 * Provides the same extra row as the Android app:
 *   Esc | Tab | Clip | Ctrl | ◄ ▲ ▼ ► | / | Mic
 *
 * Works via two callbacks the terminal page provides:
 *   sendRaw(bytes[])  — raw byte array into the SSH stream
 *   sendText(string)  — text string into the SSH stream
 *   focus()           — return focus to the xterm.js terminal
 */

// ── Ctrl state machine ────────────────────────────────────────────────────────
// OFF → tap → ARMED (one-shot, auto-resets after next keypress)
// ARMED → tap → LOCKED (sticky until tapped again)
// LOCKED → tap → OFF
const CtrlState = Object.freeze({ OFF: 'OFF', ARMED: 'ARMED', LOCKED: 'LOCKED' });

// ── ANSI sequences ────────────────────────────────────────────────────────────
const ANSI = {
  esc:   [0x1b],
  tab:   [0x09],
  up:    [0x1b, 0x5b, 0x41],   // ESC [ A
  down:  [0x1b, 0x5b, 0x42],   // ESC [ B
  right: [0x1b, 0x5b, 0x43],   // ESC [ C
  left:  [0x1b, 0x5b, 0x44],   // ESC [ D
  // Ctrl+arrow
  ctrlUp:    [0x1b, 0x5b, 0x31, 0x3b, 0x35, 0x41],  // ESC [ 1 ; 5 A
  ctrlDown:  [0x1b, 0x5b, 0x31, 0x3b, 0x35, 0x42],
  ctrlRight: [0x1b, 0x5b, 0x31, 0x3b, 0x35, 0x43],
  ctrlLeft:  [0x1b, 0x5b, 0x31, 0x3b, 0x35, 0x44],
};

// Ctrl+key: ASCII char & 0x1f
function ctrlByte(char) {
  return char.charCodeAt(0) & 0x1f;
}

// ── Clipboard history (simple in-memory, 10 items) ────────────────────────────
const clipboardHistory = [];

function addToClipboard(text) {
  const existing = clipboardHistory.indexOf(text);
  if (existing !== -1) clipboardHistory.splice(existing, 1);
  clipboardHistory.unshift(text);
  if (clipboardHistory.length > 10) clipboardHistory.pop();
}

// ── Voice input (Web Speech API) ──────────────────────────────────────────────
function startVoice(onResult) {
  const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition;
  if (!SpeechRec) {
    alert('Voice input not available in this browser.');
    return;
  }
  const rec = new SpeechRec();
  rec.continuous = false;
  rec.interimResults = false;
  rec.lang = 'en-US';
  rec.onresult = (evt) => {
    const text = evt.results[0][0].transcript;
    onResult(text);
  };
  rec.onerror = (evt) => {
    if (evt.error !== 'aborted') console.warn('Voice error:', evt.error);
  };
  rec.start();
  return rec;
}

// ── Repeat-on-hold helper ─────────────────────────────────────────────────────
function makeRepeating(btn, onFire) {
  let timer = null;
  let interval = null;

  const start = () => {
    onFire();
    timer = setTimeout(() => {
      interval = setInterval(onFire, 80);
    }, 400);
  };

  const stop = () => {
    clearTimeout(timer);
    clearInterval(interval);
    timer = null;
    interval = null;
  };

  btn.addEventListener('pointerdown', (e) => { e.preventDefault(); start(); });
  btn.addEventListener('pointerup', stop);
  btn.addEventListener('pointerleave', stop);
  btn.addEventListener('pointercancel', stop);
}

// ── Mount extra row ───────────────────────────────────────────────────────────
function mountExtraRow(container, { sendRaw, sendText, focus }) {
  let ctrlState = CtrlState.OFF;
  let ctrlBtn = null;
  let voiceRec = null;
  let clipPanel = null;

  function updateCtrlVisual() {
    if (!ctrlBtn) return;
    ctrlBtn.dataset.state = ctrlState;
    ctrlBtn.setAttribute('aria-pressed', ctrlState !== CtrlState.OFF);
    ctrlBtn.title = { OFF: 'Ctrl (tap to arm)', ARMED: 'Ctrl armed — next key will use Ctrl', LOCKED: 'Ctrl locked (tap to release)' }[ctrlState];
  }

  // Apply Ctrl modifier to a raw byte sequence
  function applyCtrl(bytes) {
    if (ctrlState === CtrlState.OFF) return bytes;
    // For arrow keys, use Ctrl+Arrow sequences
    if (bytes === ANSI.up)    return ANSI.ctrlUp;
    if (bytes === ANSI.down)  return ANSI.ctrlDown;
    if (bytes === ANSI.left)  return ANSI.ctrlLeft;
    if (bytes === ANSI.right) return ANSI.ctrlRight;
    // For single-byte chars, apply & 0x1f
    if (bytes.length === 1) return [bytes[0] & 0x1f];
    return bytes;
  }

  function consumeCtrl() {
    if (ctrlState === CtrlState.ARMED) {
      ctrlState = CtrlState.OFF;
      updateCtrlVisual();
    }
  }

  function fireKey(bytes) {
    const out = applyCtrl(bytes);
    sendRaw(out);
    consumeCtrl();
    focus();
  }

  // ── Build buttons ───────────────────────────────────────────────────────────
  function makeBtn(label, cls, onClick) {
    const btn = document.createElement('button');
    btn.className = `extra-key ${cls || ''}`;
    btn.textContent = label;
    btn.type = 'button';
    btn.addEventListener('pointerdown', (e) => e.preventDefault()); // prevent focus steal
    if (onClick) btn.addEventListener('click', onClick);
    return btn;
  }

  // Esc
  const escBtn = makeBtn('Esc', 'key-esc', () => { fireKey(ANSI.esc); });

  // Tab
  const tabBtn = makeBtn('Tab', 'key-tab', () => { fireKey(ANSI.tab); });

  // Clipboard
  const clipBtn = makeBtn('Clip', 'key-clip', () => toggleClipPanel());

  // Ctrl
  ctrlBtn = makeBtn('Ctrl', 'key-ctrl');
  ctrlBtn.dataset.state = CtrlState.OFF;
  ctrlBtn.addEventListener('click', () => {
    if (ctrlState === CtrlState.OFF)    ctrlState = CtrlState.ARMED;
    else if (ctrlState === CtrlState.ARMED) ctrlState = CtrlState.LOCKED;
    else                                    ctrlState = CtrlState.OFF;
    updateCtrlVisual();
    focus();
  });

  // Arrows (with repeat)
  const leftBtn  = makeBtn('◄', 'key-arrow');
  const upBtn    = makeBtn('▲', 'key-arrow');
  const downBtn  = makeBtn('▼', 'key-arrow');
  const rightBtn = makeBtn('►', 'key-arrow');

  makeRepeating(leftBtn,  () => fireKey(ANSI.left));
  makeRepeating(upBtn,    () => fireKey(ANSI.up));
  makeRepeating(downBtn,  () => fireKey(ANSI.down));
  makeRepeating(rightBtn, () => fireKey(ANSI.right));

  // Slash / quick-key
  const slashBtn = makeBtn('/', 'key-slash', () => { sendText('/'); focus(); });

  // Mic
  const micBtn = makeBtn('Mic', 'key-mic', () => {
    if (voiceRec) {
      voiceRec.abort();
      voiceRec = null;
      micBtn.classList.remove('active');
      return;
    }
    micBtn.classList.add('active');
    voiceRec = startVoice((text) => {
      sendText(text);
      micBtn.classList.remove('active');
      voiceRec = null;
      focus();
    });
    if (!voiceRec) micBtn.classList.remove('active');
  });

  // Append all buttons
  [escBtn, tabBtn, clipBtn, ctrlBtn, leftBtn, upBtn, downBtn, rightBtn, slashBtn, micBtn]
    .forEach((btn) => container.appendChild(btn));

  // ── Clipboard panel ─────────────────────────────────────────────────────────
  function toggleClipPanel() {
    if (clipPanel) {
      clipPanel.remove();
      clipPanel = null;
      return;
    }

    // Read current clipboard
    navigator.clipboard.readText().then((text) => {
      if (text) addToClipboard(text);
    }).catch(() => {}).finally(() => {
      clipPanel = document.createElement('div');
      clipPanel.className = 'clip-panel';

      if (clipboardHistory.length === 0) {
        const empty = document.createElement('div');
        empty.className = 'clip-empty';
        empty.textContent = 'Clipboard history empty';
        clipPanel.appendChild(empty);
      } else {
        clipboardHistory.forEach((item) => {
          const row = document.createElement('button');
          row.className = 'clip-item';
          row.textContent = item.length > 60 ? item.slice(0, 60) + '…' : item;
          row.addEventListener('click', () => {
            sendText(item);
            clipPanel.remove();
            clipPanel = null;
            focus();
          });
          clipPanel.appendChild(row);
        });
      }

      document.getElementById('terminal-screen').appendChild(clipPanel);
    });
  }

  // Track clipboard writes from xterm (when user copies in the terminal)
  document.addEventListener('copy', () => {
    setTimeout(() => {
      navigator.clipboard.readText().then(addToClipboard).catch(() => {});
    }, 100);
  });

  updateCtrlVisual();
}
