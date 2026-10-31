"""Execute PhotoStore's production SQL against an actual SQLite database."""
from pathlib import Path
import re
import sqlite3

source = (Path(__file__).resolve().parents[1] / "receiver/src/local/smartframe/receiver/PhotoStore.java").read_text(encoding="utf-8")
sql = re.findall(r'db\.execSQL\("([^"\n]+)"\)', source)
assert len(sql) == 4, "Review migration test when production migration changes"
create, rename, migrate, drop = sql
db = sqlite3.connect(":memory:")
db.execute("CREATE TABLE photos(hash TEXT PRIMARY KEY,uri TEXT NOT NULL,name TEXT,size INTEGER,saved INTEGER)")
old = ("a" * 64, "content://media/external/images/media/17", "가족.jpg", 1234, 99)
db.execute("INSERT INTO photos VALUES(?,?,?,?,?)", old)
db.commit()
# An interrupted migration must keep the old data intact.
try:
    with db:
        db.execute("BEGIN")
        db.execute(rename)
        db.execute(create)
        raise RuntimeError("simulated migration interruption")
except RuntimeError:
    pass
assert db.execute("SELECT * FROM photos").fetchone() == old
with db:
    db.execute("BEGIN")
    for statement in (rename, create, migrate, drop):
        db.execute(statement)
expected = (old[0], "external_primary", old[1].replace("/external/", "/external_primary/"), *old[2:])
assert db.execute("SELECT * FROM photos").fetchone() == expected
assert not db.execute("SELECT name FROM sqlite_master WHERE name='photos_v1'").fetchall()
usb = (old[0], "1234-abcd", "content://media/1234-abcd/images/media/2", *old[2:])
db.execute("INSERT INTO photos VALUES(?,?,?,?,?,?)", usb)
assert db.execute("SELECT COUNT(*) FROM photos").fetchone()[0] == 2
try:
    db.execute("INSERT INTO photos VALUES(?,?,?,?,?,?)", usb)
    raise AssertionError("duplicate in same volume accepted")
except sqlite3.IntegrityError:
    pass
db.execute("DELETE FROM photos WHERE hash=? AND volume=?", (old[0], "1234-abcd"))
assert db.execute("SELECT * FROM photos").fetchone() == expected
print("PASS: 6 storage migration/volume-isolation checks (production SQL)")
