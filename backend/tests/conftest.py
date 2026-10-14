import os

os.environ.setdefault("DATABASE_URL", "postgresql+psycopg://sawari:sawari@localhost:25432/sawari_test")

import pytest
from fastapi.testclient import TestClient
from sqlalchemy import text

from app.db import engine
from app.main import app


@pytest.fixture(scope="session")
def client():
    with TestClient(app) as c:  # runs lifespan: schema + seed
        yield c


@pytest.fixture(autouse=True)
def clean(client):
    with engine.begin() as conn:
        conn.execute(text("TRUNCATE bookings, users RESTART IDENTITY CASCADE"))
    yield


@pytest.fixture
def make_user(client):
    def _make(email="a@example.com", password="secret12", name="Asha"):
        r = client.post("/auth/register", json={"name": name, "email": email, "password": password})
        assert r.status_code == 201, r.text
        return {"Authorization": f"Bearer {r.json()['access_token']}"}

    return _make
