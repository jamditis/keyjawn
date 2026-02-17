#!/usr/bin/env python3
"""Generate Play Store assets using Selenium + Chromium snap.

Renders HTML templates to PNG at Play Store required dimensions:
- 512x512 app icon
- 1024x500 feature graphic
- 1440x2880 phone screenshots (keyboard states in device frames)

Output goes to app/src/lite/play/listings/en-US/graphics/
"""

import os
import sys
import tempfile
import time
from pathlib import Path

try:
    from selenium import webdriver
    from selenium.webdriver.chrome.options import Options
    from selenium.webdriver.chrome.service import Service
except ImportError:
    print("selenium not installed. Run: pip3 install --break-system-packages selenium")
    sys.exit(1)

PROJECT_ROOT = Path(__file__).resolve().parent.parent
GRAPHICS_DIR = PROJECT_ROOT / "app/src/lite/play/listings/en-US/graphics"
ICON_DIR = GRAPHICS_DIR / "icon"
SCREENSHOT_DIR = GRAPHICS_DIR / "phone-screenshots"

FAVICON_SVG = (PROJECT_ROOT / "website/public/favicon.svg").read_text()

# KeyJawn color palette (from OG image / website)
BG_DARK = "#0a0a0f"
BG_CARD = "#14141c"
ACCENT = "#6cf2a8"
KEY_BG = "#2B2B30"
KEY_BORDER = "#3a3a4a"
TEXT_DIM = "#8888aa"


def get_driver():
    options = Options()
    options.binary_location = "/snap/chromium/current/usr/lib/chromium-browser/chrome"
    options.add_argument("--headless=new")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-dev-shm-usage")
    options.add_argument("--disable-gpu")
    service = Service(
        executable_path="/snap/chromium/current/usr/lib/chromium-browser/chromedriver"
    )
    return webdriver.Chrome(service=service, options=options)


def render_html_to_png(driver, html, width, height, output_path):
    """Write HTML to a temp file, load in browser, screenshot at exact size."""
    with tempfile.NamedTemporaryFile(suffix=".html", delete=False, mode="w") as f:
        f.write(html)
        tmp_path = f.name

    try:
        # Use CDP to set exact viewport — set_window_size has chrome offsets
        driver.execute_cdp_cmd(
            "Emulation.setDeviceMetricsOverride",
            {
                "width": width,
                "height": height,
                "deviceScaleFactor": 1,
                "mobile": False,
            },
        )
        driver.get(f"file://{tmp_path}")
        time.sleep(0.5)
        driver.save_screenshot(str(output_path))
        print(f"  saved: {output_path} ({width}x{height})")
    finally:
        os.unlink(tmp_path)


def generate_icon(driver):
    """512x512 app icon: keyboard SVG on dark background."""
    html = f"""<!DOCTYPE html>
<html><head><style>
  * {{ margin: 0; padding: 0; }}
  body {{
    width: 512px; height: 512px;
    background: {BG_DARK};
    display: flex; align-items: center; justify-content: center;
    border-radius: 108px;
    overflow: hidden;
  }}
  .icon-wrapper {{
    width: 360px; height: 360px;
    display: flex; align-items: center; justify-content: center;
  }}
  .icon-wrapper svg {{ width: 100%; height: 100%; }}
</style></head>
<body>
  <div class="icon-wrapper">{FAVICON_SVG}</div>
</body></html>"""

    output = ICON_DIR / "icon.png"
    render_html_to_png(driver, html, 512, 512, output)


def generate_feature_graphic(driver):
    """1024x500 feature graphic: keyboard icon + tagline + key row."""
    # Build a row of key caps
    keys = ["Esc", "Tab", "Ctrl", "^", "v", "<", ">"]
    key_html = ""
    for k in keys:
        key_html += f"""<div style="
            background: {KEY_BG}; border: 1px solid {KEY_BORDER};
            border-radius: 6px; padding: 8px 14px;
            font-family: monospace; font-size: 18px; color: white;
            min-width: 36px; text-align: center;
        ">{k}</div>"""

    html = f"""<!DOCTYPE html>
<html><head><style>
  * {{ margin: 0; padding: 0; box-sizing: border-box; }}
  body {{
    width: 1024px; height: 500px;
    background: {BG_DARK};
    display: flex; align-items: center; justify-content: center;
    font-family: -apple-system, sans-serif;
    overflow: hidden;
  }}
  .content {{
    display: flex; align-items: center; gap: 60px;
    padding: 0 60px;
  }}
  .icon {{ width: 180px; height: 180px; flex-shrink: 0; }}
  .icon svg {{ width: 100%; height: 100%; }}
  .text {{ display: flex; flex-direction: column; gap: 16px; }}
  .title {{
    font-size: 48px; font-weight: 700; color: white;
    letter-spacing: -1px;
  }}
  .title span {{ color: {ACCENT}; }}
  .tagline {{
    font-size: 20px; color: {TEXT_DIM};
    line-height: 1.4;
  }}
  .keys {{
    display: flex; gap: 8px; margin-top: 8px;
  }}
</style></head>
<body>
  <div class="content">
    <div class="icon">{FAVICON_SVG}</div>
    <div class="text">
      <div class="title">Key<span>Jawn</span></div>
      <div class="tagline">Terminal keyboard for LLM agents</div>
      <div class="keys">{key_html}</div>
    </div>
  </div>
</body></html>"""

    output = GRAPHICS_DIR / "feature-graphic.png"
    render_html_to_png(driver, html, 1024, 500, output)


def generate_screenshot_keyboard(driver, title, subtitle, layer_keys, filename):
    """Phone screenshot (1440x2880) showing keyboard in a device frame."""
    # Build keyboard rows from layer_keys (list of lists of key labels)
    rows_html = ""
    for row in layer_keys:
        keys_in_row = ""
        for key in row:
            width = "flex: 1"
            if key in ("shift", "backspace", "delete"):
                width = "flex: 1.5"
            elif key == "space":
                width = "flex: 4"
            elif key in ("return", "enter"):
                width = "flex: 2"

            display_key = key
            bg = KEY_BG
            border = KEY_BORDER
            color = "white"
            if key in ("Esc", "Ctrl"):
                bg = "#4A2525"
                border = "#6a3535"
            elif key in ("Tab",):
                bg = "#253050"
                border = "#354a70"
            elif key.startswith("^") or key in ("v", "<", ">"):
                bg = "#2a4a35"
                border = "#3a6a4a"
                color = ACCENT

            keys_in_row += f"""<div style="
                {width}; background: {bg}; border: 2px solid {border};
                border-radius: 8px; padding: 16px 6px;
                font-family: monospace; font-size: 28px; color: {color};
                text-align: center; min-height: 56px;
                display: flex; align-items: center; justify-content: center;
            ">{display_key}</div>"""

        rows_html += f'<div style="display: flex; gap: 6px; width: 100%;">{keys_in_row}</div>'

    html = f"""<!DOCTYPE html>
<html><head><style>
  * {{ margin: 0; padding: 0; box-sizing: border-box; }}
  body {{
    width: 1440px; height: 2880px;
    background: #1a1a2e;
    display: flex; flex-direction: column;
    font-family: -apple-system, sans-serif;
    overflow: hidden;
  }}
  .status-bar {{
    height: 80px; background: #111;
    display: flex; align-items: center;
    padding: 0 40px; color: {TEXT_DIM}; font-size: 24px;
  }}
  .app-area {{
    flex: 1; background: #111118;
    display: flex; flex-direction: column;
    justify-content: center; align-items: center;
    padding: 60px;
  }}
  .app-title {{
    font-size: 52px; color: white; font-weight: 600;
    margin-bottom: 16px;
  }}
  .app-subtitle {{
    font-size: 28px; color: {TEXT_DIM};
    margin-bottom: 60px;
    text-align: center; line-height: 1.5;
  }}
  .terminal-box {{
    background: {BG_DARK}; border: 2px solid #333;
    border-radius: 16px; padding: 40px;
    width: 100%; max-width: 1200px;
    font-family: monospace; font-size: 26px; color: {ACCENT};
    line-height: 2;
  }}
  .prompt {{ color: {ACCENT}; }}
  .cursor {{ background: {ACCENT}; color: {BG_DARK}; padding: 0 2px; }}
  .keyboard {{
    background: {BG_CARD};
    padding: 20px 16px 40px;
    display: flex; flex-direction: column; gap: 8px;
  }}
</style></head>
<body>
  <div class="status-bar">
    <span style="flex:1">9:41</span>
    <span>KeyJawn</span>
  </div>
  <div class="app-area">
    <div class="app-title">{title}</div>
    <div class="app-subtitle">{subtitle}</div>
    <div class="terminal-box">
      <div><span class="prompt">$</span> claude -p "write a haiku"</div>
      <div style="color: white; margin-top: 16px;">Code flows like water</div>
      <div style="color: white;">Terminal keys at your side</div>
      <div style="color: white;">KeyJawn sets you free<span class="cursor"> </span></div>
    </div>
  </div>
  <div class="keyboard">
    {rows_html}
  </div>
</body></html>"""

    output = SCREENSHOT_DIR / filename
    render_html_to_png(driver, html, 1440, 2880, output)


def main():
    ICON_DIR.mkdir(parents=True, exist_ok=True)
    SCREENSHOT_DIR.mkdir(parents=True, exist_ok=True)

    print("Starting Chromium...")
    driver = get_driver()

    try:
        print("\nGenerating icon (512x512)...")
        generate_icon(driver)

        print("\nGenerating feature graphic (1024x500)...")
        generate_feature_graphic(driver)

        print("\nGenerating screenshots (1440x2880)...")

        # Screenshot 1: main keyboard with terminal row
        generate_screenshot_keyboard(
            driver,
            title="Terminal keys, always ready",
            subtitle="Esc, Tab, Ctrl, and arrow keys\nin a dedicated row above QWERTY",
            layer_keys=[
                ["Esc", "Tab", "Ctrl", "^", "v", "<", ">"],
                ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"],
                ["q", "w", "e", "r", "t", "y", "u", "i", "o", "p"],
                ["a", "s", "d", "f", "g", "h", "j", "k", "l"],
                ["shift", "z", "x", "c", "v", "b", "n", "m", "backspace"],
                ["?123", ",", "space", ".", "enter"],
            ],
            filename="01-terminal-keys.png",
        )

        # Screenshot 2: symbols layer with slash commands
        generate_screenshot_keyboard(
            driver,
            title="Symbols and slash commands",
            subtitle="Full symbol access plus quick-insert\nfor common CLI commands",
            layer_keys=[
                ["Esc", "Tab", "Ctrl", "^", "v", "<", ">"],
                ["1", "2", "3", "4", "5", "6", "7", "8", "9", "0"],
                ["!", "@", "#", "$", "%", "^", "&", "*", "(", ")"],
                ["-", "=", "[", "]", "\\", ";", "'", "`"],
                ["shift", "~", "+", "{", "}", "|", ":", '"', "delete"],
                ["abc", "/", "space", ".", "enter"],
            ],
            filename="02-symbols-layer.png",
        )

        print("\nAll assets generated.")
    finally:
        driver.quit()


if __name__ == "__main__":
    main()
