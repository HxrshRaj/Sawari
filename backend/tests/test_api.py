import threading
from datetime import datetime, timedelta, timezone

from sqlalchemy import text

from app.db import engine


def slot(offset_h=2, hours=3):
    start = (datetime.now(timezone.utc) + timedelta(hours=offset_h)).replace(microsecond=0)
    return start, start + timedelta(hours=hours)


def book(client, headers, car_id, start, end):
    return client.post(
        "/bookings",
        headers=headers,
        json={"car_id": car_id, "start_time": start.isoformat(), "end_time": end.isoformat()},
    )


def test_seed_has_enough_cars_in_three_cities(client):
    cars = client.get("/cars").json()
    assert len(cars) >= 20
    assert {c["city"] for c in cars} == {"Bengaluru", "Mumbai", "Delhi"}


def test_car_filters(client):
    assert all(c["city"] == "Mumbai" for c in client.get("/cars", params={"city": "mumbai"}).json())
    assert all(c["fuel"] == "Electric" for c in client.get("/cars", params={"fuel": "Electric"}).json())
    assert all("nexon" in c["name"].lower() for c in client.get("/cars", params={"q": "nexon"}).json())
    assert client.get("/cars/999999").status_code == 404


def test_register_login_and_duplicate(client):
    body = {"name": "A", "email": "Dup@Example.com", "password": "secret12"}
    assert client.post("/auth/register", json=body).status_code == 201
    assert client.post("/auth/register", json=body).status_code == 409
    assert client.post("/auth/login", json={"email": "dup@example.com", "password": "secret12"}).status_code == 200
    assert client.post("/auth/login", json={"email": "dup@example.com", "password": "wrong-pw"}).status_code == 401


def test_bookings_require_auth(client):
    assert client.get("/bookings").status_code == 401
    assert client.get("/bookings", headers={"Authorization": "Bearer junk"}).status_code == 401


def test_create_list_cancel(client, make_user):
    h = make_user()
    start, end = slot()
    r = book(client, h, 1, start, end)
    assert r.status_code == 201
    b = r.json()
    assert b["status"] == "confirmed" and b["total_price"] == 3 * b["car"]["price_per_hour"]
    assert [x["id"] for x in client.get("/bookings", headers=h).json()] == [b["id"]]
    c = client.post(f"/bookings/{b['id']}/cancel", headers=h)
    assert c.status_code == 200 and c.json()["status"] == "cancelled"


def test_overlap_rejected_but_adjacent_and_other_car_allowed(client, make_user):
    h = make_user()
    start, end = slot()
    assert book(client, h, 1, start, end).status_code == 201
    assert book(client, h, 1, start + timedelta(hours=1), end + timedelta(hours=1)).status_code == 409
    assert book(client, h, 1, start - timedelta(hours=1), start + timedelta(minutes=30)).status_code == 409
    assert book(client, h, 1, end, end + timedelta(hours=1)).status_code == 201  # back-to-back is fine
    assert book(client, h, 2, start, end).status_code == 201  # different car


def test_cancel_frees_the_slot(client, make_user):
    h = make_user()
    start, end = slot()
    b = book(client, h, 1, start, end).json()
    client.post(f"/bookings/{b['id']}/cancel", headers=h)
    assert book(client, h, 1, start, end).status_code == 201


def test_cannot_cancel_someone_elses_booking(client, make_user):
    h1, h2 = make_user("a@example.com"), make_user("b@example.com")
    start, end = slot()
    b = book(client, h1, 1, start, end).json()
    assert client.post(f"/bookings/{b['id']}/cancel", headers=h2).status_code == 404
    assert client.get("/bookings", headers=h2).json() == []


def test_validation(client, make_user):
    h = make_user()
    start, end = slot()
    assert book(client, h, 1, end, start).status_code == 422
    assert book(client, h, 1, start - timedelta(days=1), start - timedelta(hours=20)).status_code == 422
    assert book(client, h, 1, start, start + timedelta(hours=100)).status_code == 422
    assert book(client, h, 99999, start, end).status_code == 404


def test_concurrent_double_booking_only_one_wins(client, make_user):
    """20 users race for the same car and slot: exactly one 201, the rest 409."""
    tokens = [make_user(f"u{i}@example.com") for i in range(20)]
    start, end = slot(5, 4)
    barrier = threading.Barrier(len(tokens))
    codes = []

    def worker(h):
        barrier.wait()
        codes.append(book(client, h, 3, start, end).status_code)

    threads = [threading.Thread(target=worker, args=(h,)) for h in tokens]
    [t.start() for t in threads]
    [t.join() for t in threads]
    assert sorted(codes) == [201] + [409] * 19
    with engine.begin() as conn:
        n = conn.execute(text("SELECT count(*) FROM bookings WHERE car_id=3 AND status='confirmed'")).scalar()
    assert n == 1
