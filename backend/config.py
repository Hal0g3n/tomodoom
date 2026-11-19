"""Backend configuration, read from environment variables only (no secrets in git)."""

from __future__ import annotations

import os
from dataclasses import dataclass, field


@dataclass(frozen=True)
class Config:
    # Secrets use repr=False so a logged or printed Config never shows them.
    # Bearer token the Android app sends (= the app's TRUFAR_API_TOKEN Gradle property).
    backend_token: str = field(repr=False)
    # Shared secret TruFar sends when Kafka calls the MCP endpoint.
    mcp_key: str = field(repr=False)
    # TruFar web chat: origin, the conversation to relay into, and the browser login cookie.
    trufar_base_url: str
    trufar_conversation_id: str
    trufar_cookie: str = field(repr=False)
    # "firestore" or "memory" (memory = single process only: local dev and tests).
    store: str
    # Firebase service-account key JSON, for hosts without a key file (Vercel). Optional.
    firebase_service_account: str = field(default="", repr=False)

    @staticmethod
    def from_env() -> "Config":
        def get(name: str, default: str = "") -> str:
            return os.environ.get(name, default).strip()

        service_account = get("FIREBASE_SERVICE_ACCOUNT")
        store = get("TOMODOOM_STORE")
        if not store:
            # Firestore when there are Google credentials: the key JSON in an env var (Vercel),
            # a key file, or Cloud Run's own service account (Cloud Run sets K_SERVICE).
            # Memory otherwise (tests, local runs).
            has_credentials = (service_account or get("GOOGLE_APPLICATION_CREDENTIALS")
                               or get("K_SERVICE"))
            store = "firestore" if has_credentials else "memory"
        return Config(
            backend_token=get("BACKEND_TOKEN"),
            mcp_key=get("MCP_KEY"),
            trufar_base_url=get("TRUFAR_BASE_URL", "https://trufar.cn").rstrip("/"),
            trufar_conversation_id=get("TRUFAR_CONVERSATION_ID"),
            trufar_cookie=get("TRUFAR_COOKIE"),
            store=store,
            firebase_service_account=service_account,
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
