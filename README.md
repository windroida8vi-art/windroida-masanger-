# Windroida · ویندروید

پیام‌رسان تلگرام‌مانند فارسی، فقط با پایتون (بدون وابستگی سنگین): سرور + رابط وب + اپ اندروید + دیتاشیت مدیریت (دسکتاپ).
به‌همراه **اتاق تماشا/گوش‌دادن همزمان**، **بازی‌ها** (شطرنج، مافیا، منچ)، نظرسنجی، تماس صوتی/تصویری، **فضای ابری** (Cloud Drive) و **قاب** (Ghab).

A Telegram-like Persian messenger in plain Python: server + web UI + Android app + desktop admin datasheet,
with watch/listen-together rooms, games (chess, mafia, ludo), polls, voice/video calls, a cloud drive and Ghab (SSO-integrated).

## شروع سریع / Quick start (Windows)
1. پایتون ۳.۱۰+ نصب کن.
2. `start.bat` رو اجرا کن (کتابخونه‌ها رو نصب می‌کنه، سرور https رو بالا میاره و مرورگر رو باز می‌کنه).
3. با حساب ادمین پیش‌فرض وارد شو و **همون لحظه رمزش رو عوض کن** (پایین ببین).

لینوکس/مک: `pip install -r requirements.txt` و بعد `python start.py` (یا فقط سرور: `python server.py --web`).

## حساب‌ها و رمزهای پیش‌فرض / Default accounts

| چی | پیش‌فرض | کجا ساخته می‌شه |
|---|---|---|
| حساب ادمین پیام‌رسان | نام کاربری `windroida` · رمز `admin123456` | بار اول که سرور بالا میاد، خودکار |
| رمز دیتاشیت (برنامه‌ی مدیریت دسکتاپ) | `admin123456` | داخل `datasheet.py` (فقط هش) |

> ⚠️ این رمزها عمومی هستن (تو همین README نوشته شدن). قبل از باز کردن سرور روی اینترنت حتماً عوضشون کن.
> تا وقتی رمز ادمین پیش‌فرض باشه، سرور موقع بالا اومدن توی کنسول هشدار می‌ده.

ادمین چه کاری می‌تونه بکنه: تنها کسیه که تو **کانال اطلاعیه‌ها** پیام می‌ذاره و اطلاعات کانال (عکس/اسم/توضیح) رو عوض می‌کنه.
نام `windroida` (و `admin`، `root`، `support` و…) برای ثبت‌نام کاربران عادی رزروه.

## چطور عوض کنم؟ و کی می‌تونه؟ / How to change them, and who can

| می‌خوای عوض کنی | چطور | چه کسی می‌تونه |
|---|---|---|
| **رمز ادمین** (بعد از ساخته شدن حساب) | وارد حساب ادمین شو ← پروفایل/تنظیمات ← «تغییر رمز» | خودِ ادمین (کسی که رمز فعلی رو بلده). بقیه‌ی کاربرا فقط رمز خودشون رو عوض می‌کنن. |
| **رمز اولیه‌ی ادمین** (قبل از اولین اجرا) | متغیر محیطی `WINDROIDA_ADMIN_PASSWORD` | صاحب سرور (کسی که به کامپیوتر/سرور دسترسی داره) |
| **نام حساب ادمین** | متغیر محیطی `WINDROIDA_ADMIN` (مثلاً `set WINDROIDA_ADMIN=myname`) | صاحب سرور؛ قبل از اولین اجرا تنظیمش کن. اگه حساب قبلاً ساخته شده، حساب جدید با اون نام ساخته می‌شه و قدیمی عادی می‌مونه. |
| **رمز دیتاشیت** | متغیر محیطی `WINDROIDA_DATASHEET_PASSWORD` (متن ساده) یا هش تازه جای `_PW_SALT/_PW_HASH` در `datasheet.py` | صاحب سرور |
| سایر نام‌ها/پورت‌ها | `WINDROIDA_DRIVE_PORT`، `WINDROIDA_GHAB_PORT`، `WINDROIDA_CERT_NAMES`، … | صاحب سرور |

مثال (ویندوز، قبل از اولین اجرا):
```bat
set WINDROIDA_ADMIN=myadmin
set WINDROIDA_ADMIN_PASSWORD=a-strong-pass-123
set WINDROIDA_DATASHEET_PASSWORD=another-strong-pass
start.bat
```
مثال (لینوکس/مک):
```bash
WINDROIDA_ADMIN=myadmin WINDROIDA_ADMIN_PASSWORD='a-strong-pass-123' python start.py
```
نکته‌ها:
- `WINDROIDA_ADMIN_PASSWORD` فقط موقع **ساخته شدن** حساب خونده می‌شه. بعدش رمز فقط از داخل برنامه عوض می‌شه.
- سوال/جواب امنیتی حساب ادمین تصادفیه (کسی بلد نیست)، پس «فراموشی رمز» برای ادمین کار نمی‌کنه؛ اگه رمز رو گم کردی، فایل `data/windroida.db` رو پاک کن (همه‌ی داده‌ها پاک می‌شن) یا ردیف کاربر رو دستی عوض کن.
- رمز کاربران با scrypt هش می‌شه؛ پیام‌ها روی دیسک رمزنگاری نمی‌شن.

## امکانات / Features
- ثبت‌نام با سوال امنیتی، ورود ماندگار، پروفایل با عکس (گالری عکس‌ها)، گروه و کانال، مدیریت گروه (ادمین‌ها، مسدودسازی، لینک دعوت)
- پیام صوتی، فایل، عکس/ویدیو، ری‌اکشن، پاسخ، ویرایش، حذف، فوروارد، استیکر، نظرسنجی، جستجو، کارت لینک
- پخش آهنگ تو چت (کشیدن روی نوار برای جلو/عقب مثل تلگرام)
- تماس صوتی و تصویری از طریق سرور
- **اتاق تماشا/گوش‌دادن همزمان** + بازی‌ها: **شطرنج** (۲ نفره)، **مافیا** (۵–۱۵ نفر)، **منچ/Ludo** (۲–۴ نفر؛ تاس و قوانین سمت سرور، صدا و انیمیشن)
- اپ اندروید (`android/`): نسخه‌ی WebView (پیش‌فرض) و نسخه‌ی بومی آزمایشی با پخش‌کننده‌ی موسیقی، اعلان پایدار و حرکات انگشت
- **Cloud Drive** (`cloud-drive/`) و **قاب/Ghab** (`ghab/`): با حساب پیام‌رسان (SSO) وارد می‌شن
- نگهبان (watchdog) که سرور و سرویس‌ها رو زنده نگه می‌داره؛ گواهی https خودامضا با پین‌کردن اثرانگشت

## ساختار / Layout
```
server.py  common.py  rooms.py  social.py  games.py   سرور و منطق (پروتکل WebSocket JSON)
mafia.py mafia_room.py  mensch.py mensch_room.py     موتور و چسب بازی‌های اتاق
chess.py polls.py groupadmin.py webgw.py webpush.py   شطرنج، نظرسنجی، مدیریت گروه، درگاه وب
web/            رابط وب (HTML/JS/CSS)
android/        اپ اندروید (Kotlin، بدون کتابخونه‌ی خارجی)
cloud-drive/ ghab/   سرویس‌های همراه
datasheet.py    برنامه‌ی مدیریت دسکتاپ (tkinter)
start.py start.bat   اجرای همه‌چیز
test_*.py       تست‌ها (مثلاً python test_core.py)
```

## تست‌ها / Tests
هر فایل `test_*.py` مستقل اجرا می‌شه: `python test_core.py`، `python test_mensch_engine.py`، `python test_security.py` و…
تست‌های `*_ui.py` به Playwright نیاز دارن (`pip install playwright && playwright install chromium`).

## اندروید
راهنمای ساخت و نصب: [`android/README-android.md`](android/README-android.md). آدرس سرور رو تو برنامه بزن: `https://SERVER-IP:8081`.

## امنیت / Security notes
- رمز پیش‌فرض‌ها رو عوض کن؛ فایل‌های `data/` (دیتابیس، گواهی و کلیدها) رو هیچ‌وقت تو گیت نذار (`.gitignore` ردشون می‌کنه).
- بدون https، ویس و تماس فقط روی localhost کار می‌کنن.
- اگه سرورت رو عمومی می‌کنی، فقط پورت‌های لازم (پیش‌فرض ۸۰۸۱) رو باز بذار.

## مجوز / License
مجوزی انتخاب نشده؛ قبل از انتشار یکی بذار (مثلاً MIT) و فایل `LICENSE` اضافه کن. فونت Vazirmatn با مجوز OFL همراهه (`web/FONT-LICENSE.txt`).
