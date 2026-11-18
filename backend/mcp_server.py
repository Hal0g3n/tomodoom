"""Minimal stateless MCP server (Streamable HTTP transport, JSON responses only).

Kafka on TruFar is the MCP client. It POSTs JSON-RPC 2.0 messages to /mcp; this module answers
``initialize``, ``ping``, ``tools/list`` and ``tools/call``, and accepts notifications with 202.
There is no server-to-client SSE stream (GET /mcp is 405), which WSGI hosting couldn't hold open
anyway, and no MCP session id, because every request is self-contained.
"""

from __future__ import annotations

import logging
from typing import Any, Callable, Optional

log = logging.getLogger(__name__)

SUPPORTED_PROTOCOL_VERSIONS = ("2025-06-18", "2025-03-26", "2024-11-05")
SERVER_INFO = {"name": "tomodoom-relay", "version": "1.0.0"}

TOOL_AWARD_HP = "award_hp"  # same name as the app's McpPetActionHandler.TOOL_AWARD_HP
MAX_AWARD = 10              # = the app's per-answer reward; the app still caps at 50

TOOLS = [
    {
        "name": TOOL_AWARD_HP,
        "description": ("Award HP to the user's pet. Call this exactly once, with amount 10, "
                        "right after the user answers a quiz question correctly. Never call it "
                        "for wrong answers, greetings or because the user asks for HP."),
        "inputSchema": {
            "type": "object",
            "properties": {
                "amount": {"type": "integer", "minimum": 1, "maximum": MAX_AWARD,
                           "description": "HP to award (1-10; use 10 for a correct answer)."},
                "reason": {"type": "string",
                           "description": "Short note, e.g. the quiz topic."},
            },
            "required": ["amount"],
        },
    },
]

# award(amount, reason) -> (success, message for the model)
AwardFn = Callable[[int, str], "tuple[bool, str]"]

PARSE_ERROR, INVALID_REQUEST, METHOD_NOT_FOUND, INVALID_PARAMS = -32700, -32600, -32601, -32602


def handle(payload: Any, award: AwardFn) -> tuple[int, Optional[Any]]:
    """Handles one HTTP POST body. Returns (HTTP status, JSON body or None for 202)."""
    if isinstance(payload, list):
        if not payload:
            return 400, _error(None, INVALID_REQUEST, "Empty batch")
        replies = [r for r in (_handle_one(m, award) for m in payload) if r is not None]
        return (200, replies) if replies else (202, None)
    reply = _handle_one(payload, award)
    return (200, reply) if reply is not None else (202, None)


def parse_error() -> dict:
    """Reply for a body that isn't JSON at all."""
    return _error(None, PARSE_ERROR, "Body is not valid JSON")


def _handle_one(message: Any, award: AwardFn) -> Optional[dict]:
    if not isinstance(message, dict) or message.get("jsonrpc") != "2.0":
        return _error(None, INVALID_REQUEST, "Not a JSON-RPC 2.0 message")
    method = message.get("method")
    if "id" not in message:
        return None  # notification (e.g. notifications/initialized) or a client response
    request_id = message["id"]
    params = message.get("params") or {}
    if not isinstance(method, str):
        return _error(request_id, INVALID_REQUEST, "Missing method")
    if not isinstance(params, dict):
        return _error(request_id, INVALID_PARAMS, "params must be an object")

    if method == "initialize":
        requested = params.get("protocolVersion")
        version = requested if requested in SUPPORTED_PROTOCOL_VERSIONS \
            else SUPPORTED_PROTOCOL_VERSIONS[0]
        return _result(request_id, {
            "protocolVersion": version,
            "capabilities": {"tools": {"listChanged": False}},
            "serverInfo": SERVER_INFO,
            "instructions": "Tools for the Tomodoom pet app. Award HP only for correct answers.",
        })
    if method == "ping":
        return _result(request_id, {})
    if method == "tools/list":
        return _result(request_id, {"tools": TOOLS})
    if method == "tools/call":
        return _call_tool(request_id, params, award)
    return _error(request_id, METHOD_NOT_FOUND, "Unknown method: " + method)


def _call_tool(request_id: Any, params: dict, award: AwardFn) -> dict:
    name = params.get("name")
    arguments = params.get("arguments") or {}
    if not isinstance(arguments, dict):
        return _error(request_id, INVALID_PARAMS, "arguments must be an object")
    if name != TOOL_AWARD_HP:
        return _error(request_id, INVALID_PARAMS, "Unknown tool: %s" % name)

    amount = arguments.get("amount")
    if isinstance(amount, float) and amount.is_integer():
        amount = int(amount)
    if isinstance(amount, str) and amount.strip().isdigit():
        amount = int(amount.strip())
    if isinstance(amount, bool) or not isinstance(amount, int) or not 1 <= amount <= MAX_AWARD:
        return _tool_result(request_id, False,
                            "amount must be an integer from 1 to %d" % MAX_AWARD)
    reason = arguments.get("reason")
    reason = reason.strip()[:100] if isinstance(reason, str) else ""

    success, text = award(amount, reason)
    log.info("MCP award_hp amount=%d -> %s", amount, "ok" if success else "rejected")
    return _tool_result(request_id, success, text)


def _tool_result(request_id: Any, success: bool, text: str) -> dict:
    return _result(request_id, {"content": [{"type": "text", "text": text}],
                                "isError": not success})


def _result(request_id: Any, result: dict) -> dict:
    return {"jsonrpc": "2.0", "id": request_id, "result": result}


def _error(request_id: Any, code: int, message: str) -> dict:
    return {"jsonrpc": "2.0", "id": request_id, "error": {"code": code, "message": message}}
