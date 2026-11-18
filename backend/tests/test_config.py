import pytest

from config import Config

ENV_NAMES = ("TOMODOOM_STORE", "GOOGLE_APPLICATION_CREDENTIALS", "K_SERVICE", "BACKEND_TOKEN",
             "MCP_KEY", "TRUFAR_CONVERSATION_ID", "TRUFAR_COOKIE", "TRUFAR_BASE_URL")


@pytest.fixture(autouse=True)
def clean_env(monkeypatch):
    for name in ENV_NAMES:
        monkeypatch.delenv(name, raising=False)


def test_defaults_to_memory_store_and_trufar_cn():
    config = Config.from_env()
    assert config.store == "memory"
    assert config.trufar_base_url == "https://trufar.cn"
    assert config.missing() == ["BACKEND_TOKEN", "MCP_KEY", "TRUFAR_CONVERSATION_ID",
                                "TRUFAR_COOKIE"]


def test_cloud_run_uses_firestore(monkeypatch):
    monkeypatch.setenv("K_SERVICE", "tomodoom-relay")
    assert Config.from_env().store == "firestore"


def test_key_file_uses_firestore(monkeypatch):
    monkeypatch.setenv("GOOGLE_APPLICATION_CREDENTIALS", "/secrets/key.json")
    assert Config.from_env().store == "firestore"


def test_explicit_store_wins(monkeypatch):
    monkeypatch.setenv("K_SERVICE", "tomodoom-relay")
    monkeypatch.setenv("TOMODOOM_STORE", "memory")
    assert Config.from_env().store == "memory"


def test_values_are_trimmed(monkeypatch):
    monkeypatch.setenv("TRUFAR_COOKIE", "  sid=abc \n")
    monkeypatch.setenv("TRUFAR_BASE_URL", "https://trufar.cn/")
    config = Config.from_env()
    assert config.trufar_cookie == "sid=abc"
    assert config.trufar_base_url == "https://trufar.cn"
