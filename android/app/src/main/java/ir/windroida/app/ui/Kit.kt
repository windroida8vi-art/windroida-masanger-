package ir.windroida.app.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Colors of the Telegram-like look; follows the system dark/light setting. */
class Theme(val dark: Boolean) {
    val bg = if (dark) 0xFF0E1621.toInt() else 0xFFFFFFFF.toInt()
    val panel = if (dark) 0xFF17212B.toInt() else 0xFFFFFFFF.toInt()
    val bar = if (dark) 0xFF17212B.toInt() else 0xFF517DA2.toInt()
    val barText = 0xFFFFFFFF.toInt()
    val chatBg = if (dark) 0xFF0E1621.toInt() else 0xFFDDE6EC.toInt()
    val text = if (dark) 0xFFF5F5F5.toInt() else 0xFF111111.toInt()
    val muted = if (dark) 0xFF7F91A4.toInt() else 0xFF707579.toInt()
    val accent = if (dark) 0xFF5EB5F7.toInt() else 0xFF3390EC.toInt()
    val accentFill = 0xFF3390EC.toInt()
    val line = if (dark) 0xFF0B121A.toInt() else 0xFFE4E4E5.toInt()
    val mine = if (dark) 0xFF2B5278.toInt() else 0xFFEFFDDE.toInt()
    val theirs = if (dark) 0xFF182533.toInt() else 0xFFFFFFFF.toInt()
    val mineText = if (dark) 0xFFFFFFFF.toInt() else 0xFF111111.toInt()
    val mineMeta = if (dark) 0xFF7DA8D3.toInt() else 0xFF5DA05A.toInt()
    val danger = 0xFFE53935.toInt()
    val ok = 0xFF4FAE4E.toInt()
    val field = if (dark) 0xFF0E1621.toInt() else 0xFFF1F3F4.toInt()
    val ripple = if (dark) 0x22FFFFFF else 0x22000000
    val badge = 0xFF3390EC.toInt()
    val badgeMuted = if (dark) 0xFF3A4A5A.toInt() else 0xFFC6C9CC.toInt()
    val quoteBar = if (dark) 0xFF5EB5F7.toInt() else 0xFF3390EC.toInt()

    companion object {
        fun of(c: Context) = Theme((c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)
        val AVATAR_COLORS = intArrayOf(0xFFE17076.toInt(), 0xFFFAA774.toInt(), 0xFFA695E7.toInt(), 0xFF7BC862.toInt(), 0xFF6EC9CB.toInt(), 0xFF65AADD.toInt(), 0xFFEE7AAE.toInt())
    }
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()
fun Context.dpf(v: Float): Float = v * resources.displayMetrics.density

fun rounded(color: Int, radiusPx: Float): GradientDrawable = GradientDrawable().also { it.setColor(color); it.cornerRadius = radiusPx }

fun roundedCorners(color: Int, tl: Float, tr: Float, br: Float, bl: Float): GradientDrawable =
    GradientDrawable().also { it.setColor(color); it.cornerRadii = floatArrayOf(tl, tl, tr, tr, br, br, bl, bl) }

/** A background with a touch ripple on top of [base]. */
fun ripple(base: Drawable?, rippleColor: Int): Drawable = RippleDrawable(ColorStateList.valueOf(rippleColor), base, null)

/** Vazirmatn (shipped in assets/fonts). Falls back to the system font if it cannot be loaded. */
object Fonts {
    private var reg: Typeface? = null
    private var bold: Typeface? = null
    private var tried = false
    private fun load(ctx: Context) {
        if (tried) return
        tried = true
        try { reg = Typeface.createFromAsset(ctx.applicationContext.assets, "fonts/Vazirmatn-Regular.ttf") } catch (_: Exception) {}
        try { bold = Typeface.createFromAsset(ctx.applicationContext.assets, "fonts/Vazirmatn-Bold.ttf") } catch (_: Exception) {}
    }
    fun regular(ctx: Context): Typeface { load(ctx); return reg ?: Typeface.DEFAULT }
    fun bold(ctx: Context): Typeface { load(ctx); return bold ?: Typeface.DEFAULT_BOLD }
}

fun label(ctx: Context, text: String, sizeSp: Float, color: Int, bold: Boolean = false): TextView = TextView(ctx).also {
    it.text = text
    it.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp)
    it.setTextColor(color)
    it.typeface = if (bold) Fonts.bold(ctx) else Fonts.regular(ctx)
    it.textDirection = View.TEXT_DIRECTION_FIRST_STRONG
    it.includeFontPadding = false
}

fun TextView.oneLine(): TextView { maxLines = 1; ellipsize = TextUtils.TruncateAt.END; return this }

fun lp(w: Int, h: Int) = LinearLayout.LayoutParams(w, h)
const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun LinearLayout.LayoutParams.weight(w: Float): LinearLayout.LayoutParams { weight = w; return this }

fun hSpace(ctx: Context, dpv: Int): View = View(ctx).also { it.layoutParams = LinearLayout.LayoutParams(ctx.dp(dpv), 1) }

/** Icons drawn with the Canvas (no image files), 24x24 design grid, scaled to whatever size the view has. */
class IconDrawable(private val kind: String, private val color: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = color; it.style = Paint.Style.STROKE; it.strokeCap = Paint.Cap.ROUND; it.strokeJoin = Paint.Join.ROUND }
    private val f = Paint(Paint.ANTI_ALIAS_FLAG).also { it.color = color; it.style = Paint.Style.FILL }
    override fun draw(c: Canvas) {
        val b = bounds
        val s = Math.min(b.width(), b.height()) / 24f
        c.save()
        c.translate(b.left + (b.width() - 24 * s) / 2f, b.top + (b.height() - 24 * s) / 2f)
        c.scale(s, s)
        p.strokeWidth = 2f
        when (kind) {
            "menu" -> { for (y in floatArrayOf(6f, 12f, 18f)) c.drawLine(4f, y, 20f, y, p) }
            "back" -> { val q = Path(); q.moveTo(15f, 5f); q.lineTo(8f, 12f); q.lineTo(15f, 19f); c.drawPath(q, p) }
            "forward" -> { val q = Path(); q.moveTo(9f, 5f); q.lineTo(16f, 12f); q.lineTo(9f, 19f); c.drawPath(q, p) }
            "search" -> { c.drawCircle(10.5f, 10.5f, 6f, p); c.drawLine(15f, 15f, 20f, 20f, p) }
            "close" -> { c.drawLine(6f, 6f, 18f, 18f, p); c.drawLine(18f, 6f, 6f, 18f, p) }
            "more" -> { for (y in floatArrayOf(5f, 12f, 19f)) c.drawCircle(12f, y, 1.8f, f) }
            "plus" -> { c.drawLine(12f, 5f, 12f, 19f, p); c.drawLine(5f, 12f, 19f, 12f, p) }
            "check" -> { val q = Path(); q.moveTo(5f, 12.5f); q.lineTo(10f, 17.5f); q.lineTo(19f, 7f); c.drawPath(q, p) }
            "check2" -> {
                p.strokeWidth = 1.8f
                val q = Path(); q.moveTo(2f, 12.5f); q.lineTo(7f, 17.5f); q.lineTo(16f, 7f); c.drawPath(q, p)
                val r = Path(); r.moveTo(10f, 16.5f); r.lineTo(11f, 17.5f); r.lineTo(21f, 6.5f); c.drawPath(r, p)
            }
            "clock" -> { c.drawCircle(12f, 12f, 8f, p); c.drawLine(12f, 7f, 12f, 12f, p); c.drawLine(12f, 12f, 15.5f, 14f, p) }
            "send" -> { val q = Path(); q.moveTo(3f, 20f); q.lineTo(21f, 12f); q.lineTo(3f, 4f); q.lineTo(3f, 10.5f); q.lineTo(14f, 12f); q.lineTo(3f, 13.5f); q.close(); c.drawPath(q, f) }
            "mic" -> {
                c.drawRoundRect(RectF(9f, 3f, 15f, 14f), 3f, 3f, f)
                val q = Path(); q.moveTo(6f, 11f); q.arcTo(RectF(6f, 6f, 18f, 18f), 180f, -180f); c.drawPath(q, p)
                c.drawLine(12f, 18f, 12f, 21f, p)
            }
            "attach" -> { c.drawRoundRect(RectF(7.5f, 3.5f, 16.5f, 20.5f), 4.5f, 4.5f, p); c.drawLine(12f, 8f, 12f, 15f, p) }
            "reply" -> { val q = Path(); q.moveTo(10f, 7f); q.lineTo(4f, 12f); q.lineTo(10f, 17f); c.drawPath(q, p); val r = Path(); r.moveTo(4f, 12f); r.lineTo(14f, 12f); r.cubicTo(18f, 12f, 20f, 14f, 20f, 19f); c.drawPath(r, p) }
            "pencil" -> { val q = Path(); q.moveTo(4f, 20f); q.lineTo(5f, 15f); q.lineTo(16f, 4f); q.lineTo(20f, 8f); q.lineTo(9f, 19f); q.close(); c.drawPath(q, p) }
            "trash" -> { c.drawLine(5f, 7f, 19f, 7f, p); c.drawRect(RectF(7f, 7f, 17f, 20f), p); c.drawLine(10f, 4f, 14f, 4f, p) }
            "pin" -> { val q = Path(); q.moveTo(9f, 3f); q.lineTo(15f, 3f); q.lineTo(14f, 10f); q.lineTo(18f, 14f); q.lineTo(6f, 14f); q.lineTo(10f, 10f); q.close(); c.drawPath(q, p); c.drawLine(12f, 14f, 12f, 21f, p) }
            "copy" -> { c.drawRoundRect(RectF(8f, 8f, 19f, 20f), 2f, 2f, p); val q = Path(); q.moveTo(5f, 15f); q.lineTo(5f, 5f); q.lineTo(14f, 5f); c.drawPath(q, p) }
            "download" -> { c.drawLine(12f, 4f, 12f, 15f, p); val q = Path(); q.moveTo(7f, 11f); q.lineTo(12f, 16f); q.lineTo(17f, 11f); c.drawPath(q, p); c.drawLine(5f, 20f, 19f, 20f, p) }
            "play" -> { val q = Path(); q.moveTo(8f, 5f); q.lineTo(19f, 12f); q.lineTo(8f, 19f); q.close(); c.drawPath(q, f) }
            "pause" -> { c.drawRect(RectF(7f, 5f, 11f, 19f), f); c.drawRect(RectF(13f, 5f, 17f, 19f), f) }
            "smile" -> { c.drawCircle(12f, 12f, 8.5f, p); c.drawCircle(9f, 10f, 1.1f, f); c.drawCircle(15f, 10f, 1.1f, f); val q = Path(); q.moveTo(8f, 14f); q.quadTo(12f, 18f, 16f, 14f); c.drawPath(q, p) }
            "next" -> { val q = Path(); q.moveTo(6f, 5f); q.lineTo(15f, 12f); q.lineTo(6f, 19f); q.close(); c.drawPath(q, f); c.drawLine(18.5f, 5f, 18.5f, 19f, p) }
            "prev" -> { val q = Path(); q.moveTo(18f, 5f); q.lineTo(9f, 12f); q.lineTo(18f, 19f); q.close(); c.drawPath(q, f); c.drawLine(5.5f, 5f, 5.5f, 19f, p) }
            "repeat", "repeat1" -> {
                val q = Path(); q.moveTo(5f, 13f); q.lineTo(5f, 11f); q.arcTo(RectF(5f, 7f, 9f, 11f), 180f, 90f); q.lineTo(18f, 7f); c.drawPath(q, p)
                val a = Path(); a.moveTo(15f, 4f); a.lineTo(18.5f, 7f); a.lineTo(15f, 10f); c.drawPath(a, p)
                val r = Path(); r.moveTo(19f, 11f); r.lineTo(19f, 13f); r.arcTo(RectF(15f, 13f, 19f, 17f), 0f, 90f); r.lineTo(6f, 17f); c.drawPath(r, p)
                val b = Path(); b.moveTo(9f, 14f); b.lineTo(5.5f, 17f); b.lineTo(9f, 20f); c.drawPath(b, p)
                if (kind == "repeat1") { c.drawLine(11.5f, 10.8f, 12.8f, 10f, p); c.drawLine(12.8f, 10f, 12.8f, 14f, p) }
            }
            "shuffle" -> {
                val q = Path(); q.moveTo(4f, 7f); q.lineTo(8f, 7f); q.cubicTo(13f, 7f, 11f, 17f, 16f, 17f); q.lineTo(20f, 17f); c.drawPath(q, p)
                val r = Path(); r.moveTo(4f, 17f); r.lineTo(8f, 17f); r.cubicTo(10f, 17f, 10.5f, 14f, 11f, 13f); c.drawPath(r, p)
                val r2 = Path(); r2.moveTo(13f, 11f); r2.cubicTo(13.6f, 9.6f, 14.5f, 7f, 16f, 7f); r2.lineTo(20f, 7f); c.drawPath(r2, p)
                val a = Path(); a.moveTo(17f, 4f); a.lineTo(20f, 7f); a.lineTo(17f, 10f); c.drawPath(a, p)
                val b = Path(); b.moveTo(17f, 14f); b.lineTo(20f, 17f); b.lineTo(17f, 20f); c.drawPath(b, p)
            }
            "queue" -> { for (y in floatArrayOf(6f, 11f, 16f)) c.drawLine(4f, y, 15f, y, p); val q = Path(); q.moveTo(17f, 14f); q.lineTo(21f, 17f); q.lineTo(17f, 20f); q.close(); c.drawPath(q, f) }
            "gamepad" -> { c.drawRoundRect(RectF(3f, 7f, 21f, 18f), 5f, 5f, p); c.drawLine(8f, 10.5f, 8f, 14.5f, p); c.drawLine(6f, 12.5f, 10f, 12.5f, p); c.drawCircle(15f, 11.5f, 1.2f, f); c.drawCircle(17.5f, 14f, 1.2f, f) }
            "chat" -> { val q = Path(); q.moveTo(5f, 5f); q.lineTo(19f, 5f); q.lineTo(19f, 16f); q.lineTo(11f, 16f); q.lineTo(6.5f, 20f); q.lineTo(6.5f, 16f); q.lineTo(5f, 16f); q.close(); c.drawPath(q, p) }
            "file" -> { val q = Path(); q.moveTo(6f, 3f); q.lineTo(14f, 3f); q.lineTo(19f, 8f); q.lineTo(19f, 21f); q.lineTo(6f, 21f); q.close(); c.drawPath(q, p); c.drawLine(14f, 3f, 14f, 8f, p); c.drawLine(14f, 8f, 19f, 8f, p) }
            "group" -> { c.drawCircle(9f, 9f, 3.5f, p); c.drawCircle(17f, 10f, 2.5f, p); val q = Path(); q.moveTo(3f, 20f); q.cubicTo(3f, 14f, 15f, 14f, 15f, 20f); c.drawPath(q, p) }
            "person" -> { c.drawCircle(12f, 8f, 4f, p); val q = Path(); q.moveTo(4f, 21f); q.cubicTo(4f, 13f, 20f, 13f, 20f, 21f); c.drawPath(q, p) }
            "settings" -> { c.drawCircle(12f, 12f, 3f, p); c.drawCircle(12f, 12f, 8f, p) }
            "bell" -> { val q = Path(); q.moveTo(6f, 17f); q.lineTo(6f, 11f); q.cubicTo(6f, 5f, 18f, 5f, 18f, 11f); q.lineTo(18f, 17f); q.lineTo(20f, 18f); q.lineTo(4f, 18f); q.close(); c.drawPath(q, p); c.drawLine(10f, 21f, 14f, 21f, p) }
            "image" -> { c.drawRoundRect(RectF(3f, 5f, 21f, 19f), 2f, 2f, p); c.drawCircle(9f, 10f, 1.8f, f); val q = Path(); q.moveTo(4f, 18f); q.lineTo(10f, 12f); q.lineTo(14f, 16f); q.lineTo(16f, 14f); q.lineTo(20f, 18f); c.drawPath(q, p) }
            "cloud" -> { val q = Path(); q.moveTo(7f, 18f); q.cubicTo(2f, 18f, 2f, 11f, 8f, 11f); q.cubicTo(9f, 5f, 19f, 6f, 18f, 12f); q.cubicTo(23f, 12f, 23f, 18f, 18f, 18f); q.close(); c.drawPath(q, p) }
            "film" -> { c.drawRoundRect(RectF(3f, 6f, 21f, 18f), 2f, 2f, p); val q = Path(); q.moveTo(10f, 9f); q.lineTo(15f, 12f); q.lineTo(10f, 15f); q.close(); c.drawPath(q, f) }
            "logout" -> { val q = Path(); q.moveTo(10f, 4f); q.lineTo(4f, 4f); q.lineTo(4f, 20f); q.lineTo(10f, 20f); c.drawPath(q, p); c.drawLine(9f, 12f, 20f, 12f, p); val r = Path(); r.moveTo(16f, 8f); r.lineTo(20f, 12f); r.lineTo(16f, 16f); c.drawPath(r, p) }
            "bookmark" -> { val q = Path(); q.moveTo(7f, 4f); q.lineTo(17f, 4f); q.lineTo(17f, 20f); q.lineTo(12f, 16f); q.lineTo(7f, 20f); q.close(); c.drawPath(q, p) }
            "lock" -> { c.drawRoundRect(RectF(5f, 10f, 19f, 20f), 2f, 2f, p); val q = Path(); q.moveTo(8f, 10f); q.lineTo(8f, 7f); q.arcTo(RectF(8f, 3f, 16f, 11f), 180f, 180f); q.lineTo(16f, 10f); c.drawPath(q, p) }
            "phone" -> { val q = Path(); q.moveTo(6f, 4f); q.lineTo(10f, 4f); q.lineTo(11f, 9f); q.lineTo(9f, 10.5f); q.cubicTo(10f, 13f, 11f, 14f, 13.5f, 15f); q.lineTo(15f, 13f); q.lineTo(20f, 14f); q.lineTo(20f, 18f); q.cubicTo(11f, 21f, 3f, 12f, 6f, 4f); c.drawPath(q, p) }
            "video" -> { c.drawRoundRect(RectF(3f, 7f, 16f, 17f), 2.5f, 2.5f, p); val q = Path(); q.moveTo(16f, 11f); q.lineTo(21f, 8f); q.lineTo(21f, 16f); q.lineTo(16f, 13f); q.close(); c.drawPath(q, p) }
            "speaker" -> { val q = Path(); q.moveTo(4f, 9f); q.lineTo(8f, 9f); q.lineTo(13f, 5f); q.lineTo(13f, 19f); q.lineTo(8f, 15f); q.lineTo(4f, 15f); q.close(); c.drawPath(q, p); val r = Path(); r.moveTo(16f, 9f); r.arcTo(RectF(11f, 8f, 19f, 16f), -50f, 100f); c.drawPath(r, p) }
            "micoff" -> { c.drawRoundRect(RectF(9f, 3f, 15f, 14f), 3f, 3f, p); c.drawLine(12f, 18f, 12f, 21f, p); c.drawLine(4f, 4f, 20f, 20f, p) }
            "videooff" -> { c.drawRoundRect(RectF(3f, 7f, 16f, 17f), 2.5f, 2.5f, p); c.drawLine(4f, 4f, 20f, 20f, p) }
            "hangup" -> { val q = Path(); q.moveTo(3f, 14f); q.cubicTo(8f, 9f, 16f, 9f, 21f, 14f); q.lineTo(19f, 17f); q.lineTo(15f, 15.5f); q.lineTo(15f, 13f); q.cubicTo(13f, 12f, 11f, 12f, 9f, 13f); q.lineTo(9f, 15.5f); q.lineTo(5f, 17f); q.close(); c.drawPath(q, f) }
            else -> c.drawCircle(12f, 12f, 4f, f)
        }
        c.restore()
    }
    override fun setAlpha(a: Int) { p.alpha = a; f.alpha = a }
    override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf; f.colorFilter = cf }
    @Deprecated("required override") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

fun icon(ctx: Context, kind: String, color: Int, sizeDp: Int = 24): ImageView = ImageView(ctx).also {
    it.setImageDrawable(IconDrawable(kind, color))
    it.layoutParams = LinearLayout.LayoutParams(ctx.dp(sizeDp), ctx.dp(sizeDp))
    it.scaleType = ImageView.ScaleType.FIT_CENTER
}

/** Gentle "press" feedback: the view shrinks a little while a finger is down. Never consumes the touch. */
@android.annotation.SuppressLint("ClickableViewAccessibility")
fun View.pressScale(to: Float = 0.94f) {
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            android.view.MotionEvent.ACTION_DOWN -> v.animate().scaleX(to).scaleY(to).setDuration(90).start()
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> v.animate().scaleX(1f).scaleY(1f).setDuration(140).setInterpolator(android.view.animation.OvershootInterpolator(2f)).start()
        }
        false
    }
}

/** Round touch target around an icon (toolbar buttons). */
fun iconButton(ctx: Context, th: Theme, kind: String, color: Int, onClick: () -> Unit): FrameLayout {
    val box = FrameLayout(ctx)
    val sz = ctx.dp(44)
    box.layoutParams = LinearLayout.LayoutParams(sz, sz)
    val img = ImageView(ctx)
    img.setImageDrawable(IconDrawable(kind, color))
    box.addView(img, FrameLayout.LayoutParams(ctx.dp(24), ctx.dp(24), Gravity.CENTER))
    box.background = ripple(null, th.ripple)
    box.isClickable = true
    box.contentDescription = kind
    box.setOnClickListener { onClick() }
    box.pressScale(0.85f)
    return box
}

fun EditText.styled(th: Theme, hint: String): EditText {
    typeface = Fonts.regular(context)
    this.hint = hint
    setHintTextColor(th.muted)
    setTextColor(th.text)
    setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 16f)
    textDirection = View.TEXT_DIRECTION_FIRST_STRONG
    background = null
    return this
}

fun primaryButton(ctx: Context, th: Theme, text: String, onClick: () -> Unit): TextView {
    val b = label(ctx, text, 16f, 0xFFFFFFFF.toInt(), true)
    b.gravity = Gravity.CENTER
    b.background = ripple(rounded(th.accentFill, ctx.dpf(10f)), 0x33FFFFFF)
    b.setPadding(ctx.dp(16), ctx.dp(13), ctx.dp(16), ctx.dp(13))
    b.isClickable = true
    b.setOnClickListener { onClick() }
    b.pressScale(0.97f)
    return b
}

fun textButton(ctx: Context, th: Theme, text: String, color: Int = th.accent, onClick: () -> Unit): TextView {
    val b = label(ctx, text, 15f, color, true)
    b.gravity = Gravity.CENTER
    b.setPadding(ctx.dp(14), ctx.dp(12), ctx.dp(14), ctx.dp(12))
    b.background = ripple(null, th.ripple)
    b.isClickable = true
    b.setOnClickListener { onClick() }
    b.pressScale(0.96f)
    return b
}
