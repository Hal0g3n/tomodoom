#!/usr/bin/env python3
"""Render the pet add-ons (thumb_<itemId>.png) and pet portraits (thumb_pet_<petId>.png) with headless Edge.

Serves the repo on localhost, opens thumbnails.html (which draws each add-on from pet-scene.js) and
reads the PNGs back from the page's DOM. Re-run it whenever an add-on, a pet or its PETS profile changes.

Usage: python3 tools/maniengine/make_thumbnails.py      (set EDGE to use another Chromium binary)
"""
import base64
import functools
import html
import http.server
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import tempfile
import threading

REPO = pathlib.Path(__file__).resolve().parents[2]
OUT = REPO / "app" / "src" / "main" / "res" / "drawable-nodpi"
PAGE = "tools/maniengine/thumbnails.html"
BROWSERS = ("/mnt/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe", "msedge",
            "microsoft-edge", "chromium", "google-chrome")


class QuietHandler(http.server.SimpleHTTPRequestHandler):
    def log_message(self, *args):
        pass


def find_browser():
    for name in filter(None, (os.environ.get("EDGE"), *BROWSERS)):
        path = shutil.which(name) or (name if os.path.exists(name) else None)
        if path:
            return path
    sys.exit("make_thumbnails: no Edge/Chromium found; set EDGE to its path")


def native_path(browser, path):
    """A Windows browser started from WSL needs a Windows path."""
    if browser.endswith(".exe") and shutil.which("wslpath"):
        return subprocess.check_output(["wslpath", "-w", str(path)], text=True).strip()
    return str(path)


def render(browser, url):
    with tempfile.TemporaryDirectory(dir=REPO / "app" / "build", ignore_cleanup_errors=True) as profile:
        return subprocess.run(
            [browser, "--headless=new", "--use-angle=swiftshader", "--enable-unsafe-swiftshader",
             "--ignore-gpu-blocklist", "--no-first-run", "--virtual-time-budget=120000",
             f"--user-data-dir={native_path(browser, profile)}", "--dump-dom", url],
            capture_output=True, text=True, encoding="utf-8", timeout=300).stdout


def main():
    handler = functools.partial(QuietHandler, directory=str(REPO))
    server = http.server.ThreadingHTTPServer(("127.0.0.1", 0), handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    dom = render(find_browser(), f"http://localhost:{server.server_address[1]}/{PAGE}")
    server.shutdown()

    found = re.search(r'<pre id="out">(.*?)</pre>', dom, re.S)
    text = html.unescape(found[1]) if found else ""
    if not text.startswith("{"):
        sys.exit(f"make_thumbnails: page produced no thumbnails: {text[:300] or dom[:300]!r}")
    OUT.mkdir(parents=True, exist_ok=True)
    for item_id, url in json.loads(text).items():
        png = base64.b64decode(url.split(",", 1)[1])
        (OUT / f"thumb_{item_id}.png").write_bytes(png)
        print(f"thumb_{item_id}.png  {len(png)} bytes")


if __name__ == "__main__":
    main()
