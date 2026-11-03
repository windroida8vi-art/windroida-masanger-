"""Fresh-install admin account: default windroida / admin123456, env overrides, no second seeding, name is reserved. Run: python test_seed_admin.py"""
import os, sys, tempfile
from pathlib import Path
sys.path.insert(0, ".")
import common
tmp = Path(tempfile.mkdtemp()); common.DATA_DIR = tmp; common.UPLOADS_DIR = tmp/"u"; common.AVATARS_DIR = tmp/"a"; common.WALLPAPERS_DIR = tmp/"w"; common.ROOMAUDIO_DIR = tmp/"r"; common.FILES_DIR = tmp/"f"; (tmp/"f").mkdir()
import server as srv
from client_core import Client
res = []
def ok(n, c, x=""): res.append(bool(c)); print(("  ok   " if c else "  FAIL ") + n, "" if c else x)
S = srv.WindroidaServer("127.0.0.1", 0, db_path=tmp/"t.db", web_port=0); port = S.start()
ok("default admin name is windroida", srv.ADMIN_USER == "windroida")
ok("seed creates the account", S.seed_admin() is True)
ok("second seed does nothing", S.seed_admin() is False)
c = Client(); c.connect("127.0.0.1", port)
r = c.call("login", username="windroida", password="admin123456"); ok("login with the default password", r.get("ok"), r)
c2 = Client(); c2.connect("127.0.0.1", port)
ok("wrong password refused", not c2.call("login", username="windroida", password="nope-nope1").get("ok"))
c3 = Client(); c3.connect("127.0.0.1", port)
r = c3.call("register", username="windroida", password="Str0ng-pass1", display_name="x", sec_question="سوال تستی؟", sec_answer="x1")
ok("nobody can register the admin name", not r.get("ok"), r)
r = c.call("change_password", old_password="admin123456", new_password="N3w-secret-pass"); ok("admin can change the password", r.get("ok"), r)
c4 = Client(); c4.connect("127.0.0.1", port)
ok("old default no longer works", not c4.call("login", username="windroida", password="admin123456").get("ok"))
c5 = Client(); c5.connect("127.0.0.1", port)
ok("new password works", c5.call("login", username="windroida", password="N3w-secret-pass").get("ok"))
os._exit(0 if all(res) else 1)
