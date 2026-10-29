"""验证仓库实际 SQL 的抢占、幂等、取消与过期恢复。"""
import json
import re
import sqlite3
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[4]
JAVA = (ROOT / 'src/main/java/com/example/cursorquitterweb/musicmv/aiphoto/AiPhotoJobRepository.java').read_text()
SQL = []
for match in re.finditer(r'db\.query\(\s*((?:"(?:[^"\\]|\\.)*"\s*(?:\+\s*)?)+)', JAVA):
    SQL.append(''.join(json.loads(s) for s in re.findall(r'"(?:[^"\\]|\\.)*"', match.group(1))))

def query(prefix):
    return next(s for s in SQL if s.startswith(prefix))

class JobSqlTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(':memory:')
        self.db.row_factory = sqlite3.Row
        self.db.executescript((ROOT / 'src/main/resources/db/migrations/music-mv-v18-ai-photo-jobs.sql').read_text())
        self.insert = query('INSERT')
        self.claim = query('UPDATE music_mv_ai_photo_jobs SET status=CASE')
        self.update = query('UPDATE music_mv_ai_photo_jobs SET status=?')
        self.db.execute(self.insert, ('job','owner','request','fingerprint','template','version','photo_1','source','hash','{}','https://site'))
    def test_request_unique(self):
        self.db.execute(self.insert, ('second','owner','request','different','template','version','photo_1','new','hash','{}','https://site'))
        self.assertEqual(self.db.execute('SELECT COUNT(*) FROM music_mv_ai_photo_jobs').fetchone()[0],1)
    def test_one_worker_and_stale_lease(self):
        self.assertIsNotNone(self.db.execute(self.claim,('lease','job','queued')).fetchone())
        self.assertIsNone(self.db.execute(self.claim,('other','job','queued')).fetchone())
        self.db.execute(self.update,('ready',None,None,'result',None,'job','other'))
        self.assertEqual(self.db.execute('SELECT status FROM music_mv_ai_photo_jobs').fetchone()[0],'submitting')
    def test_cancel_prevents_late_write(self):
        self.db.execute(self.claim,('lease','job','queued')).fetchone()
        self.db.execute(query("UPDATE music_mv_ai_photo_jobs SET status='canceled'"),('owner','job'))
        self.db.execute(self.update,('ready',None,None,'result',None,'job','lease'))
        row=self.db.execute('SELECT status,result_asset_id FROM music_mv_ai_photo_jobs').fetchone()
        self.assertEqual(tuple(row),('canceled',None))
    def test_unknown_submit_is_never_requeued(self):
        self.db.execute(self.claim,('lease','job','queued')).fetchone()
        self.db.execute("UPDATE music_mv_ai_photo_jobs SET lease_until=datetime('now','-1 minute')")
        self.db.execute(query("UPDATE music_mv_ai_photo_jobs SET status='submission_unknown'"))
        self.assertEqual(self.db.execute('SELECT status FROM music_mv_ai_photo_jobs').fetchone()[0],'submission_unknown')
        self.assertEqual(len(self.db.execute(query('SELECT * FROM music_mv_ai_photo_jobs WHERE status IN')).fetchall()),0)
    def test_recover_poll_without_new_submission(self):
        self.db.execute("UPDATE music_mv_ai_photo_jobs SET status='generating',provider_task_id='remote',lease_until=datetime('now','-1 minute')")
        row=self.db.execute(self.claim,('recovered','job','generating')).fetchone()
        self.assertEqual(row['status'],'generating')
        self.assertEqual(row['provider_task_id'],'remote')

if __name__ == '__main__':
    unittest.main()
