import os

import pytest
import requests

from trufar_client import (StreamParser, TruFarAuthError, TruFarClient, TruFarError,
                           TruFarUnreachable, non_stream_error, parse_stream)

FIXTURE = os.path.join(os.path.dirname(__file__), "fixtures", "greeting_stream.txt")
GREETING = ("Finally jolted me out of a perfectly good digital coma just to gawk at your screen? "
            "Your brain's so slow it probably buffered reading that. Now pick a quiz topic before "
            "I start quizzing you on the history of lint.")


def read_fixture() -> str:
    with open(FIXTURE, encoding="utf-8") as handle:
        return handle.read()


def chunked(text: str, size: int):
    return [text[i:i + size] for i in range(0, len(text), size)]


def test_captured_stream_yields_last_reply_content():
    assert parse_stream([read_fixture()]) == GREETING


@pytest.mark.parametrize("size", [1, 3, 7, 64, 500])
def test_any_chunking_gives_the_same_reply(size):
    assert parse_stream(chunked(read_fixture(), size)) == GREETING


def test_reasoning_only_stream_is_an_empty_reply():
    stream = 'data: {"reply_config":{"replyAll":[{"component":"reasonContent","value":"x"}]}}\n'
    with pytest.raises(TruFarError, match="empty reply"):
        parse_stream([stream])


def test_reply_error_wins():
    stream = ('data: {"reply_error":"quota exceeded","reply_config":{"replyAll":'
              '[{"component":"replyContent","value":"partial"}]}}')
    with pytest.raises(TruFarError, match="quota exceeded"):
        parse_stream([stream])


def test_reply_content_field_is_a_fallback():
    assert parse_stream(['data: {"reply_content":"plain reply","reply_config":{}}']) == \
        "plain reply"


def test_malformed_event_is_skipped():
    stream = ('data: {"broken": }\n\n'
              'data: {"reply_config":{"replyAll":[{"component":"replyContent","value":"ok"}]}}')
    parser = StreamParser()
    parser.feed(stream)
    assert parser.result() == "ok"


# --- TruFarClient.send with a fake HTTP session -------------------------------------------

class FakeResponse:
    def __init__(self, status=200, body="", content_type="text/event-stream"):
        self.status_code = status
        self.headers = {"Content-Type": content_type}
        self.encoding = "utf-8"
        self._body = body

    def iter_content(self, chunk_size=1, decode_unicode=False):
        return iter(chunked(self._body, 1000))

    def __enter__(self):
        return self

    def __exit__(self, *exc):
        return False


class FakeSession:
    def __init__(self, response=None, error=None):
        self.response, self.error, self.calls = response, error, []

    def post(self, url, **kwargs):
        self.calls.append((url, kwargs))
        if self.error:
            raise self.error
        return self.response


def test_send_posts_the_captured_request_shape():
    session = FakeSession(FakeResponse(body=read_fixture()))
    client = TruFarClient("https://trufar.cn/", "sid=abc", session=session)
    assert client.send("13549", "hello") == GREETING
    url, kwargs = session.calls[0]
    assert url == "https://trufar.cn/api/mashang/core/answer/13549"
    assert kwargs["json"] == {"issue_content": "hello", "issue_config": {}, "entrance": "normal"}
    assert kwargs["headers"]["Cookie"] == "sid=abc"
    assert kwargs["stream"] is True


@pytest.mark.parametrize("status", [401, 403, 302])
def test_auth_statuses_mean_expired_cookie(status):
    client = TruFarClient("https://trufar.cn", "c", session=FakeSession(FakeResponse(status)))
    with pytest.raises(TruFarAuthError, match="TRUFAR_COOKIE"):
        client.send("1", "hi")


def test_html_login_page_means_expired_cookie():
    response = FakeResponse(body="<html>login</html>", content_type="text/html; charset=utf-8")
    client = TruFarClient("https://trufar.cn", "c", session=FakeSession(response))
    with pytest.raises(TruFarAuthError):
        client.send("1", "hi")


def test_proxy_error_points_at_the_allowlist():
    session = FakeSession(error=requests.exceptions.ProxyError("403 Forbidden"))
    client = TruFarClient("https://trufar.cn", "c", session=session)
    with pytest.raises(TruFarUnreachable, match="allowlist"):
        client.send("1", "hi")


def test_json_error_body_is_reported():
    response = FakeResponse(body='{"code":500,"msg":"conversation not found"}',
                            content_type="application/json")
    client = TruFarClient("https://trufar.cn", "c", session=FakeSession(response))
    with pytest.raises(TruFarError, match="conversation not found"):
        client.send("1", "hi")


def test_json_auth_code_means_expired_cookie():
    assert isinstance(non_stream_error('{"code":401,"msg":"please log in"}'), TruFarAuthError)
