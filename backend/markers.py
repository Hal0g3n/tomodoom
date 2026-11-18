"""Port of the app's trufar/QuizMarkerParser: grading markers in Kafka's replies.

Kafka's TruFar prompt asks it to end a reply that grades an answer with [[CORRECT]] or [[WRONG]].
The markers are stripped from what the user sees. This is the HP fallback for when the MCP
award_hp call can't reach the backend in time (see CLAUDE.md §5.2).
"""

from __future__ import annotations

import re
from dataclasses import dataclass

NONE, CORRECT, WRONG = "none", "correct", "wrong"
EMPTY_REPLY_FALLBACK = "…"

# Tolerates case, inner whitespace and single/double/triple brackets, e.g. "[ correct ]]".
_MARKER = re.compile(r"\[{1,3}\s*(CORRECT|WRONG)\s*\]{1,3}", re.IGNORECASE)
_EXTRA_BLANK_LINES = re.compile(r"\n{3,}")


@dataclass(frozen=True)
class Parsed:
    text: str      # reply without markers, trimmed, never empty
    verdict: str   # NONE, CORRECT or WRONG; the last marker wins


def parse(reply: str) -> Parsed:
    verdict = NONE
    for match in _MARKER.finditer(reply):
        verdict = CORRECT if match.group(1).upper() == "CORRECT" else WRONG
    text = _EXTRA_BLANK_LINES.sub("\n\n", _MARKER.sub("", reply).strip())
    return Parsed(text or EMPTY_REPLY_FALLBACK, verdict)
