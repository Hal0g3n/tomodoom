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
| `Procfile`, `.python-version`, `.gcloudignore` | Cloud Run build: gunicorn command, Python 3.12, upload exclusions |

## Configuration (environment variables)

| Variable | Value |
|---|---|
| `BACKEND_TOKEN` | long random string; also set as the app's `TRUFAR_API_TOKEN` Gradle property |
| `MCP_KEY` | another long random string; given to TruFar when you register the MCP server |
| `TRUFAR_CONVERSATION_ID` | the number at the end of `/api/mashang/core/answer/<id>` in DevTools (e.g. `13549`) |
| `TRUFAR_COOKIE` | the whole `cookie` request header from that request (DevTools → Headers) |
| `TRUFAR_BASE_URL` | optional, default `https://trufar.cn` |
| `GOOGLE_CLOUD_PROJECT` | Google Cloud project id (set on Cloud Run so Firestore knows the project) |
| `GOOGLE_APPLICATION_CREDENTIALS` | path to a service-account JSON; only needed **outside** Google Cloud |
| `TOMODOOM_STORE` | optional: `firestore` or `memory` (default: firestore on Cloud Run or when a key file is set) |

On Cloud Run, `BACKEND_TOKEN`, `MCP_KEY` and `TRUFAR_COOKIE` come from Secret Manager (see below).

Make random secrets with `python -c "import secrets; print(secrets.token_urlsafe(32))"`.
**Never commit** the cookie, tokens or service-account file. The cookie is your TruFar login:
anyone who has it can use your account.

`GET /` reports `{"ok": true}` once everything required is set, or lists what's missing.

## Run locally

```bash
cd backend
python -m venv .venv && .venv/Scripts/activate      # macOS/Linux: source .venv/bin/activate
pip install -r requirements-dev.txt
python -m pytest tests                              # 67 tests, no network
# Local server with an in-memory store:
BACKEND_TOKEN=dev MCP_KEY=dev TRUFAR_CONVERSATION_ID=13549 TRUFAR_COOKIE='...' flask --app app run
```

The app requires HTTPS, so a phone can't use `http://localhost`. Test the app against the
deployed server (or an HTTPS tunnel such as `cloudflared tunnel --url http://localhost:5000`).

## Deploy on Google Cloud Run (recommended)

All commands run in **Cloud Shell** (console.cloud.google.com → the **>_** button), which has
`gcloud` built in. The region `asia-east1` (Taiwan) is the closest Tier-1 region to trufar.cn.

1. **Project + billing:** at console.firebase.google.com → **Add project** (Analytics not needed),
   then upgrade it to the **Blaze** plan. A card is required; this usage stays within the free
   tier. Add a budget alert under *Billing → Budgets & alerts* to be safe.
2. **APIs and the database:**
   ```bash
   gcloud config set project <project-id>
   gcloud services enable run.googleapis.com cloudbuild.googleapis.com \
     artifactregistry.googleapis.com secretmanager.googleapis.com firestore.googleapis.com
   gcloud firestore databases create --location=asia-east1
   ```
   If the database already exists (for example, created from the Firebase console), that last
   command says so. That's fine.
3. **Secrets.** Each value is pasted at a silent prompt, so it's never shown or kept in shell history:
   ```bash
   read -rsp "BACKEND_TOKEN: " V && printf %s "$V" | gcloud secrets create backend-token --data-file=- ; echo
   read -rsp "MCP_KEY: " V && printf %s "$V" | gcloud secrets create mcp-key --data-file=- ; echo
   read -rsp "TRUFAR_COOKIE: " V && printf %s "$V" | gcloud secrets create trufar-cookie --data-file=- ; echo
   unset V
   ```
4. **Permissions** for the service's runtime account (the default compute service account):
   ```bash
   P=$(gcloud config get-value project)
   SA=$(gcloud projects describe $P --format='value(projectNumber)')-compute@developer.gserviceaccount.com
   for R in roles/secretmanager.secretAccessor roles/datastore.user; do
     gcloud projects add-iam-policy-binding $P --member=serviceAccount:$SA --role=$R --condition=None
   done
   ```
5. **Deploy:**
   ```bash
   git clone https://github.com/Hal0g3n/tomodoom.git && cd tomodoom/backend
   gcloud run deploy tomodoom-relay --source . --region asia-east1 --allow-unauthenticated \
     --max-instances 2 --timeout 300 \
     --set-env-vars TRUFAR_CONVERSATION_ID=<conversation id>,GOOGLE_CLOUD_PROJECT=$P \
     --set-secrets BACKEND_TOKEN=backend-token:latest,MCP_KEY=mcp-key:latest,TRUFAR_COOKIE=trufar-cookie:latest
   ```
   Answer **Y** if it offers to create an Artifact Registry repository or grant Cloud Build
   permissions. It ends by printing the service URL, `https://tomodoom-relay-….run.app`.
   - `--allow-unauthenticated` is intended: the app and TruFar must reach it, and the app token
     and MCP key protect it.
   - If the build fails with a permissions error, grant the same service account
     `roles/cloudbuild.builds.builder` and run the deploy again.
6. **Check:** opening `<url>/` shows `"ok": true`. Logs are at *Cloud Run → tomodoom-relay →
   Logs*, or `gcloud run services logs read tomodoom-relay --region asia-east1`.
7. **App and TruFar:** follow steps 4–5 of the PythonAnywhere section below, using the run.app
   URL instead of the PythonAnywhere one.

**Updating:** run `cd ~/tomodoom && git pull`, then repeat the step 5 deploy command.
**New TruFar cookie:**
```bash
read -rsp "TRUFAR_COOKIE: " V && printf %s "$V" | gcloud secrets versions add trufar-cookie --data-file=- ; unset V; echo
gcloud run services update tomodoom-relay --region asia-east1   # new revision picks up :latest
```

## Deploy on PythonAnywhere (paid plans only)

The **free plan can't reach trufar.cn** (confirmed: the relay answers "TruFar unreachable from
host"). These steps only work on a paid account. Steps 4–5 (app and TruFar setup) apply to any host.

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

- **PythonAnywhere free tier:** outbound traffic goes through an allowlist proxy that blocks
  trufar.cn (confirmed). The app shows *"TruFar unreachable from host (PythonAnywhere free
  allowlist?)"*. Use Cloud Run or a paid account. With a single web worker, Kafka's `/mcp` call
  also can't be served until the turn ends; on Cloud Run, gunicorn's 8 threads avoid that. The
  prompt still asks for the `[[CORRECT]]` marker as a fallback.
- **Cloud Run reaching trufar.cn** hasn't been confirmed yet. trufar.cn is China-hosted and could
  block or slow down Google Cloud addresses. The first chat after a deploy tells you.
- **Cold starts:** after a period with no traffic, Cloud Run's first request takes a few extra
  seconds. That's well within the app's 120-second timeout.
- **Cookie expiry:** when the TruFar login expires, the app shows *"TruFar login expired: refresh
  TRUFAR_COOKIE"*. Copy a fresh `cookie` header from DevTools and add it as a new Secret Manager
  version (see "New TruFar cookie" above), or into the WSGI file on PythonAnywhere.
- **One shared TruFar conversation:** every app session talks to the same conversation (fine for
  a one-person demo). Only one turn runs at a time; a second one gets HTTP 409 "Kafka is busy".
  Per-user conversations need the request TruFar sends for "new chat", which hasn't been captured yet.
- The trufar.cn endpoint is undocumented and may change without notice; check TruFar's terms of use.
- Kafka can be talked into awarding HP. Each award is at most 10, and the app caps and de-duplicates.
