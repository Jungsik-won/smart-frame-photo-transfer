"""Exercise production queue/archive SQL with real SQLite and 200-photo batches."""
from pathlib import Path
import re
import sqlite3

source = (Path(__file__).resolve().parents[1] / 'sender/src/local/smartframe/sender/SenderDb.java').read_text(encoding='utf-8')
queries = dict(re.findall(r'static final String (\w+)="([^"]+)";', source))
db = sqlite3.connect(':memory:')
for sql in re.findall(r'd\.execSQL\("(CREATE TABLE [^"]+)"\)', source):
    db.execute(sql)
for i in range(200):
    h = f'{i:064x}'
    db.execute('INSERT INTO photos VALUES(?,?,?,?,?)', (h, f'{i}.jpg', '/private/' + h, 100, 10))
    db.execute('INSERT INTO receipts VALUES(?,?,?,?,?,?)', ('frameA', h, f'{i}.jpg', 'done', '', 20))
def pending(frame='frameA'):
    return db.execute(queries['PENDING_SQL'], (frame,)).fetchall()
def archived(frame='frameA'):
    return db.execute(queries['ARCHIVE_SQL'], (frame,)).fetchall()
assert not pending() and len(archived()) == 200
# Confirmed photos leave the queue; history is independent and stays intact.
for h, _ in archived():
    db.execute('DELETE FROM photos WHERE hash=?', (h,))
assert db.execute('SELECT COUNT(*) FROM photos').fetchone()[0] == 0
assert db.execute('SELECT COUNT(*) FROM receipts').fetchone()[0] == 200
# Selecting an old photo again must not inherit the old success before rechecking the frame.
h = f'{1:064x}'
db.execute('INSERT INTO photos VALUES(?,?,?,?,?)', (h, 'again.jpg', '/private/again', 100, 30))
assert len(pending()) == 1 and not archived()
db.execute("UPDATE receipts SET status='failed',updated=31 WHERE hash=?", (h,))
assert len(pending()) == 1 and not archived()
db.execute("UPDATE receipts SET status='done',updated=32 WHERE hash=?", (h,))
db.execute('INSERT INTO receipts VALUES(?,?,?,?,?,?)', ('frameB', h, 'again.jpg', 'failed', 'offline', 32))
assert not pending() and len(pending('frameB')) == 1 and not archived()
db.execute("UPDATE receipts SET status='done',updated=33 WHERE frame='frameB'")
assert len(archived()) == 1
db.execute("UPDATE receipts SET status='deleted',updated=34 WHERE frame='frameA' AND hash=?", (h,))
assert not pending(), 'remote deletion must not silently queue the photo again'
db.execute('UPDATE photos SET added=35 WHERE hash=?', (h,))
assert len(pending()) == 1 and not archived(), 'explicit reselection after deletion may resend'
# History beyond the previous 100-row cutoff remains reachable in bounded pages.
history_sql = re.search(r'rawQuery\("(SELECT name,status,updated FROM receipts[^"]+)"', source).group(1)
pages = [db.execute(history_sql, ('frameA', str(page * 50))).fetchall() for page in range(4)]
assert all(len(page) == 50 for page in pages)
assert len({row[0] for page in pages for row in page}) == 200
print('PASS: sender queue/archive/history scenarios (200 completed, retry, other-frame failure, history pagination)')
