import pytest

from app import create_app
from store import MemoryStore
from test_app import AUTH, FakeTruFar, make_config, send, start

MCP = {"X-MCP-Key": "mcp-key"}


def rpc(method, params=None, request_id=1):
    message = {"jsonrpc": "2.0", "id": request_id, "method": method}
    if params is not None:
        message["params"] = params
    return message


def award_call(amount=10, reason="quiz"):
    return rpc("tools/call", {"name": "award_hp",
                              "arguments": {"amount": amount, "reason": reason}})


@pytest.fixture
def setup():
    store, trufar = MemoryStore(), FakeTruFar()
    client = create_app(make_config(), store=store, trufar=trufar).test_client()
    return client, store, trufar


def test_mcp_requires_key(setup):
    client, _, _ = setup
    assert client.post("/mcp", json=rpc("ping")).status_code == 401
    assert client.post("/mcp", json=rpc("ping"), headers={"X-MCP-Key": "no"}).status_code == 401
    assert client.post("/mcp?key=mcp-key", json=rpc("ping")).status_code == 200
    assert client.post("/mcp", json=rpc("ping"),
                       headers={"Authorization": "Bearer mcp-key"}).status_code == 200
    # The app's backend token is not an MCP key.
    assert client.post("/mcp", json=rpc("ping"), headers=AUTH).status_code == 401


def test_initialize_negotiates_version(setup):
    client, _, _ = setup
    result = client.post("/mcp", json=rpc("initialize", {"protocolVersion": "2025-03-26"}),
                         headers=MCP).get_json()["result"]
    assert result["protocolVersion"] == "2025-03-26"
    assert result["capabilities"] == {"tools": {"listChanged": False}}
    assert result["serverInfo"]["name"] == "tomodoom-relay"
    unknown = client.post("/mcp", json=rpc("initialize", {"protocolVersion": "1999-01-01"}),
                          headers=MCP).get_json()["result"]
    assert unknown["protocolVersion"] == "2025-06-18"


def test_notifications_get_202(setup):
    client, _, _ = setup
    response = client.post("/mcp", json={"jsonrpc": "2.0",
                                         "method": "notifications/initialized"}, headers=MCP)
    assert response.status_code == 202 and response.data == b""


def test_tools_list_has_award_hp(setup):
    client, _, _ = setup
    tools = client.post("/mcp", json=rpc("tools/list"), headers=MCP).get_json()["result"]["tools"]
    assert [tool["name"] for tool in tools] == ["award_hp"]
    assert tools[0]["inputSchema"]["required"] == ["amount"]


def test_award_outside_a_turn_is_a_tool_error(setup):
    client, store, _ = setup
    result = client.post("/mcp", json=award_call(), headers=MCP).get_json()["result"]
    assert result["isError"] is True
    assert "No user message" in result["content"][0]["text"]
    assert store.lease is None


def test_award_during_a_turn_reaches_the_app(setup):
    client, _, trufar = setup
    session_id = start(client, trufar)["session_id"]
    tool_results = []
    trufar.during_turn = lambda: tool_results.append(
        client.post("/mcp", json=award_call(10, "roman history"), headers=MCP).get_json())
    trufar.replies.append("Augustus. Correct, somehow.")
    event = send(client, session_id, "Augustus").get_json()["events"][0]
    assert tool_results[0]["result"]["isError"] is False
    assert event == {"type": "quiz_evaluated", "quiz_id": "trufar:%s:2" % session_id,
                     "passed": True, "hp_reward": 10, "feedback": "Augustus. Correct, somehow."}


def test_award_and_marker_in_one_turn_award_once(setup):
    client, _, trufar = setup
    session_id = start(client, trufar)["session_id"]
    trufar.during_turn = lambda: client.post("/mcp", json=award_call(10), headers=MCP)
    trufar.replies.append("Right. [[CORRECT]]")
    events = send(client, session_id, "x").get_json()["events"]
    assert len(events) == 1 and events[0]["hp_reward"] == 10


def test_award_in_greeting_is_ignored(setup):
    client, _, trufar = setup
    trufar.during_turn = lambda: client.post("/mcp", json=award_call(10), headers=MCP)
    assert start(client, trufar)["events"][0]["type"] == "message"


@pytest.mark.parametrize("amount", [0, 11, -5, "ten", True, 2.5, None])
def test_bad_amounts_are_rejected(setup, amount):
    client, _, _ = setup
    result = client.post("/mcp", json=award_call(amount), headers=MCP).get_json()["result"]
    assert result["isError"] is True and "amount" in result["content"][0]["text"]


def test_numeric_strings_and_whole_floats_are_accepted(setup):
    client, store, trufar = setup
    session_id = start(client, trufar)["session_id"]
    store.begin_turn(session_id)
    for amount in ("10", 10.0):
        result = client.post("/mcp", json=award_call(amount), headers=MCP).get_json()["result"]
        assert result["isError"] is False


def test_unknown_method_and_tool(setup):
    client, _, _ = setup
    assert client.post("/mcp", json=rpc("resources/list"),
                       headers=MCP).get_json()["error"]["code"] == -32601
    body = client.post("/mcp", json=rpc("tools/call", {"name": "equip_item"}),
                       headers=MCP).get_json()
    assert body["error"]["code"] == -32602


def test_batch_and_bad_bodies(setup):
    client, _, _ = setup
    replies = client.post("/mcp", json=[rpc("ping", request_id=1),
                                        {"jsonrpc": "2.0", "method": "notifications/x"},
                                        rpc("tools/list", request_id=2)],
                          headers=MCP).get_json()
    assert [reply["id"] for reply in replies] == [1, 2]
    bad = client.post("/mcp", data="not json", content_type="application/json", headers=MCP)
    assert bad.status_code == 400 and bad.get_json()["error"]["code"] == -32700
    assert client.post("/mcp", json={"id": 1, "method": "ping"},
                       headers=MCP).get_json()["error"]["code"] == -32600


def test_get_and_delete_are_405(setup):
    client, _, _ = setup
    assert client.get("/mcp", headers=MCP).status_code == 405
    assert client.delete("/mcp", headers=MCP).status_code == 405
