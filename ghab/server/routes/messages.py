"""ارسال پست به آدم‌ها، صندوق پیام و چت."""
import time

from flask import Blueprint, jsonify, request, session

import os

from .. import storage, vis
from ..db import q, q1, run
from ..ranking import affinity, invalidate, marks
from ..security import auth, cur_user, err, too_many
from ..serializers import msg_ser, uj

bp = Blueprint('messages', __name__)
_typing = {}  # {(فرستنده، گیرنده): زمان آخرین تایپ}
EMOJI = ('❤️', '😂', '😮', '😢', '👍', '🔥')


@bp.get('/api/share/suggest')
@auth
def share_suggest():
    me_ = session['uid']
    s = (request.args.get('q') or '').strip().lower()
    aff = affinity(me_)
    # فقط کسانی که من فالوشان کرده‌ام
    ids = [r['followee'] for r in q('select followee from follows where follower=?', me_)]
    us = [u for u in (q1('select * from users where id=?', i) for i in ids) if u and u['id'] != me_]
    if s:
        us = [u for u in us if s in (u['username'] or '').lower() or s in (u['name'] or '').lower()]
    us = sorted([u for u in us if u['id'] not in vis.blocked(me_)], key=lambda u: -aff.get(u['id'], 0))
    return jsonify([uj(u) for u in us])


def put_msg(frm, to, text, pid):
    run('insert into messages(sender,receiver,text,post_id,created) values(?,?,?,?,?)', frm, to, text, pid, time.time())


@bp.post('/api/messages/send')
@auth
def msg_send():
    me_ = session['uid']
    d = request.get_json(silent=True) or {}
    names = [str(n).lower() for n in (d.get('to') or [])][:20]
    text = (d.get('text') or '').strip()[:1000]
    try:
        pid = int(d.get('post_id')) if d.get('post_id') else None
    except (TypeError, ValueError):
        pid = None
    ids = [r['id'] for r in q(f'select id from users where username in ({marks(len(names))}) and id!=?', *names, me_)] if names else []
    ids = [i for i in ids if i not in vis.blocked(me_)]
    if not ids:
        return err('گیرنده‌ای انتخاب نشده')
    if pid and not q1('select 1 from posts where id=?', pid):
        return err('پست پیدا نشد', 404)
    if not text and not pid:
        return err('پیام خالی است')
    invalidate(me_)
    for i in ids:
        if pid:
            put_msg(me_, i, '', pid)
            if text:
                put_msg(me_, i, text, None)
        else:
            put_msg(me_, i, text, None)
    return jsonify(sent=len(ids))


@bp.get('/api/inbox')
@auth
def inbox():
    me_ = session['uid']
    last, unread = {}, {}
    for m in q('select * from messages where sender=? or receiver=? order by id desc limit 2500', me_, me_):
        peer = m['receiver'] if m['sender'] == me_ else m['sender']
        last.setdefault(peer, m)
        if m['receiver'] == me_ and not m['seen']:
            unread[peer] = unread.get(peer, 0) + 1
    out = []
    for peer, m in last.items():
        u = q1('select * from users where id=?', peer)
        if u and peer not in vis.blocked(me_):
            out.append(dict(user=uj(u), last=msg_ser(m), unread=unread.get(peer, 0)))
    return jsonify(out)


@bp.get('/api/unread')
@auth
def unread():
    return jsonify(unread=q1('select count(*) c from messages where receiver=? and seen=0 and deleted=0', session['uid'])['c'],
                   activity=q1('select count(*) c from notifications where user_id=? and seen=0', session['uid'])['c'])


@bp.get('/api/chat/<username>')
@auth
def chat_get(username):
    me_ = session['uid']
    u = q1('select * from users where username=?', username.lower())
    if not u or u['id'] == me_ or u['id'] in vis.blocked(me_):
        return err('گفتگو امکان‌پذیر نیست', 404)
    after = request.args.get('after', type=int, default=0)
    pair = '((sender=? and receiver=?) or (sender=? and receiver=?))'
    if after:
        rows = q(f'select * from messages where id>? and {pair} order by id limit 300', after, me_, u['id'], u['id'], me_)
    else:
        rows = list(reversed(q(f'select * from messages where {pair} order by id desc limit 100', me_, u['id'], u['id'], me_)))
    run('update messages set seen=1 where sender=? and receiver=? and seen=0', u['id'], me_)
    su = q1('select max(id) m from messages where sender=? and receiver=? and seen=1', me_, u['id'])['m'] or 0
    removed = [r['id'] for r in q(f'select id from messages where deleted=1 and {pair} order by id desc limit 100', me_, u['id'], u['id'], me_)]
    recent = [r['id'] for r in q(f'select id from messages where {pair} order by id desc limit 100', me_, u['id'], u['id'], me_)]
    reacts = {}
    if recent:
        for r in q(f'select message_id, emoji from message_reactions where message_id in ({",".join("?" * len(recent))})', *recent):
            reacts.setdefault(r['message_id'], []).append(r['emoji'])
    typing = time.time() - _typing.get((u['id'], me_), 0) < 4
    return jsonify(user=uj(u), messages=[dict(msg_ser(m), reacts=reacts.get(m['id'], [])) for m in rows], seen_upto=su,
                   removed=removed, reacts=reacts, typing=typing)


@bp.post('/api/chat/<username>')
@auth
def chat_post(username):
    me_ = session['uid']
    u = q1('select * from users where username=?', username.lower())
    t = ((request.get_json(silent=True) or {}).get('text') or '').strip()[:1000]
    if not u or u['id'] == me_ or u['id'] in vis.blocked(me_):
        return err('گفتگو امکان‌پذیر نیست', 404)
    if not t:
        return err('پیام خالی است')
    put_msg(me_, u['id'], t, None)
    invalidate(me_)
    return jsonify(ok=True)


@bp.delete('/api/messages/<int:mid>')
@auth
def unsend(mid):
    """حذف پیام برای هر دو طرف (فقط فرستنده)."""
    m = q1('select media from messages where id=? and sender=?', mid, session['uid'])
    n = run('update messages set deleted=1, text=\'\', post_id=null, media=null where id=? and sender=?', mid, session['uid']).rowcount
    if m and m['media']:
        storage.rm_file(m['media'])
    return jsonify(ok=True) if n else err('پیام پیدا نشد', 404)


def _peer(username):
    u = q1('select * from users where username=?', username.lower())
    me_ = session['uid']
    return None if not u or u['id'] == me_ or u['id'] in vis.blocked(me_) else u


@bp.post('/api/chat/<username>/typing')
@auth
def typing(username):
    u = _peer(username)
    if u:
        _typing[(session['uid'], u['id'])] = time.time()
        if len(_typing) > 5000:
            _typing.clear()
    return jsonify(ok=True)


@bp.post('/api/chat/<username>/media')
@auth
def chat_media(username):
    """عکس یا پیام صوتی در چت."""
    u, me_ = _peer(username), session['uid']
    if not u:
        return err('گفتگو امکان‌پذیر نیست', 404)
    if too_many(('dmm', me_), 40, 60):
        return err('تعداد زیاد است؛ کمی صبر کن', 429)
    f, kind, name = request.files.get('file'), request.form.get('kind'), cur_user()['username']
    if not f or not f.filename:
        return err('فایلی نیامد')
    if kind == 'voice':
        rel = storage.save_audio(f, name)
    else:
        r = storage.save_file(f, name, 'dm', kinds=('image',), thumb=False, side=1080)
        rel, kind = (r[0] if r else None), 'image'
    if not rel:
        return err('فایل نامعتبر یا بیش از حد بزرگ است')
    run('insert into messages(sender,receiver,text,post_id,created,kind,media) values(?,?,?,?,?,?,?)',
        me_, u['id'], '', None, time.time(), kind, rel)
    return jsonify(ok=True)


@bp.post('/api/messages/<int:mid>/react')
@auth
def react(mid):
    emoji, me_ = (request.get_json(silent=True) or {}).get('emoji'), session['uid']
    m = q1('select * from messages where id=? and deleted=0 and (sender=? or receiver=?)', mid, me_, me_)
    if not m or emoji not in EMOJI:
        return err('پیام پیدا نشد', 404)
    cur = q1('select emoji from message_reactions where message_id=? and user_id=?', mid, me_)
    if cur and cur['emoji'] == emoji:
        run('delete from message_reactions where message_id=? and user_id=?', mid, me_)
    else:
        run('insert or replace into message_reactions values(?,?,?)', mid, me_, emoji)
    return jsonify(reacts=[r['emoji'] for r in q('select emoji from message_reactions where message_id=?', mid)])
