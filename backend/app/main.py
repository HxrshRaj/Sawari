import math
from contextlib import asynccontextmanager
from datetime import datetime, timedelta, timezone

from fastapi import Depends, FastAPI, HTTPException, Query
from sqlalchemy import select
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from .config import settings
from .db import SessionLocal, get_db
from .models import Booking, Car, User
from .schema import init_schema
from .schemas import BookingIn, BookingOut, CarOut, LoginIn, RegisterIn, TokenOut, UserOut
from .security import create_token, current_user, hash_password, verify_password
from .seed import seed_cars


@asynccontextmanager
async def lifespan(_: FastAPI):
    init_schema()
    with SessionLocal() as db:
        seed_cars(db)
    yield


app = FastAPI(
    title="Sawari API",
    version="1.0.0",
    description="Car rental backend for the Sawari Android app.",
    lifespan=lifespan,
)


@app.get("/health", tags=["meta"])
def health():
    return {"status": "ok"}


# ---------- auth ----------
@app.post("/auth/register", response_model=TokenOut, status_code=201, tags=["auth"])
def register(body: RegisterIn, db: Session = Depends(get_db)):
    email = body.email.lower()
    if db.scalar(select(User).where(User.email == email)):
        raise HTTPException(409, "Email already registered")
    user = User(name=body.name.strip(), email=email, password_hash=hash_password(body.password))
    db.add(user)
    try:
        db.commit()
    except IntegrityError:  # lost a race on the unique email index
        db.rollback()
        raise HTTPException(409, "Email already registered")
    return TokenOut(access_token=create_token(user.id), user=UserOut.model_validate(user))


@app.post("/auth/login", response_model=TokenOut, tags=["auth"])
def login(body: LoginIn, db: Session = Depends(get_db)):
    user = db.scalar(select(User).where(User.email == body.email.lower()))
    if user is None or not verify_password(body.password, user.password_hash):
        raise HTTPException(401, "Incorrect email or password")
    return TokenOut(access_token=create_token(user.id), user=UserOut.model_validate(user))


# ---------- cars ----------
@app.get("/cities", response_model=list[str], tags=["cars"])
def cities(db: Session = Depends(get_db)):
    return list(db.scalars(select(Car.city).distinct().order_by(Car.city)))


@app.get("/cars", response_model=list[CarOut], tags=["cars"])
def list_cars(
    city: str | None = None,
    fuel: str | None = None,
    q: str | None = Query(None, description="substring match on car name"),
    db: Session = Depends(get_db),
):
    stmt = select(Car).order_by(Car.id)
    if city:
        stmt = stmt.where(Car.city.ilike(city))
    if fuel:
        stmt = stmt.where(Car.fuel.ilike(fuel))
    if q:
        stmt = stmt.where(Car.name.ilike(f"%{q}%"))
    return list(db.scalars(stmt))


@app.get("/cars/{car_id}", response_model=CarOut, tags=["cars"])
def get_car(car_id: int, db: Session = Depends(get_db)):
    car = db.get(Car, car_id)
    if car is None:
        raise HTTPException(404, "Car not found")
    return car


@app.get("/cars/{car_id}/busy", tags=["cars"], summary="Upcoming busy slots for a car (no user info)")
def car_busy_slots(car_id: int, db: Session = Depends(get_db)):
    rows = db.execute(
        select(Booking.start_time, Booking.end_time)
        .where(Booking.car_id == car_id, Booking.status == "confirmed", Booking.end_time > datetime.now(timezone.utc))
        .order_by(Booking.start_time)
    ).all()
    return [{"start_time": s, "end_time": e} for s, e in rows]


# ---------- bookings ----------
def _aware(dt: datetime) -> datetime:
    return dt if dt.tzinfo else dt.replace(tzinfo=timezone.utc)


@app.post("/bookings", response_model=BookingOut, status_code=201, tags=["bookings"])
def create_booking(body: BookingIn, user: User = Depends(current_user), db: Session = Depends(get_db)):
    start, end = _aware(body.start_time), _aware(body.end_time)
    if end <= start:
        raise HTTPException(422, "end_time must be after start_time")
    if start < datetime.now(timezone.utc) - timedelta(minutes=5):
        raise HTTPException(422, "start_time is in the past")
    if end - start > timedelta(hours=settings.max_booking_hours):
        raise HTTPException(422, f"Bookings are limited to {settings.max_booking_hours} hours")
    car = db.get(Car, body.car_id)
    if car is None:
        raise HTTPException(404, "Car not found")
    hours = math.ceil((end - start).total_seconds() / 3600)
    booking = Booking(
        user_id=user.id, car_id=car.id, start_time=start, end_time=end,
        total_price=hours * car.price_per_hour, status="confirmed",
    )
    db.add(booking)
    try:
        # No SELECT-then-INSERT: the exclusion constraint rejects overlaps atomically.
        db.commit()
    except IntegrityError as e:
        db.rollback()
        if "bookings_no_overlap" in str(e.orig):
            raise HTTPException(409, "This car is already booked for part of that time range")
        raise
    db.refresh(booking)
    return booking


@app.get("/bookings", response_model=list[BookingOut], tags=["bookings"])
def my_bookings(user: User = Depends(current_user), db: Session = Depends(get_db)):
    return list(db.scalars(select(Booking).where(Booking.user_id == user.id).order_by(Booking.start_time.desc())))


@app.post("/bookings/{booking_id}/cancel", response_model=BookingOut, tags=["bookings"])
def cancel_booking(booking_id: int, user: User = Depends(current_user), db: Session = Depends(get_db)):
    booking = db.get(Booking, booking_id)
    if booking is None or booking.user_id != user.id:
        raise HTTPException(404, "Booking not found")
    if booking.status == "cancelled":
        return booking  # idempotent
    booking.status = "cancelled"
    db.commit()
    db.refresh(booking)
    return booking
