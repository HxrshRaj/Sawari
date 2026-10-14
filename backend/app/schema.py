"""Schema bootstrap, including the exclusion constraint that makes double-booking impossible."""
from sqlalchemy import text

from .db import Base, engine
from . import models  # noqa: F401  (register tables)

EXCLUSION_DDL = """
ALTER TABLE bookings ADD CONSTRAINT bookings_no_overlap
EXCLUDE USING gist (car_id WITH =, tstzrange(start_time, end_time, '[)') WITH &&)
WHERE (status = 'confirmed')
"""


def init_schema() -> None:
    with engine.begin() as conn:
        conn.execute(text("CREATE EXTENSION IF NOT EXISTS btree_gist"))
        # serialize concurrent bootstraps (several workers starting at once)
        conn.execute(text("SELECT pg_advisory_xact_lock(727274)"))
        Base.metadata.create_all(conn)
        exists = conn.execute(
            text("SELECT 1 FROM pg_constraint WHERE conname = 'bookings_no_overlap'")
        ).first()
        if not exists:
            conn.execute(text(EXCLUSION_DDL))
