package ir.windroida.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import ir.windroida.app.net.Api
import ir.windroida.app.net.Http
import ir.windroida.app.net.Model
import ir.windroida.app.net.ServerCfg
import ir.windroida.app.net.fetchFingerprint
import ir.windroida.app.net.list
import ir.windroida.app.net.lng
import ir.windroida.app.net.str
import ir.windroida.app.ui.AuthScreen
import ir.windroida.app.ui.Img
import ir.windroida.app.ui.IconDrawable
import ir.windroida.app.ui.dp
import ir.windroida.app.ui.iconButton
import ir.windroida.app.ui.label
import ir.windroida.app.ui.ListScreen
import ir.windroida.app.ui.ChatScreen
import ir.windroida.app.ui.Screen
import ir.windroida.app.ui.SetupScreen
import ir.windroida.app.ui.Theme
import java.io.File
import ir.windroida.app.net.map

/**
 * The native Windroida app: chat list, chats, login ... drawn with real Android views (no WebView).
 * Rooms, games, Cloud Drive and Ghab still open as web pages until they get native screens.
 * If anything here crashes, the next launch opens the old web app instead (see installCrashGuard).
 */
class NativeActivity : Activity() {
    lateinit var th: Theme
    val main = Handler(Looper.getMainLooper())
    val model = Model()
    private val listeners = ArrayList<Model.Listener>()
    fun listen(l: Model.Listener) { if (!listeners.contains(l)) listeners.add(l) }
    fun unlisten(l: Model.Listener) { listeners.remove(l) }
    var api: Api? = null
    val groupAdmin by lazy { ir.windroida.app.ui.GroupAdmin(this, th) }
    val polls by lazy { ir.windroida.app.ui.Polls(this, th) }
    val calls by lazy { ir.windroida.app.ui.CallManager(this) }
    var http: Http? = null
    var img: Img? = null
    private lateinit var root: FrameLayout
    private lateinit var miniBar: android.widget.LinearLayout
    private lateinit var miniTitle: android.widget.TextView
    private lateinit var miniIcon: android.widget.ImageView
    private val playerListeners = ArrayList<(Long, Float, Boolean) -> Unit>()
    fun addPlayerListener(l: (Long, Float, Boolean) -> Unit) { if (!playerListeners.contains(l)) playerListeners.add(l) }
    fun removePlayerListener(l: (Long, Float, Boolean) -> Unit) { playerListeners.remove(l) }
    private val stack = ArrayList<Screen>()
    private var stopJob: Runnable? = null
    private var resultCb: ((Int, Intent?) -> Unit)? = null
    private var permCb: ((Boolean) -> Unit)? = null
    private var pendingOpenChat = 0L
    private val startedAt = System.currentTimeMillis()

    val downloads: ir.windroida.app.ui.Downloads by lazy { ir.windroida.app.ui.Downloads(this, { http }, { mediaKey }) }
    val voicePlayer: ir.windroida.app.ui.VoicePlayer by lazy { ir.windroida.app.ui.VoicePlayer.shared(main) }

    val mediaKey: String get() = api?.mediaKey ?: ""

    // ------------------------------------------------------------------ lifecycle
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installCrashGuard()
        if (Prefs.mode(this) == "web") { startActivity(Intent(this, MainActivity::class.java)); finish(); return }   // the mode only ever changes when the user chooses it
        th = Theme.of(this)
        model.listener = object : Model.Listener {
            override fun onChatList() { for (l in ArrayList(listeners)) l.onChatList() }
            override fun onChatChanged(chatId: Long) { for (l in ArrayList(listeners)) l.onChatChanged(chatId) }
            override fun onMessages(chatId: Long, kind: Int, messageId: Long) { for (l in ArrayList(listeners)) l.onMessages(chatId, kind, messageId) }
            override fun onTyping(chatId: Long) { for (l in ArrayList(listeners)) l.onTyping(chatId) }
            override fun onNeedRefresh() { refreshChats() }
            override fun onSessionEnded(reason: String) { /* the Api reports LOGGED_OUT for the same event */ }
        }
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_LTR      // same layout as Telegram: back on the left, send/mic on the right; text aligns by its own language
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        window.statusBarColor = th.bar
        window.navigationBarColor = th.panel
        if (Build.VERSION.SDK_INT >= 26 && !th.dark) window.decorView.systemUiVisibility = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        root = FrameLayout(this)
        root.setBackgroundColor(th.bg)
        val frame = android.widget.LinearLayout(this); frame.orientation = android.widget.LinearLayout.VERTICAL
        frame.setBackgroundColor(th.bg)
        frame.addView(buildMini(), android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        frame.addView(root, android.widget.LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(frame, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        voicePlayer.onProgress { id, p, playing -> updateMini(); for (l in ArrayList(playerListeners)) l(id, p, playing) }
        voicePlayer.addStateListener(musicState)
        voicePlayer.onFailure = { msg -> toast(msg) }
        updateMini()
        pendingOpenChat = intent?.getIntExtra("chat", 0)?.toLong() ?: 0L
        boot()
        showLastCrash()
    }

    /** The previous run crashed: show the technical text once so it can be reported. */
    private fun showLastCrash() {
        try {
            val f = File(filesDir, "native-crash.txt")
            if (!f.isFile) return
            val text = f.readText().take(1500)
            f.renameTo(File(filesDir, "native-crash-last.txt"))
            val tv = android.widget.TextView(this); tv.text = text; tv.textSize = 11f; tv.setTextIsSelectable(true); tv.setPadding(40, 20, 40, 20)
            tv.textDirection = View.TEXT_DIRECTION_LTR
            val sv = android.widget.ScrollView(this); sv.addView(tv)
            AlertDialog.Builder(this).setTitle("برنامه دفعه‌ی قبل بسته شد").setView(sv)
                .setPositiveButton("باشه", null).setNegativeButton("نسخه وب") { _, _ -> openWeb() }.show()
        } catch (_: Exception) {}
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        val id = i.getIntExtra("chat", 0).toLong()
        if (id != 0L) { if (model.chats.containsKey(id)) openChat(id) else pendingOpenChat = id }
    }

    override fun onStart() {
        super.onStart()
        stopJob?.let { main.removeCallbacks(it) }; stopJob = null
        App.foreground = true
        model.viewing = true
        if (this::root.isInitialized) api?.let { if (it.state == Api.OFFLINE) it.reconnectNow(); it.start() }
        (stack.lastOrNull() as? ListScreen)?.onShow()
    }

    override fun onStop() {
        App.foreground = false
        model.viewing = false
        saveCache()
        // keep the link for a minute (file pickers, permission dialogs, quick app switches), then let the phone rest
        val job = Runnable { api?.stop() }
        stopJob = job
        main.postDelayed(job, 60_000)
        super.onStop()
    }

    override fun onDestroy() {
        voicePlayer.removeStateListener(musicState)
        stopJob?.let { main.removeCallbacks(it) }
        api?.stop()
        for (s in stack) s.onDestroy()
        super.onDestroy()
    }

    override fun onBackPressed() {
        viewer?.let { it.close(); return }
        sheet?.let { it.close(); return }
        val t = stack.lastOrNull()
        if (t != null && t.onBack()) return
        if (stack.size > 1) pop() else super.onBackPressed()
    }

    @Deprecated("framework callback")
    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val cb = resultCb; resultCb = null
        cb?.invoke(res, data)
    }

    @Deprecated("framework callback")
    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        val cb = permCb; permCb = null
        cb?.invoke(res.isNotEmpty() && res[0] == android.content.pm.PackageManager.PERMISSION_GRANTED)
    }

    // ------------------------------------------------------------------ music: system media session + notification, player sheet
    private var svcOn = false
    private val musicState: () -> Unit = {
        updateMini()
        if (voicePlayer.playingId != 0L) { if (!svcOn) { svcOn = true; MusicService.start(this) } } else svcOn = false
    }
    private var sheet: ir.windroida.app.ui.PlayerSheet? = null
    fun showPlayer() {
        if (sheet != null || voicePlayer.playingId == 0L) return
        val s = ir.windroida.app.ui.PlayerSheet(this, voicePlayer); sheet = s
        root.addView(s.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }
    private var viewer: ir.windroida.app.ui.ImageViewer? = null
    fun openImage(id: Long, title: String, gallery: List<String>? = null) {
        if (viewer != null) return
        val v = ir.windroida.app.ui.ImageViewer(this, id, title, gallery); viewer = v
        root.addView(v.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }
    fun removeViewer(v: ir.windroida.app.ui.ImageViewer) { try { root.removeView(v.view) } catch (_: Exception) {}; if (viewer === v) viewer = null }
    /** invite link: show the group, ask, join */
    fun joinByInvite(token: String) {
        rpc("invite_info", mapOf("token" to token)) { ok, r ->
            if (!ok) { toast(r.str("error", "لینک معتبر نیست")); return@rpc }
            val c = r.map("chat") ?: return@rpc
            android.app.AlertDialog.Builder(this).setTitle(c.str("title")).setMessage((c.str("description").ifEmpty { "" }) + "\nعضو بشی؟")
                .setPositiveButton("عضو شو") { _, _ -> rpc("join", mapOf("token" to token)) { ok2, r2 ->
                    if (ok2) { refreshChats(); r2.map("chat")?.let { n -> model.chats[n.lng("id")] = LinkedHashMap(n); openChat(n.lng("id")) } } else toast(r2.str("error", "نشد")) } }
                .setNegativeButton("نه", null).show()
        }
    }
    fun removeOverlay(v: View, s: Any) { try { root.removeView(v) } catch (_: Exception) {}; if (sheet === s) sheet = null }
    /** where songs are streamed from (None while not connected) */
    fun streamCfg(): ir.windroida.app.ui.StreamCfg? {
        val h = http ?: return null
        return ir.windroida.app.ui.StreamCfg(h.url(""), Prefs.pin(this), mediaKey, File(cacheDir, "stream"), { t -> downloads.cached(t.id, t.name) }, { t, cb -> downloads.fetch(t.id, t.name, { _, _ -> }) { f, e -> cb(f, e) } })
    }

    // ------------------------------------------------------------------ mini player (songs / voice keep playing across screens)
    private fun buildMini(): View {
        val bar = android.widget.LinearLayout(this); bar.orientation = android.widget.LinearLayout.HORIZONTAL; bar.gravity = android.view.Gravity.CENTER_VERTICAL
        bar.setBackgroundColor(th.panel); bar.visibility = View.GONE; bar.elevation = dp(3).toFloat()
        miniIcon = android.widget.ImageView(this)
        miniIcon.setImageDrawable(IconDrawable("pause", th.accent))
        val ib = android.widget.FrameLayout(this); ib.isClickable = true
        ib.addView(miniIcon, android.widget.FrameLayout.LayoutParams(dp(22), dp(22), android.view.Gravity.CENTER))
        ib.setOnClickListener { voicePlayer.toggleCurrent() }
        bar.addView(ib, android.widget.LinearLayout.LayoutParams(dp(48), dp(44)))
        miniTitle = label(this, "", 14f, th.text, true)
        miniTitle.maxLines = 1; miniTitle.ellipsize = android.text.TextUtils.TruncateAt.END
        miniTitle.isClickable = true; miniTitle.setOnClickListener { showPlayer() }; miniTitle.setPadding(0, dp(12), 0, dp(12))
        bar.addView(miniTitle, android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(iconButton(this, th, "close", th.muted) { voicePlayer.stop(); updateMini() })
        miniBar = bar
        return bar
    }

    private fun updateMini() {
        val on = voicePlayer.playingId != 0L
        miniBar.visibility = if (on) View.VISIBLE else View.GONE
        if (!on) return
        miniTitle.text = voicePlayer.title + (if (voicePlayer.subtitle.isNotEmpty()) "  ·  " + voicePlayer.subtitle else "")
        miniIcon.setImageDrawable(IconDrawable(if (voicePlayer.isPlaying) "pause" else "play", th.accent))
    }

    // ------------------------------------------------------------------ swipe from the left edge = back (like Telegram)
    private var swipeState = 0
    private var sx = 0f
    private var sy = 0f
    private var vt: android.view.VelocityTracker? = null

    /** a finger that starts on a seek bar / a horizontally scrolling strip / a text field keeps its own gesture (the edge strip still works) */
    private fun swipeBlocked(ev: android.view.MotionEvent): Boolean {
        if (ev.rawX < dp(36)) return false
        fun hit(v: View): Boolean {
            val r = android.graphics.Rect()
            if (!v.getGlobalVisibleRect(r) || !r.contains(ev.rawX.toInt(), ev.rawY.toInt())) return false
            if (v is android.widget.SeekBar || v is android.widget.HorizontalScrollView || v is android.widget.EditText || v.tag == "noswipe") return true
            if (v is ViewGroup) for (i in 0 until v.childCount) if (hit(v.getChildAt(i))) return true
            return false
        }
        return try { hit(stack.last().view) } catch (_: Exception) { false }
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (!this::root.isInitialized || stack.size < 2 || stack.last() is ir.windroida.app.ui.CallScreen) { swipeState = 0; return super.dispatchTouchEvent(ev) }
        val top = stack.last().view
        val under = stack[stack.size - 2].view
        val w = root.width.toFloat()
        when (ev.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> {
                swipeState = if (viewer == null && sheet == null && !swipeBlocked(ev)) 1 else 0
                if (swipeState == 1) { sx = ev.rawX; sy = ev.rawY; vt?.recycle(); vt = android.view.VelocityTracker.obtain(); vt!!.addMovement(ev) }
            }
            android.view.MotionEvent.ACTION_MOVE -> if (swipeState >= 1) {
                vt?.addMovement(ev)
                val dx = ev.rawX - sx; val dy = ev.rawY - sy
                if (swipeState == 1 && dx > dp(14) && dx > Math.abs(dy) * 1.5f) {
                    swipeState = 2
                    val c = android.view.MotionEvent.obtain(ev); c.action = android.view.MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(c); c.recycle()
                    val v = currentFocus; if (v != null) hideKeyboard(v)
                }
                if (swipeState == 2) {
                    val x = Math.max(0f, dx)
                    top.translationX = x; under.alpha = 1f; under.translationX = -0.25f * w * (1f - Math.min(1f, x / w))
                    return true
                }
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                if (swipeState == 2) {
                    swipeState = 0
                    vt?.computeCurrentVelocity(1000); val vx = vt?.xVelocity ?: 0f
                    val x = top.translationX
                    if (ev.actionMasked == android.view.MotionEvent.ACTION_UP && (x > w * 0.35f || vx > 1200f)) {
                        under.translationX = 0f
                        top.animate().translationX(w).setDuration(140).withEndAction { finishPop() }.start()
                    } else {
                        top.animate().translationX(0f).setDuration(140).start()
                        under.animate().translationX(-0.25f * w).setDuration(140).withEndAction { under.translationX = 0f }.start()
                    }
                    vt?.recycle(); vt = null
                    return true
                }
                swipeState = 0
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    /** the top screen is already off-screen (slid away): remove it for real */
    private fun finishPop() {
        if (stack.size <= 1) return
        val s = stack.removeAt(stack.size - 1)
        s.onHide(); root.removeView(s.view); s.onDestroy()
        stack.lastOrNull()?.let { it.view.translationX = 0f; it.onShow() }
    }

    // ------------------------------------------------------------------ helpers for screens
    fun toast(msg: String) { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }

    fun pickResult(intent: Intent, cb: (Int, Intent?) -> Unit) {
        resultCb = cb
        try { startActivityForResult(intent, 4711) } catch (e: Exception) { resultCb = null; toast("برنامه‌ای برای این کار پیدا نشد") }
    }

    fun askPermission(perm: String, cb: (Boolean) -> Unit) {
        if (checkSelfPermission(perm) == android.content.pm.PackageManager.PERMISSION_GRANTED) { cb(true); return }
        permCb = cb
        requestPermissions(arrayOf(perm), 4712)
    }

    /** Runs [cb] on the UI thread with the answer of a server call. */
    fun rpc(op: String, params: Map<String, Any?> = emptyMap(), cb: ((Boolean, Map<String, Any?>) -> Unit)? = null) {
        val a = api
        if (a == null) { cb?.invoke(false, mapOf("error" to "به سرور وصل نیستی")); return }
        a.call(op, params) { ok, r -> main.post { cb?.invoke(ok, r) } }
    }

    fun top(): Screen? = stack.lastOrNull()

    fun push(s: Screen) {
        val prev = stack.lastOrNull()
        stack.add(s)
        root.addView(s.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        if (prev != null) {
            s.view.translationX = root.width * 0.25f
            s.view.alpha = 0f
            s.view.animate().translationX(0f).alpha(1f).setDuration(170).start()
            prev.onHide()
        }
        s.onShow()
    }

    fun pop() {
        if (stack.size <= 1) return
        val s = stack.removeAt(stack.size - 1)
        s.onHide()
        s.view.animate().translationX(root.width.toFloat()).setDuration(150).withEndAction { root.removeView(s.view); s.onDestroy() }.start()
        stack.lastOrNull()?.onShow()
        val v = currentFocus; if (v != null) hideKeyboard(v)
    }

    fun replaceAll(s: Screen) {
        val old = ArrayList(stack)
        stack.clear()
        for (o in old) { o.onHide(); root.removeView(o.view); o.onDestroy() }
        stack.add(s)
        root.addView(s.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        s.onShow()
    }

    fun hideKeyboard(v: View) {
        val im = getSystemService(android.view.inputmethod.InputMethodManager::class.java)
        im?.hideSoftInputFromWindow(v.windowToken, 0)
    }

    fun openChat(id: Long) {
        val t = stack.lastOrNull()
        if (t is ChatScreen && t.chatId == id) return
        if (!model.chats.containsKey(id)) return
        // a chat opened from another chat replaces it (no deep stacks)
        if (t is ChatScreen) replaceTop(ChatScreen(this, id)) else push(ChatScreen(this, id))
    }

    /** open [id] at message [around] (from the profile's shared-media lists); replaces an open chat instead of stacking */
    fun openChatAt(id: Long, around: Long) {
        if (!model.chats.containsKey(id)) return
        val t = stack.lastOrNull()
        if (t is ChatScreen) replaceTop(ChatScreen(this, id, around)) else push(ChatScreen(this, id, around))
    }

    private fun replaceTop(s: Screen) {
        if (stack.size <= 1) { push(s); return }
        val old = stack.removeAt(stack.size - 1)
        old.onHide(); root.removeView(old.view); old.onDestroy()
        stack.add(s)
        root.addView(s.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        s.onShow()
    }

    /** open the web app for one visit (rooms, games, profile photo) WITHOUT changing the chosen mode; back returns here */
    fun openWebOnce(hash: String? = null, chat: Long = 0L) {
        val i = Intent(this, MainActivity::class.java).putExtra("once", true)
        if (hash != null) i.putExtra("hash", hash)
        if (chat != 0L) i.putExtra("chat", chat.toInt())
        startActivity(i)
    }
    fun openWeb() { Prefs.setMode(this, "web"); startActivity(Intent(this, MainActivity::class.java)); finish() }

    // ------------------------------------------------------------------ start-up: server -> certificate -> connection -> screens
    private fun boot() {
        val server = Prefs.server(this)
        if (server.isEmpty()) { replaceAll(SetupScreen(this)); return }
        val cfg0 = ServerCfg.parse(server, Prefs.pin(this))
        if (cfg0 == null) { replaceAll(SetupScreen(this)); return }
        if (cfg0.tls && cfg0.pin.isEmpty()) { confirmCertificate(cfg0); return }
        startNet(cfg0)
    }

    /** First connection to an https server: show the certificate fingerprint once; the user confirms it matches the server window. */
    fun confirmCertificate(cfg: ServerCfg) {
        replaceAll(ir.windroida.app.ui.WaitScreen(this, "در حال اتصال به سرور…"))
        Thread {
            val fp = try { fetchFingerprint(cfg.host, cfg.port) } catch (e: Exception) { null }
            main.post {
                if (fp == null) { if (Prefs.pin(this).isNotEmpty()) startNet(ServerCfg(cfg.host, cfg.port, cfg.tls, Prefs.pin(this))) else replaceAll(SetupScreen(this, "به این آدرس وصل نشد: ${cfg.base}")); return@post }
                AlertDialog.Builder(this).setTitle("تأیید گواهی سرور").setCancelable(false)
                    .setMessage("اثر انگشت (SHA-256) گواهی این سرور:\n\n$fp\n\nفقط اگه دقیقاً با اثری که تو پنجره سرور (start.bat) نوشته شده یکیه، اعتماد کن.")
                    .setPositiveButton("اعتماد می‌کنم") { _, _ -> Prefs.setPin(this, fp); startNet(ServerCfg(cfg.host, cfg.port, cfg.tls, fp)) }
                    .setNegativeButton("لغو") { _, _ -> if (Prefs.pin(this).isNotEmpty()) startNet(ServerCfg(cfg.host, cfg.port, cfg.tls, Prefs.pin(this))) else replaceAll(SetupScreen(this)) }.show()
            }
        }.start()
    }

    /** The server certificate changed (or the saved fingerprint is wrong): forget it and ask the user to confirm the current one again. */
    fun repin() {
        api?.stop()
        // the saved fingerprint is KEPT until the user confirms a new one: cancelling (or a failed attempt) leaves everything as it was
        val cfg = ServerCfg.parse(Prefs.server(this), "")
        if (cfg == null) replaceAll(SetupScreen(this)) else confirmCertificate(cfg)
    }

    fun startNet(cfg: ServerCfg) {
        api?.stop()
        val a = Api(cfg)
        a.token = Prefs.token(this)
        api = a
        val h = Http(cfg)
        http = h
        img = Img(h, File(cacheDir, "img"))
        val cached = if (a.token.isNotEmpty()) loadCache() else false
        a.listener = object : Api.Listener {
            override fun onEvent(e: Map<String, Any?>) {
                val ev = e.str("event")
                if (ev.startsWith("call_")) { if (ev == "call_audio") calls.onAudio(e) else main.post { calls.onEvent(e) }; return }
                main.post { model.applyEvent(e) }
            }
            override fun onState(state: Int, detail: String) { main.post { onApiState(state, detail) } }
        }
        if (cached) replaceAll(ListScreen(this)) else replaceAll(ir.windroida.app.ui.WaitScreen(this, "در حال اتصال…"))
        a.start()
    }

    var netError = ""
    fun onApiState(state: Int, detail: String) {
        val t = stack.lastOrNull()
        if (state == Api.OFFLINE) { netError = detail; (t as? ir.windroida.app.ui.WaitScreen)?.setError(detail.ifEmpty { "ارتباط قطع شد" }) }
        if (state == Api.ONLINE) netError = ""
        when (state) {
            Api.CONNECTED -> if (api?.token.isNullOrEmpty() && t !is AuthScreen) replaceAll(AuthScreen(this))
            Api.LOGGED_OUT -> {
                Prefs.setToken(this, ""); NotifyService.stop(this)
                if (t !is AuthScreen) replaceAll(AuthScreen(this, if (detail == "banned") "حساب تو مسدود شد." else if (detail == "session_ended") "این نشست از دستگاه دیگه بسته شد." else ""))
            }
            Api.ONLINE -> afterOnline()
        }
        (stack.lastOrNull() as? ListScreen)?.onNetState(state)
        (stack.lastOrNull() as? ChatScreen)?.onNetState(state)
    }

    private var refreshing = false
    /** Reload the chat list from the server (something changed that we can't apply by hand). */
    fun refreshChats() {
        if (refreshing || api?.state != Api.ONLINE) return
        refreshing = true
        rpc("chats") { ok, r -> refreshing = false; if (ok) model.setChats(r.list("chats")) }
    }

    /** Logged in (fresh login or resume): keep the token, reload chat list (and the open chat), start notifications. */
    private fun afterOnline() {
        val a = api ?: return
        Prefs.setToken(this, a.token)
        NotifyService.start(this)
        askKeepAlive()
        model.replaceMe(a.me)
        calls.onOnline()
        rpc("chats") { ok, r ->
            if (!ok) return@rpc
            model.setChats(r.list("chats"))
            val t = stack.lastOrNull()
            if (t !is ListScreen && t !is ChatScreen) replaceAll(ListScreen(this))
            (stack.lastOrNull() as? ChatScreen)?.reloadLatest()
            if (pendingOpenChat != 0L) { val id = pendingOpenChat; pendingOpenChat = 0L; openChat(id) }
        }
    }

    /** once: notification permission + "don't optimise battery" so the background connection survives */
    private fun askKeepAlive() {
        if (android.os.Build.VERSION.SDK_INT >= 33) askPermission(android.Manifest.permission.POST_NOTIFICATIONS) {}
        val sp = getSharedPreferences("wd", MODE_PRIVATE)
        if (sp.getBoolean("battery_asked", false)) return
        sp.edit().putBoolean("battery_asked", true).apply()
        val pm = getSystemService(android.os.PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) return
        android.app.AlertDialog.Builder(this).setMessage("برای اینکه پیام‌ها و تماس‌ها وقتی اپ بسته‌ست هم برسن، محدودیت باتری برای Windroida برداشته بشه؟")
            .setPositiveButton("باشه") { _, _ -> try { startActivity(Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:$packageName"))) } catch (_: Exception) {} }
            .setNegativeButton("نه", null).show()
    }

    fun logout() {
        api?.logout()
        Prefs.setToken(this, "")
        NotifyService.stop(this)
        try { File(filesDir, "cache.json").delete() } catch (_: Exception) {}
        model.chats.clear(); model.msgs.clear()
        api?.token = ""
        api?.reconnectNow()
        replaceAll(AuthScreen(this))
    }

    // ------------------------------------------------------------------ saved copy
    private fun saveCache() {
        try { if (model.myId != 0L) File(filesDir, "cache.json").writeText(model.toCache()) } catch (_: Exception) {}
    }

    private fun loadCache(): Boolean = try {
        val f = File(filesDir, "cache.json")
        f.isFile && model.loadCache(f.readText(), 0L)
    } catch (_: Exception) { false }

    // ------------------------------------------------------------------ crash guard
    private fun installCrashGuard() {
        val old = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val log = File(filesDir, "native-crash.txt")
                log.writeText(java.util.Date().toString() + "\n" + android.util.Log.getStackTraceString(e))
                // never switch mode by ourselves: the next start shows this log with an explicit "نسخه وب" button
            } catch (_: Throwable) {}
            old?.uncaughtException(t, e)
        }
    }
}
