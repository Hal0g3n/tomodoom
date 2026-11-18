import pytest

from app import create_app
from config import Config
from store import MemoryStore
from trufar_client import TruFarAuthError

TOKEN = "test-token"
AUTH = {"Authorization": "Bearer " + TOKEN}


def make_config(**overrides) -> Config:
    values = dict(backend_token=TOKEN, mcp_key="mcp-key", trufar_base_url="https://trufar.cn",
                  trufar_conversation_id="13549", trufar_cookie="sid=x", store="memory")
    values.update(overrides)
    return Config(**values)


class FakeTruFar:
    """Replies from a queue; ``during_turn`` runs mid-turn, as Kafka's MCP call would."""

    def __init__(self):
        self.replies = []
        self.sent = []
        self.during_turn = None
        self.error = None

    def send(self, conversation_id, text):
        self.sent.append((conversation_id, text))
        if self.during_turn:
            self.during_turn()
        if self.error:
            raise self.error
        return self.replies.pop(0)


@pytest.fixture
def setup():
    store, trufar = MemoryStore(), FakeTruFar()
    app = create_app(make_config(), store=store, trufar=trufar)
    return app.test_client(), store, trufar


def start(client, trufar, greeting="Wake me again and I bite. Pick a topic."):
    trufar.replies.append(greeting)
    response = client.post("/v1/chat/sessions", json={"client": "tomodoom", "tools": []},
                           headers=AUTH)
    assert response.status_code == 200, response.get_json()
    return response.get_json()


def send(client, session_id, text):
    return client.post("/v1/chat/sessions/%s/messages" % session_id, json={"text": text},
                       headers=AUTH)


def test_health_reports_configuration():
    client = create_app(make_config(trufar_cookie=""), store=MemoryStore(),
                        trufar=FakeTruFar()).test_client()
    body = client.get("/").get_json()
    assert body["ok"] is False and body["missing"] == ["TRUFAR_COOKIE"]
    response = client.post("/v1/chat/sessions", headers=AUTH)
    assert response.status_code == 503
    assert "TRUFAR_COOKIE" in response.get_json()["error"]["message"]


def test_requires_backend_token(setup):
    client, _, _ = setup
    assert client.post("/v1/chat/sessions").status_code == 401
    assert client.post("/v1/chat/sessions",
                       headers={"Authorization": "Bearer nope"}).status_code == 401


def test_create_session_sends_hidden_start_turn(setup):
    client, _, trufar = setup
    body = start(client, trufar)
    assert body["session_id"]
    assert body["events"] == [{"type": "message",
                               "text": "Wake me again and I bite. Pick a topic."}]
    assert trufar.sent == [("13549", "__START__")]


def test_marker_in_greeting_never_awards(setup):
    client, _, trufar = setup
    body = start(client, trufar, greeting="Hello. [[CORRECT]]")
    assert body["events"][0] == {"type": "message", "text": "Hello."}


def test_plain_reply_is_a_message(setup):
    client, _, trufar = setup
    session_id = start(client, trufar)["session_id"]
    trufar.replies.append("History. Who was the first Roman emperor?")
    body = send(client, session_id, "history").get_json()
    assert body == {"events": [{"type": "message",
                                "text": "History. Who was the first Roman emperor?"}]}


def test_correct_marker_awards_ten_hp(setup):
    client, store, trufar = setup
    session_id = start(client, trufar)["session_id"]
    trufar.replies.append("Augustus. Even a clock is right twice a day. [[CORRECT]]")
    event = send(client, session_id, "Augustus").get_json()["events"][0]
    assert event == {"type": "quiz_evaluated", "quiz_id": "trufar:%s:2" % session_id,
                     "passed": True, "hp_reward": 10,
                     "feedback": "Augustus. Even a clock is right twice a day."}
    assert store.turns[(session_id, 2)]["reason"] == "marker"


def test_wrong_marker_is_a_plain_message(setup):
    client, _, trufar = setup
    session_id = start(client, trufar)["session_id"]
    trufar.replies.append("Nero? Pathetic. [[WRONG]]")
    assert send(client, session_id, "Nero").get_json()["events"][0] == \
        {"type": "message", "text": "Nero? Pathetic."}


def test_mcp_award_during_turn_wins_over_markers(setup):
    client, store, trufar = setup
    session_id = start(client, trufar)["session_id"]
    trufar.during_turn = lambda: store.add_award(7, "roman history")
    trufar.replies.append("Fine. Correct.")
    event = send(client, session_id, "Augustus").get_json()["events"][0]
    assert event["type"] == "quiz_evaluated" and event["hp_reward"] == 7
    assert store.turns[(session_id, 2)]["reason"] == "mcp:roman history"


def test_quiz_ids_are_unique_per_turn(setup):
    client, _, trufar = setup
    session_id = start(client, trufar)["session_id"]
    trufar.replies += ["Right. [[CORRECT]]", "Right again. [[CORRECT]]"]
    first = send(client, session_id, "a").get_json()["events"][0]["quiz_id"]
    second = send(client, session_id, "b").get_json()["events"][0]["quiz_id"]
    assert first != second


def test_award_outside_a_turn_is_rejected(setup):
    _, store, _ = setup
    assert store.add_award(10, "late") is None


def test_lease_blocks_a_concurrent_turn(setup):
    client, store, trufar = setup
    first = start(client, trufar)["session_id"]
    second = start(client, trufar)["session_id"]
    store.begin_turn(first)  # another worker is mid-turn
    response = send(client, second, "hi")
    assert response.status_code == 409
    assert "busy" in response.get_json()["error"]["message"]


def test_lease_is_released_when_trufar_fails(setup):
    client, store, trufar = setup
    session_id = start(client, trufar)["session_id"]
    trufar.error = TruFarAuthError("TruFar login expired: refresh TRUFAR_COOKIE")
    response = send(client, session_id, "hi")
    assert response.status_code == 503
    assert response.get_json() == {"error": {"message":
                                             "TruFar login expired: refresh TRUFAR_COOKIE"}}
    assert store.lease is None


def test_unknown_or_closed_session_is_404(setup):
    client, _, trufar = setup
    assert send(client, "missing", "hi").status_code == 404
    session_id = start(client, trufar)["session_id"]
    assert client.delete("/v1/chat/sessions/" + session_id, headers=AUTH).status_code == 204
    assert send(client, session_id, "hi").status_code == 404


@pytest.mark.parametrize("body", [{}, {"text": "  "}, {"text": 5}, {"text": "__START__"},
                                  {"text": "x" * 4001}])
def test_bad_message_bodies_are_400(setup, body):
    client, _, trufar = setup
    session_id = start(client, trufar)["session_id"]
    assert client.post("/v1/chat/sessions/%s/messages" % session_id, json=body,
                       headers=AUTH).status_code == 400


def test_unused_endpoints_return_no_events(setup):
    client, _, trufar = setup
    session_id = start(client, trufar)["session_id"]
    for path in ("quiz-answers", "tool-results"):
        response = client.post("/v1/chat/sessions/%s/%s" % (session_id, path), json={},
                               headers=AUTH)
        assert response.get_json() == {"events": []}
