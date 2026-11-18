# Tomodoom relay backend

A small Flask app that sits between the Android app and **Kafka running on the TruFar website**
(trufar.cn has no public API):

```
Android app ──/v1/chat/sessions──► relay (this folder) ──web-chat endpoint──► Kafka on trufar.cn
     ▲                                  ▲                                          │
     └──── quiz_evaluated (+HP) ────────┴──────────── POST /mcp award_hp ◄─────────┘
```

- The app keeps its existing client (`trufar/HttpTruFarChatService`); set `TRUFAR_API_URL` to
  this server and it's used instead of the echo bot.
- Every message is relayed into **one** TruFar conversation using your browser login cookie.
- When Kafka grades an answer correct, it calls the `award_hp` MCP tool on this server (or, as a
  fallback, ends its reply with `[[CORRECT]]`). The app receives a `quiz_evaluated` event and
  applies the HP itself, so the replay guard and caps stay on the device.

Design details and the captured TruFar protocol are in `CLAUDE.md` §5.2 ("Relay backend").

## Files

| File | What it does |
|---|---|
| `app.py` | Flask app + WSGI entry (`app`); app-facing routes and `/mcp` |
| `trufar_client.py` | posts to trufar.cn and parses its `data:` snapshot stream |
| `mcp_server.py` | minimal stateless MCP server (JSON-RPC over HTTP POST) with `award_hp` |
| `store.py` | sessions + the "turn in flight" lease (Firestore, or memory for tests) |
| `markers.py` | `[[CORRECT]]`/`[[WRONG]]` fallback (port of the app's `QuizMarkerParser`) |
| `config.py` | environment variables |

## Configuration (environment variables)

| Variable | Value |
|---|---|
| `BACKEND_TOKEN` | long random string; also set as the app's `TRUFAR_API_TOKEN` Gradle property |
| `MCP_KEY` | another long random string; given to TruFar when you register the MCP server |
| `TRUFAR_CONVERSATION_ID` | the number at the end of `/api/mashang/core/answer/<id>` in DevTools (e.g. `13549`) |
| `TRUFAR_COOKIE` | the whole `cookie` request header from that request (DevTools → Headers) |
| `TRUFAR_BASE_URL` | optional, default `https://trufar.cn` |
| `GOOGLE_APPLICATION_CREDENTIALS` | path to the Firebase service-account JSON (turns on Firestore) |
| `TOMODOOM_STORE` | optional: `firestore` or `memory` (default: firestore if the credentials are set) |

Make random secrets with `python -c "import secrets; print(secrets.token_urlsafe(32))"`.
**Never commit** the cookie, tokens or service-account file. The cookie is your TruFar login:
anyone who has it can use your account.

`GET /` reports `{"ok": true}` once everything required is set, or lists what's missing.

## Run locally

```bash
cd backend
python -m venv .venv && .venv/Scripts/activate      # macOS/Linux: source .venv/bin/activate
pip install -r requirements-dev.txt
python -m pytest tests                              # 62 tests, no network
# Local server with an in-memory store:
BACKEND_TOKEN=dev MCP_KEY=dev TRUFAR_CONVERSATION_ID=13549 TRUFAR_COOKIE='...' flask --app app run
```

The app requires HTTPS, so a phone can't use `http://localhost`. Test the app against the
deployed server (or an HTTPS tunnel such as `cloudflared tunnel --url http://localhost:5000`).

## Deploy on PythonAnywhere

1. **Firestore:** in the Firebase console, create a project, then *Build → Firestore Database →
   Create database* (production mode; no client rules are needed because only this server uses
   it). Under *Project settings → Service accounts → Generate new private key*, download the JSON.
2. **Code:** in a PythonAnywhere Bash console:
   ```bash
   git clone https://github.com/Hal0g3n/tomodoom.git
   cd tomodoom/backend
   mkvirtualenv tomodoom --python=python3.10
   pip install -r requirements.txt
   ```
   Upload the service-account JSON (*Files* tab) somewhere **outside** the repo, e.g.
   `/home/<you>/secrets/firebase.json`.
3. **Web app:** *Web → Add a new web app → Manual configuration → Python 3.10*. Set
   *Virtualenv* to `tomodoom` and *Source code* to `/home/<you>/tomodoom/backend`. Edit the WSGI
   file so it contains only:
   ```python
   import os, sys
   os.environ.update({
       "BACKEND_TOKEN": "...",
       "MCP_KEY": "...",
       "TRUFAR_CONVERSATION_ID": "13549",
       "TRUFAR_COOKIE": "...",
       "GOOGLE_APPLICATION_CREDENTIALS": "/home/<you>/secrets/firebase.json",
   })
   sys.path.insert(0, "/home/<you>/tomodoom/backend")
   from app import app as application
   ```
   Click **Reload**. `https://<you>.pythonanywhere.com/` should show `"ok": true`.
4. **App:** in `~/.gradle/gradle.properties` (outside the repo) set
   `TRUFAR_API_URL=https://<you>.pythonanywhere.com` and `TRUFAR_API_TOKEN=<BACKEND_TOKEN>`, leave
   `TRUFAR_LLM_URL` unset, then rebuild and reinstall.
5. **TruFar:** in Kafka's editor, add an MCP server with endpoint
   `https://<you>.pythonanywhere.com/mcp` and header `X-MCP-Key: <MCP_KEY>` (or, if only a URL is
   accepted, `https://<you>.pythonanywhere.com/mcp?key=<MCP_KEY>`). Then add this to Kafka's prompt:
   > When the user answers a quiz question correctly, call the `award_hp` tool with amount 10
   > before you reply, and end your reply with [[CORRECT]]. When they answer wrongly, end your
   > reply with [[WRONG]]. Never call award_hp for anything else.

   Kafka's agent mode is ReAct with `max_iteration` 3, which is enough for one tool call per turn.

## Known limitations

- **PythonAnywhere free tier:** outbound traffic goes through an allowlist proxy, and trufar.cn is
  probably not on it. If it isn't, the app shows *"TruFar unreachable from host (PythonAnywhere
  free allowlist?)"*. Fix it by upgrading to a paid account or hosting the same code elsewhere
  (Render, Railway, Fly.io, or your PC plus a tunnel). No code change is needed.
- **One web worker (free tier):** Kafka's `/mcp` call arrives while `/messages` still occupies the
  only worker, so it waits until the turn ends and is then rejected. The `[[CORRECT]]` marker
  still grants the HP, which is why the prompt asks for both.
- **Cookie expiry:** when the TruFar login expires, the app shows *"TruFar login expired: refresh
  TRUFAR_COOKIE"*. Copy a fresh `cookie` header from DevTools into the WSGI file and reload.
- **One shared TruFar conversation:** every app session talks to the same conversation (fine for
  a one-person demo). Only one turn runs at a time; a second one gets HTTP 409 "Kafka is busy".
  Per-user conversations need the request TruFar sends for "new chat", which hasn't been captured yet.
- The trufar.cn endpoint is undocumented and may change without notice; check TruFar's terms of use.
- Kafka can be talked into awarding HP. Each award is at most 10, and the app caps and de-duplicates.
