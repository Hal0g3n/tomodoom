"""Tomodoom relay backend: the Android app <-> this Flask app <-> Kafka on the TruFar website.

The app talks to the /v1/chat/sessions event API (CLAUDE.md §5.2, implemented on the device by
trufar/HttpTruFarChatService). Each user message is relayed into one TruFar web-chat conversation
and Kafka's reply is returned as an event. HP for a correct answer comes from Kafka's MCP
``award_hp`` call or, as a fallback, from a [[CORRECT]] marker in the reply. Either way the app
receives a ``quiz_evaluated`` event and applies the HP through its own McpPetActionHandler, which
keeps the replay guard and the award cap on the device.

WSGI entry point: ``app`` (PythonAnywhere: ``from app import app as application``).
"""

from __future__ import annotations

import hmac
import logging
from functools import wraps
from typing import Optional

from flask import Flask, jsonify, request

import markers
from config import Config
from store import RelayBusy, SessionNotFound, Store, create_store
from trufar_client import TruFarAuthError, TruFarClient, TruFarError

log = logging.getLogger("tomodoom.relay")

START_TURN = "__START__"     # hidden first turn; Kafka's persona answers it with a greeting
CORRECT_ANSWER_HP = 10       # = the app's OpenAiCompatibleChatService.CORRECT_ANSWER_HP
MAX_MESSAGE_CHARS = 4000


class _Relay:
    """Runs one TruFar turn under the store's lease and turns the result into app events."""

    def __init__(self, config: Config, store: Store, trufar: TruFarClient) -> None:
        self.config = config
        self.store = store
        self.trufar = trufar

    def turn(self, session_id: str, text: str, allow_award: bool) -> dict:
        turn = self.store.begin_turn(session_id)
        try:
            raw = self.trufar.send(self.config.trufar_conversation_id, text)
        finally:
            award = self.store.end_turn(session_id, turn)
        parsed = markers.parse(raw)

        amount, reason = 0, ""
        if allow_award:
            if award is not None:
                amount, reason = min(award.amount, CORRECT_ANSWER_HP), "mcp:" + award.reason
            elif parsed.verdict == markers.CORRECT:
                amount, reason = CORRECT_ANSWER_HP, "marker"
        self.store.record_turn(session_id, turn, amount > 0, amount, reason)
        log.info("turn %s:%d relayed (award=%d via %s)", session_id[:8], turn, amount,
                 reason or "-")

        if amount > 0:
            return {"type": "quiz_evaluated", "quiz_id": "trufar:%s:%d" % (session_id, turn),
                    "passed": True, "hp_reward": amount, "feedback": parsed.text}
        return {"type": "message", "text": parsed.text}


def _error(status: int, message: str):
    return jsonify({"error": {"message": message}}), status


def create_app(config: Optional[Config] = None, store: Optional[Store] = None,
               trufar: Optional[TruFarClient] = None) -> Flask:
    config = config or Config.from_env()
    app = Flask(__name__)
    app.json.ensure_ascii = False
    missing = config.missing()
    if missing:
        log.error("relay not configured; missing: %s", ", ".join(missing))
    relay = None
    if not missing:
        relay = _Relay(config, store or create_store(config.store),
                       trufar or TruFarClient(config.trufar_base_url, config.trufar_cookie))
    app.config["RELAY"] = relay

    def requires_app_token(view):
        @wraps(view)
        def wrapper(*args, **kwargs):
            if relay is None:
                return _error(503, "Relay backend not configured (missing %s)"
                              % ", ".join(missing))
            supplied = request.headers.get("Authorization", "")
            expected = "Bearer " + config.backend_token
            if not hmac.compare_digest(supplied.encode(), expected.encode()):
                return _error(401, "Bad or missing backend token")
            return view(*args, **kwargs)
        return wrapper

    @app.errorhandler(SessionNotFound)
    def _session_not_found(error):
        return _error(404, str(error))

    @app.errorhandler(RelayBusy)
    def _busy(error):
        return _error(409, str(error))

    @app.errorhandler(TruFarError)
    def _trufar_failed(error):
        log.warning("TruFar relay failed: %s", error)
        status = 503 if isinstance(error, TruFarAuthError) else 502
        return _error(status, str(error))

    @app.get("/")
    def health():
        return jsonify({"ok": relay is not None, "service": "tomodoom-relay",
                        "missing": missing})

    @app.post("/v1/chat/sessions")
    @requires_app_token
    def create_session():
        session_id = relay.store.create_session()
        greeting = relay.turn(session_id, START_TURN, allow_award=False)
        return jsonify({"session_id": session_id, "events": [greeting]})

    @app.post("/v1/chat/sessions/<session_id>/messages")
    @requires_app_token
    def send_message(session_id: str):
        body = request.get_json(silent=True)
        text = body.get("text") if isinstance(body, dict) else None
        if not isinstance(text, str) or not text.strip():
            return _error(400, "text is required")
        if len(text) > MAX_MESSAGE_CHARS:
            return _error(400, "Message is too long (max %d characters)" % MAX_MESSAGE_CHARS)
        if text.strip() == START_TURN:
            return _error(400, "Reserved message")
        return jsonify({"events": [relay.turn(session_id, text, allow_award=True)]})

    @app.post("/v1/chat/sessions/<session_id>/quiz-answers")
    @requires_app_token
    def quiz_answer(session_id: str):
        # Kafka quizzes in free text, so the app never has an active QuizPrompt from us.
        return jsonify({"events": []})

    @app.post("/v1/chat/sessions/<session_id>/tool-results")
    @requires_app_token
    def tool_result(session_id: str):
        # We never send tool_call events to the app; accept and ignore.
        return jsonify({"events": []})

    @app.delete("/v1/chat/sessions/<session_id>")
    @requires_app_token
    def close_session(session_id: str):
        relay.store.close_session(session_id)
        return "", 204

    return app


logging.basicConfig(level=logging.INFO)
app = create_app()
