import json
import sys
import types

import pytest

import store
from app import create_app
from config import Config

KEY = {"type": "service_account", "project_id": "tomodoom", "client_email": "a@b.iam",
       "private_key": "-----BEGIN PRIVATE KEY-----\nabc\n-----END PRIVATE KEY-----\n"}


def test_parse_service_account_accepts_a_key_file():
    assert store.parse_service_account(json.dumps(KEY)) == KEY


@pytest.mark.parametrize("value", ["not json", "[]", json.dumps({"type": "user"}),
                                   json.dumps({"type": "service_account"})])
def test_parse_service_account_rejects_other_values(value):
    with pytest.raises(ValueError) as error:
        store.parse_service_account(value)
    assert "FIREBASE_SERVICE_ACCOUNT" in str(error.value)
    assert value not in str(error.value) or len(value) < 3


def test_errors_never_echo_the_secret():
    secret = '{"type": "service_account", "private_key": "TOPSECRET"'  # truncated paste
    with pytest.raises(ValueError) as error:
        store.parse_service_account(secret)
    assert "TOPSECRET" not in str(error.value)
    assert error.value.__cause__ is None


@pytest.fixture
def fake_firebase(monkeypatch):
    """A stand-in firebase_admin package that records how it was initialised."""
    calls = {}
    package = types.ModuleType("firebase_admin")
    package._apps = {}
    credentials = types.ModuleType("firebase_admin.credentials")
    firestore = types.ModuleType("firebase_admin.firestore")

    credentials.Certificate = lambda info: ("cert", info)

    def initialize_app(credential=None):
        calls["credential"] = credential
        package._apps["[DEFAULT]"] = object()

    class FakeDb:
        def collection(self, name):
            return types.SimpleNamespace(document=lambda doc: (name, doc))

    package.initialize_app = initialize_app
    package.credentials = credentials
    package.firestore = firestore
    firestore.client = FakeDb
    firestore.transactional = lambda fn: fn
    monkeypatch.setitem(sys.modules, "firebase_admin", package)
    monkeypatch.setitem(sys.modules, "firebase_admin.credentials", credentials)
    monkeypatch.setitem(sys.modules, "firebase_admin.firestore", firestore)
    return calls


def test_firestore_store_uses_env_json_credentials(fake_firebase):
    store.FirestoreStore(json.dumps(KEY))
    assert fake_firebase["credential"] == ("cert", KEY)


def test_firestore_store_falls_back_to_default_credentials(fake_firebase):
    store.FirestoreStore()
    assert fake_firebase["credential"] is None


def test_bad_service_account_is_reported_not_crashed():
    config = Config(backend_token="t", mcp_key="m", trufar_base_url="https://trufar.cn",
                    trufar_conversation_id="1", trufar_cookie="c", store="firestore",
                    firebase_service_account="{oops")
    client = create_app(config).test_client()
    body = client.get("/").get_json()
    assert body["ok"] is False
    assert "FIREBASE_SERVICE_ACCOUNT is not valid JSON" in body["missing"][0]
    assert "{oops" not in json.dumps(body)


def test_config_repr_hides_secrets():
    config = Config(backend_token="tok-123", mcp_key="mcp-456", trufar_base_url="https://x",
                    trufar_conversation_id="1", trufar_cookie="sid=789", store="memory",
                    firebase_service_account="key-000")
    text = repr(config)
    for secret in ("tok-123", "mcp-456", "sid=789", "key-000"):
        assert secret not in text
