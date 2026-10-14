from datetime import datetime

from sqlalchemy import CheckConstraint, DateTime, ForeignKey, Integer, String, func
from sqlalchemy.orm import Mapped, mapped_column, relationship

from .db import Base


class User(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(100))
    email: Mapped[str] = mapped_column(String(255), unique=True, index=True)
    password_hash: Mapped[str] = mapped_column(String(255))
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())


class Car(Base):
    __tablename__ = "cars"

    id: Mapped[int] = mapped_column(primary_key=True)
    name: Mapped[str] = mapped_column(String(120))
    city: Mapped[str] = mapped_column(String(60), index=True)
    price_per_hour: Mapped[int] = mapped_column(Integer)
    image_url: Mapped[str] = mapped_column(String(500))
    seats: Mapped[int] = mapped_column(Integer)
    fuel: Mapped[str] = mapped_column(String(20))


class Booking(Base):
    __tablename__ = "bookings"
    # The double-booking guarantee lives in the database, not in application code:
    # see EXCLUSION_DDL in app/schema.py (GiST exclusion constraint on car_id + time range).
    __table_args__ = (CheckConstraint("end_time > start_time", name="ck_booking_range"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    user_id: Mapped[int] = mapped_column(ForeignKey("users.id"), index=True)
    car_id: Mapped[int] = mapped_column(ForeignKey("cars.id"), index=True)
    start_time: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    end_time: Mapped[datetime] = mapped_column(DateTime(timezone=True))
    total_price: Mapped[int] = mapped_column(Integer)
    status: Mapped[str] = mapped_column(String(20), default="confirmed")
    created_at: Mapped[datetime] = mapped_column(DateTime(timezone=True), server_default=func.now())

    car: Mapped[Car] = relationship(lazy="joined")
