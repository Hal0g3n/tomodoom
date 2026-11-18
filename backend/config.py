"""Backend configuration, read from environment variables only (no secrets in git)."""

from __future__ import annotations

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Config:
    # Bearer token the Android app sends (= the app's TRUFAR_API_TOKEN Gradle property).
    backend_token: str
    # Shared secret TruFar sends when Kafka calls the MCP endpoint.
    mcp_key: str
    # TruFar web chat: origin, the conversation to relay into, and the browser login cookie.
    trufar_base_url: str
    trufar_conversation_id: str
    trufar_cookie: str
    # "firestore" or "memory" (memory = single process only: local dev and tests).
    store: str

    @staticmethod
    def from_env() -> "Config":
        def get(name: str, default: str = "") -> str:
            return os.environ.get(name, default).strip()

        store = get("TOMODOOM_STORE")
        if not store:
            store = "firestore" if get("GOOGLE_APPLICATION_CREDENTIALS") else "memory"
        return Config(
            backend_token=get("BACKEND_TOKEN"),
            mcp_key=get("MCP_KEY"),
            trufar_base_url=get("TRUFAR_BASE_URL", "https://trufar.cn").rstrip("/"),
            trufar_conversation_id=get("TRUFAR_CONVERSATION_ID"),
            trufar_cookie=get("TRUFAR_COOKIE"),
            store=store,
        )

    def missing(self) -> list[str]:
        """Names of required settings that are empty."""
        required = {
            "BACKEND_TOKEN": self.backend_token,
            "MCP_KEY": self.mcp_key,
            "TRUFAR_CONVERSATION_ID": self.trufar_conversation_id,
            "TRUFAR_COOKIE": self.trufar_cookie,
        }
        return [name for name, value in required.items() if not value]
