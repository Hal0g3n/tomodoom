"""Client for TruFar's web chat endpoint (undocumented; captured from the browser).

Protocol (see CLAUDE.md §5.2, "Relay backend"):
    POST {base}/api/mashang/core/answer/{conversation_id}
    body {"issue_content": text, "issue_config": {}, "entrance": "normal"}, auth = login cookie.
The response is a stream of ``data: {json}`` events. Each event is a *cumulative snapshot* of the
reply so far, and events are not always newline-separated (``}data: {``). The reply text is the
``value`` of the ``replyContent`` component in ``reply_config.replyAll``; ``reasonContent`` is the
model's thinking and is discarded.
"""

from __future__ import annotations

import json
import logging
import re
from typing import Iterable, Optional

import requests

log = logging.getLogger(__name__)

ANSWER_PATH = "/api/mashang/core/answer/{conversation_id}"
REPLY_COMPONENT = "replyContent"
CONNECT_TIMEOUT_S = 15
READ_TIMEOUT_S = 120  # per chunk; deepseek-v4-pro thinks before replying
MAX_STREAM_CHARS = 20_000_000  # snapshots repeat the whole text, so streams are large


class TruFarError(Exception):
    """A relay failure. ``str(error)`` is safe to show to the user."""


class TruFarAuthError(TruFarError):
    """TruFar rejected the login cookie (expired or wrong)."""


class TruFarUnreachable(TruFarError):
    """The host can't reach TruFar (e.g. PythonAnywhere free-tier allowlist)."""


class StreamParser:
    """Incremental parser for TruFar's ``data: {...}`` stream.

    Feed it text chunks as they arrive; it decodes each complete JSON object and keeps only the
    latest reply snapshot, so memory stays bounded however long the stream is.
    """

    _PREFIX = "data:"
    # Where one event ends and the next begins. Kafka's own text may contain "data:", but
    # "}data:" (with optional whitespace) only appears between events.
    _BOUNDARY = re.compile(r"\}\s*data:")

    def __init__(self) -> None:
        self._buffer = ""
        self._decoder = json.JSONDecoder()
        self.reply: str = ""
        self.fallback_reply: str = ""
        self.error: str = ""
        self.events = 0

    def feed(self, chunk: str) -> None:
        self._buffer += chunk
        while True:
            start = self._buffer.find(self._PREFIX)
            if start < 0:
                # Keep a tail in case "data:" is split across chunks.
                self._buffer = self._buffer[-len(self._PREFIX):]
                return
            pos = start + len(self._PREFIX)
            while pos < len(self._buffer) and self._buffer[pos] in " \t\r\n":
                pos += 1
            if pos >= len(self._buffer):
                self._buffer = self._buffer[start:]
                return
            try:
                obj, end = self._decoder.raw_decode(self._buffer, pos)
            except json.JSONDecodeError:
                boundary = self._BOUNDARY.search(self._buffer, pos)
                if boundary is None:
                    # No later event yet, so the object is probably just unfinished.
                    self._buffer = self._buffer[start:]
                    return
                # Malformed event: resync on the next event boundary.
                log.warning("skipping malformed TruFar stream event")
                self._buffer = self._buffer[boundary.start() + 1:]
                continue
            self._buffer = self._buffer[end:]
            self._handle(obj)

    def _handle(self, obj: object) -> None:
        if not isinstance(obj, dict):
            return
        self.events += 1
        error = obj.get("reply_error")
        if isinstance(error, str) and error.strip():
            self.error = error.strip()
        content = obj.get("reply_content")
        if isinstance(content, str) and content.strip():
            self.fallback_reply = content
        config = obj.get("reply_config")
        parts = config.get("replyAll") if isinstance(config, dict) else None
        if isinstance(parts, list):
            for part in parts:
                if (isinstance(part, dict) and part.get("component") == REPLY_COMPONENT
                        and isinstance(part.get("value"), str)):
                    self.reply = part["value"]

    def result(self) -> str:
        """The final reply text. Raises :class:`TruFarError` if there is none."""
        if self.error:
            raise TruFarError("Kafka (TruFar) reported an error: " + self.error[:200])
        text = (self.reply or self.fallback_reply).strip()
        if not text:
            raise TruFarError("Kafka (TruFar) sent an empty reply")
        return text


def parse_stream(chunks: Iterable[str]) -> str:
    """Parses a whole stream (an iterable of text chunks) and returns the reply."""
    parser = StreamParser()
    for chunk in chunks:
        parser.feed(chunk)
    return parser.result()


class TruFarClient:
    """Sends one user turn to a TruFar conversation and returns Kafka's reply."""

    def __init__(self, base_url: str, cookie: str,
                 session: Optional[requests.Session] = None) -> None:
        self._base_url = base_url.rstrip("/")
        self._cookie = cookie
        self._http = session or requests.Session()

    def send(self, conversation_id: str, text: str) -> str:
        url = self._base_url + ANSWER_PATH.format(conversation_id=conversation_id)
        headers = {
            "Accept": "text/event-stream, application/json",
            "Content-Type": "application/json",
            "Cookie": self._cookie,
            "Origin": self._base_url,
            "Referer": self._base_url + "/",
            "User-Agent": "Mozilla/5.0 (compatible; tomodoom-relay/1.0)",
        }
        body = {"issue_content": text, "issue_config": {}, "entrance": "normal"}
        try:
            response = self._http.post(url, json=body, headers=headers, stream=True,
                                       timeout=(CONNECT_TIMEOUT_S, READ_TIMEOUT_S),
                                       allow_redirects=False)
        except requests.exceptions.ProxyError as error:
            raise TruFarUnreachable(
                "TruFar unreachable from host (PythonAnywhere free allowlist?)") from error
        except requests.exceptions.Timeout as error:
            raise TruFarError("Kafka (TruFar) took too long to answer") from error
        except requests.exceptions.RequestException as error:
            raise TruFarUnreachable("Could not reach TruFar: " + type(error).__name__) from error

        with response:
            self._check_status(response)
            if response.encoding is None:
                response.encoding = "utf-8"
            parser = StreamParser()
            head = ""  # start of the body, kept to explain a non-stream (error) response
            total = 0
            try:
                for chunk in response.iter_content(chunk_size=8192, decode_unicode=True):
                    if not chunk:
                        continue
                    if isinstance(chunk, bytes):
                        chunk = chunk.decode("utf-8", errors="replace")
                    if len(head) < 4096:
                        head += chunk[:4096 - len(head)]
                    total += len(chunk)
                    if total > MAX_STREAM_CHARS:
                        raise TruFarError("Kafka (TruFar) reply exceeded the size limit")
                    parser.feed(chunk)
            except requests.exceptions.RequestException as error:
                raise TruFarError("Kafka (TruFar) stream broke off") from error
            if parser.events == 0:
                raise non_stream_error(head)
            return parser.result()

    @staticmethod
    def _check_status(response: requests.Response) -> None:
        status = response.status_code
        if status in (401, 403) or 300 <= status < 400:
            raise TruFarAuthError("TruFar login expired: refresh TRUFAR_COOKIE")
        if status >= 400:
            raise TruFarError("TruFar returned HTTP %d" % status)
        if "text/html" in response.headers.get("Content-Type", ""):
            # A login page instead of the stream.
            raise TruFarAuthError("TruFar login expired: refresh TRUFAR_COOKIE")



def non_stream_error(body: str) -> TruFarError:
    """No events at all: TruFar probably answered with a JSON error body instead of a stream."""
    try:
        payload = json.loads(body or "{}")
    except ValueError:
        payload = {}
    message = ""
    if isinstance(payload, dict):
        code = payload.get("code")
        if code in (401, 403, "401", "403"):
            return TruFarAuthError("TruFar login expired: refresh TRUFAR_COOKIE")
        for key in ("msg", "message", "error"):
            if isinstance(payload.get(key), str) and payload[key].strip():
                message = payload[key].strip()
                break
    if message:
        return TruFarError("TruFar error: " + message[:200])
    return TruFarError("TruFar sent no reply stream")
