from sqlalchemy import func, select
from sqlalchemy.orm import Session

from .models import Car

_PHOTOS = [
    "1494976388531-d1058494cdd8", "1503376780353-7e6692767b70", "1552519507-da3b142c6e3d",
    "1583121274602-3e2820c69888", "1533473359331-0135ef1b58bf", "1549317661-bd32c8ce0db2",
    "1542362567-b07e54358753", "1555215695-3004980ad54e", "1580273916550-e323be2ae537",
]


def _img(i: int) -> str:
    return f"https://images.unsplash.com/photo-{_PHOTOS[i % len(_PHOTOS)]}?w=800&q=70&auto=format&fit=crop"


# (name, price_per_hour in INR, seats, fuel)
_CATALOG = {
    "Bengaluru": [
        ("Maruti Swift", 180, 5, "Petrol"), ("Hyundai i20", 200, 5, "Petrol"),
        ("Tata Nexon EV", 320, 5, "Electric"), ("Mahindra XUV700", 450, 7, "Diesel"),
        ("Toyota Innova Crysta", 500, 7, "Diesel"), ("Honda City", 280, 5, "Petrol"),
        ("MG ZS EV", 360, 5, "Electric"), ("Kia Seltos", 340, 5, "Diesel"),
    ],
    "Mumbai": [
        ("Maruti Baleno", 190, 5, "Petrol"), ("Hyundai Creta", 330, 5, "Diesel"),
        ("Tata Punch", 170, 5, "Petrol"), ("Tata Tigor EV", 260, 5, "Electric"),
        ("Skoda Slavia", 360, 5, "Petrol"), ("Mahindra Thar", 420, 4, "Diesel"),
        ("Toyota Fortuner", 700, 7, "Diesel"), ("Maruti Ertiga", 300, 7, "CNG"),
    ],
    "Delhi": [
        ("Maruti Dzire", 170, 5, "CNG"), ("Hyundai Venue", 250, 5, "Petrol"),
        ("Tata Harrier", 420, 5, "Diesel"), ("Mahindra Scorpio-N", 480, 7, "Diesel"),
        ("Volkswagen Virtus", 350, 5, "Petrol"), ("Kia Carens", 380, 7, "Petrol"),
        ("BYD Atto 3", 520, 5, "Electric"), ("Renault Kiger", 210, 5, "Petrol"),
    ],
}


def seed_cars(db: Session) -> int:
    if db.scalar(select(func.count()).select_from(Car)):
        return 0
    n = 0
    for city, cars in _CATALOG.items():
        for name, price, seats, fuel in cars:
            db.add(Car(name=name, city=city, price_per_hour=price, seats=seats, fuel=fuel, image_url=_img(n)))
            n += 1
    db.commit()
    return n
