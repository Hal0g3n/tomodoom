"""Session and turn state, shared by every web-worker process.

PythonAnywhere may run several worker *processes*, and Kafka's MCP call (award_hp) can land in a
different one from the request that is relaying the turn. So "which turn is in flight" can't live
in process memory: it is a lease in the store (Firestore in production). Only one TruFar turn
runs at a time, because every app session relays into the same TruFar conversation.

Layout (Firestore):
    sessions/{id}            {created, turn_count, closed}
    sessions/{id}/turns/{n}  {awarded, amount, reason, at}      audit log, no message text
    relay/current            {session_id, turn, started, award, reason}   the turn lease
"""

from __future__ import annotations

import json
import threading
import time
import uuid
from dataclasses import dataclass
from typing import Optional

# A lease older than this is treated as abandoned (the worker died mid-turn).
TURN_LEASE_S = 300


class StoreError(Exception):
    """Base class; ``str(error)`` is safe to show to the user."""


class SessionNotFound(StoreError):
    pass


class RelayBusy(StoreError):
    pass


@dataclass(frozen=True)
class Award:
    amount: int
    reason: str


class Store:
    def create_session(self) -> str:
        raise NotImplementedError

    def close_session(self, session_id: str) -> None:
        raise NotImplementedError

    def begin_turn(self, session_id: str) -> int:
        """Takes the relay lease for the next turn of ``session_id`` and returns its number."""
        raise NotImplementedError

    def add_award(self, amount: int, reason: str) -> Optional[tuple[str, int]]:
        """Attaches an award to the turn in flight; returns (session, turn), or None if idle."""
        raise NotImplementedError

    def end_turn(self, session_id: str, turn: int) -> Optional[Award]:
        """Releases the lease and returns the award made during the turn, if any."""
        raise NotImplementedError

    def record_turn(self, session_id: str, turn: int, awarded: bool, amount: int,
                    reason: str) -> None:
        raise NotImplementedError


def _new_session_id() -> str:
    return uuid.uuid4().hex


def _lease_active(lease: Optional[dict], now: float) -> bool:
    return bool(lease) and now - float(lease.get("started", 0)) < TURN_LEASE_S


class MemoryStore(Store):
    """In-process store for tests and local ``flask run``. Not shared across processes."""

    def __init__(self, clock=time.time) -> None:
        self._lock = threading.Lock()
        self._clock = clock
        self.sessions: dict[str, dict] = {}
        self.turns: dict[tuple[str, int], dict] = {}
        self.lease: Optional[dict] = None

    def create_session(self) -> str:
        session_id = _new_session_id()
        with self._lock:
            self.sessions[session_id] = {"created": self._clock(), "turn_count": 0,
                                         "closed": False}
        return session_id

    def _open_session(self, session_id: str) -> dict:
        session = self.sessions.get(session_id)
        if session is None or session["closed"]:
            raise SessionNotFound("Chat session not found; reopen the Chat tab")
        return session

    def close_session(self, session_id: str) -> None:
        with self._lock:
            if session_id in self.sessions:
                self.sessions[session_id]["closed"] = True

    def begin_turn(self, session_id: str) -> int:
        with self._lock:
            session = self._open_session(session_id)
            if _lease_active(self.lease, self._clock()):
                raise RelayBusy("Kafka is busy with another message; try again in a moment")
            session["turn_count"] += 1
            turn = session["turn_count"]
            self.lease = {"session_id": session_id, "turn": turn, "started": self._clock(),
                          "award": None, "reason": ""}
            return turn

    def add_award(self, amount: int, reason: str) -> Optional[tuple[str, int]]:
        with self._lock:
            lease = self.lease
            if not _lease_active(lease, self._clock()):
                return None
            lease["award"] = max(int(lease["award"] or 0), amount)
            lease["reason"] = reason
            return lease["session_id"], lease["turn"]

    def end_turn(self, session_id: str, turn: int) -> Optional[Award]:
        with self._lock:
            lease = self.lease
            if not lease or lease["session_id"] != session_id or lease["turn"] != turn:
                return None
            self.lease = None
            if lease["award"] is None:
                return None
            return Award(int(lease["award"]), lease["reason"])

    def record_turn(self, session_id: str, turn: int, awarded: bool, amount: int,
                    reason: str) -> None:
        with self._lock:
            self.turns[(session_id, turn)] = {"awarded": awarded, "amount": amount,
                                              "reason": reason, "at": self._clock()}


class FirestoreStore(Store):
    """Firestore-backed store, safe across processes (all lease changes are transactions)."""

    def __init__(self, service_account_json: str = "") -> None:
        # Validate the key first, so a bad paste is reported even before the SDK loads.
        key_info = parse_service_account(service_account_json) if service_account_json else None
        # Imported lazily so tests and local runs don't need the Firebase SDK installed.
        import firebase_admin
        from firebase_admin import credentials, firestore

        if not firebase_admin._apps:
            if key_info is not None:
                # Hosts without a file system for a key file (Vercel): the JSON is an env var.
                firebase_admin.initialize_app(credentials.Certificate(key_info))
            else:
                # Application Default Credentials: GOOGLE_APPLICATION_CREDENTIALS key file,
                # or the service's own account on Google Cloud (Cloud Run).
                firebase_admin.initialize_app()
        self._firestore = firestore
        self._db = firestore.client()
        self._lease_ref = self._db.collection("relay").document("current")

    def _session_ref(self, session_id: str):
        return self._db.collection("sessions").document(session_id)

    def create_session(self) -> str:
        session_id = _new_session_id()
        self._session_ref(session_id).set({"created": time.time(), "turn_count": 0,
                                           "closed": False})
        return session_id

    def close_session(self, session_id: str) -> None:
        ref = self._session_ref(session_id)
        if ref.get().exists:
            ref.update({"closed": True})

    def begin_turn(self, session_id: str) -> int:
        session_ref = self._session_ref(session_id)

        @self._firestore.transactional
        def run(transaction) -> int:
            session = session_ref.get(transaction=transaction)
            if not session.exists or session.get("closed"):
                raise SessionNotFound("Chat session not found; reopen the Chat tab")
            lease = self._lease_ref.get(transaction=transaction)
            now = time.time()
            if lease.exists and _lease_active(lease.to_dict(), now):
                raise RelayBusy("Kafka is busy with another message; try again in a moment")
            turn = int(session.get("turn_count") or 0) + 1
            transaction.update(session_ref, {"turn_count": turn})
            transaction.set(self._lease_ref, {"session_id": session_id, "turn": turn,
                                              "started": now, "award": None, "reason": ""})
            return turn

        return run(self._db.transaction())

    def add_award(self, amount: int, reason: str) -> Optional[tuple[str, int]]:
        @self._firestore.transactional
        def run(transaction) -> Optional[tuple[str, int]]:
            snapshot = self._lease_ref.get(transaction=transaction)
            lease = snapshot.to_dict() if snapshot.exists else None
            if not _lease_active(lease, time.time()):
                return None
            award = max(int(lease.get("award") or 0), amount)
            transaction.update(self._lease_ref, {"award": award, "reason": reason})
            return lease["session_id"], int(lease["turn"])

        return run(self._db.transaction())

    def end_turn(self, session_id: str, turn: int) -> Optional[Award]:
        @self._firestore.transactional
        def run(transaction) -> Optional[Award]:
            snapshot = self._lease_ref.get(transaction=transaction)
            lease = snapshot.to_dict() if snapshot.exists else None
            if not lease or lease.get("session_id") != session_id or \
                    int(lease.get("turn", -1)) != turn:
                return None
            transaction.delete(self._lease_ref)
            if lease.get("award") is None:
                return None
            return Award(int(lease["award"]), str(lease.get("reason") or ""))

        return run(self._db.transaction())

    def record_turn(self, session_id: str, turn: int, awarded: bool, amount: int,
                    reason: str) -> None:
        self._session_ref(session_id).collection("turns").document(str(turn)).set(
            {"awarded": awarded, "amount": amount, "reason": reason, "at": time.time()})


def parse_service_account(value: str) -> dict:
    """Parses the FIREBASE_SERVICE_ACCOUNT env var. Errors never include the (secret) value."""
    try:
        info = json.loads(value)
    except ValueError:
        raise ValueError("FIREBASE_SERVICE_ACCOUNT is not valid JSON; paste the whole "
                         "service-account key file") from None
    if not isinstance(info, dict) or info.get("type") != "service_account" or \
            not info.get("private_key") or not info.get("client_email"):
        raise ValueError("FIREBASE_SERVICE_ACCOUNT is not a Firebase service-account key "
                         "(expected type, private_key and client_email)")
    return info


def create_store(kind: str, service_account_json: str = "") -> Store:
    if kind == "firestore":
        return FirestoreStore(service_account_json)
    if kind == "memory":
        return MemoryStore()
    raise ValueError("TOMODOOM_STORE must be 'firestore' or 'memory', not %r" % kind)
