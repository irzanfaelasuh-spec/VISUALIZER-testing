package dev.lumenwave.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.max
import kotlin.math.min

object VisualizerOverlay {
    private val main = Handler(Looper.getMainLooper())
    private var windowManager: WindowManager? = null
    private var panel: VisualizerPanel? = null
    private var params: WindowManager.LayoutParams? = null

    fun attach(context: Context) {
        main.post {
            if (panel != null) return@post
            if (!android.provider.Settings.canDrawOverlays(context)) return@post
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val p = Prefs.from(context)
            val maxWidthDp = ((context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt() - 24).coerceAtLeast(220)
            val safeWidthDp = min(p.windowWidthDp, maxWidthDp)
            val view = VisualizerPanel(context.applicationContext, onStop = { AudioCaptureService.requestStop(context) })
            val lp = WindowManager.LayoutParams(
                dp(context, safeWidthDp), dp(context, p.windowHeightDp),
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                android.graphics.PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = dp(context, 20)
                y = dp(context, 150)
            }
            try {
                wm.addView(view, lp)
                windowManager = wm
                panel = view
                params = lp
            } catch (_: Exception) {
                panel = null
                params = null
                windowManager = null
            }
        }
    }

    fun update(values: FloatArray) {
        val copy = values.copyOf()
        main.post { panel?.setSpectrum(copy) }
    }

    fun refresh(context: Context) {
        main.post {
            panel?.refreshSettings()
            panel?.visibility = if (Prefs.from(context).visualizerEnabled) View.VISIBLE else View.GONE
            val p = Prefs.from(context)
            val lp = params
            if (lp != null && panel != null) {
                val maxWidthDp = ((context.resources.displayMetrics.widthPixels / context.resources.displayMetrics.density).toInt() - 24).coerceAtLeast(220)
                if (panel!!.isMaximized) {
                    lp.width = context.resources.displayMetrics.widthPixels - dp(context, 24)
                    lp.x = dp(context, 12)
                } else {
                    lp.width = dp(context, min(p.windowWidthDp, maxWidthDp))
                }
                if (!panel!!.isCollapsed && !panel!!.isMaximized) lp.height = dp(context, p.windowHeightDp)
                try { windowManager?.updateViewLayout(panel, lp) } catch (_: Exception) { }
            }
        }
    }

    fun detach() {
        main.post {
            val v = panel
            if (v != null) {
                try { windowManager?.removeView(v) } catch (_: Exception) { }
            }
            panel = null
            params = null
            windowManager = null
        }
    }

    private fun dp(context: Context, value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    private class VisualizerPanel(
        context: Context,
        private val onStop: () -> Unit
    ) : FrameLayout(context) {
        private val prefs = Prefs.from(context)
        private val bars = SpectrumBarsView(context)
        private val dragArea = LinearLayout(context)
        private var normalHeight = dp(prefs.windowHeightDp)
        private var normalWidth = dp(prefs.windowWidthDp)
        var isCollapsed = false
            private set
        var isMaximized = false
            private set

        init {
            val bg = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(19, 26, 42), Color.rgb(9, 13, 22))
            ).apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.argb(115, 143, 166, 211))
                alpha = (prefs.opacity * 2.55f).toInt().coerceIn(90, 255)
            }
            background = bg
            clipToPadding = false
            elevation = dp(12).toFloat()

            val column = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(12), dp(9), dp(12), dp(8))
            }
            val header = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            dragArea.orientation = LinearLayout.VERTICAL
            dragArea.gravity = Gravity.CENTER_VERTICAL
            val title = TextView(context).apply {
                text = "LUMENWAVE"
                textSize = 11.5f
                letterSpacing = 0.13f
                setTextColor(Color.rgb(231, 239, 255))
                typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            }
            val subtitle = TextView(context).apply {
                text = "LIVE SPECTRUM  /  MEDIA CAPTURE"
                textSize = 8f
                letterSpacing = 0.09f
                setTextColor(Color.rgb(125, 146, 181))
            }
            dragArea.addView(title)
            dragArea.addView(subtitle)
            header.addView(dragArea, LinearLayout.LayoutParams(0, dp(34), 1f))

            val controls = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            controls.addView(dotButton("#F16F73") { onStop() })
            controls.addView(dotButton("#E9BD63") { toggleCollapse() }, lpMarginStart(7))
            controls.addView(dotButton("#5CCB9A") { toggleMaximize() }, lpMarginStart(7))
            header.addView(controls)
            column.addView(header, LinearLayout.LayoutParams(-1, dp(38)))

            val separator = View(context).apply { setBackgroundColor(Color.argb(30, 180, 202, 245)) }
            column.addView(separator, LinearLayout.LayoutParams(-1, dp(1)))
            column.addView(bars, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(7) })

            val resize = TextView(context).apply {
                text = "◢"
                textSize = 12f
                gravity = Gravity.CENTER
                setTextColor(Color.rgb(113, 139, 187))
                setPadding(dp(5), dp(2), dp(2), dp(0))
                setOnTouchListener { _, event -> handleResize(event); true }
            }
            addView(column, LayoutParams(-1, -1))
            addView(resize, LayoutParams(dp(28), dp(25), Gravity.BOTTOM or Gravity.END))
            dragArea.setOnTouchListener { _, event -> handleDrag(event); true }
            refreshSettings()
        }

        private fun dotButton(hex: String, action: () -> Unit): View {
            val v = View(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor(hex))
                    setStroke(dp(1), Color.argb(80, 255, 255, 255))
                }
                elevation = dp(2).toFloat()
                isClickable = true
                isFocusable = true
                setOnClickListener { action() }
            }
            return v.also { it.layoutParams = LinearLayout.LayoutParams(dp(12), dp(12)) }
        }

        private fun lpMarginStart(margin: Int) = LinearLayout.LayoutParams(dp(12), dp(12)).apply { marginStart = dp(margin) }

        private fun handleDrag(event: MotionEvent): Boolean {
            val lp = VisualizerOverlay.params ?: return true
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = event.rawX.toInt(); dragStartY = event.rawY.toInt()
                    windowStartX = lp.x; windowStartY = lp.y
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val maxX = (resources.displayMetrics.widthPixels - lp.width).coerceAtLeast(0)
                    val maxY = (resources.displayMetrics.heightPixels - lp.height).coerceAtLeast(0)
                    lp.x = (windowStartX + event.rawX.toInt() - dragStartX).coerceIn(0, maxX)
                    lp.y = (windowStartY + event.rawY.toInt() - dragStartY).coerceIn(0, maxY)
                    try { VisualizerOverlay.windowManager?.updateViewLayout(this, lp) } catch (_: Exception) { }
                    return true
                }
            }
            return true
        }

        private var dragStartX = 0
        private var dragStartY = 0
        private var windowStartX = 0
        private var windowStartY = 0
        private var resizeStartX = 0f
        private var resizeStartY = 0f
        private var resizeStartW = 0
        private var resizeStartH = 0

        private fun handleResize(event: MotionEvent) {
            val lp = VisualizerOverlay.params ?: return
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    resizeStartX = event.rawX; resizeStartY = event.rawY
                    resizeStartW = lp.width; resizeStartH = lp.height
                }
                MotionEvent.ACTION_MOVE -> {
                    val maxWidth = (resources.displayMetrics.widthPixels - dp(24)).coerceAtLeast(dp(220))
                    val maxHeight = (resources.displayMetrics.heightPixels - dp(80)).coerceAtLeast(dp(180))
                    val w = min(max(dp(220), resizeStartW + (event.rawX - resizeStartX).toInt()), maxWidth)
                    val h = min(max(dp(110), resizeStartH + (event.rawY - resizeStartY).toInt()), maxHeight)
                    lp.width = w; lp.height = h
                    isCollapsed = false; isMaximized = false
                    try { VisualizerOverlay.windowManager?.updateViewLayout(this, lp) } catch (_: Exception) { }
                    prefs.windowWidthDp = (w / resources.displayMetrics.density).toInt()
                    prefs.windowHeightDp = (h / resources.displayMetrics.density).toInt()
                    normalWidth = w; normalHeight = h
                }
            }
        }

        private fun toggleCollapse() {
            val lp = VisualizerOverlay.params ?: return
            if (!isCollapsed) {
                if (!isMaximized) normalHeight = lp.height
                bars.visibility = View.GONE
                lp.height = dp(54)
                isCollapsed = true
            } else {
                bars.visibility = View.VISIBLE
                lp.height = if (isMaximized) dp(190) else normalHeight
                isCollapsed = false
            }
            try { VisualizerOverlay.windowManager?.updateViewLayout(this, lp) } catch (_: Exception) { }
        }

        private fun toggleMaximize() {
            val lp = VisualizerOverlay.params ?: return
            if (!isMaximized) {
                if (!isCollapsed) normalHeight = lp.height
                normalWidth = lp.width
                val displayWidth = resources.displayMetrics.widthPixels
                lp.width = (displayWidth - dp(24)).coerceAtLeast(dp(220))
                lp.x = dp(12)
                lp.height = dp(190)
                bars.visibility = View.VISIBLE
                isCollapsed = false
                isMaximized = true
            } else {
                lp.width = normalWidth
                lp.height = if (isCollapsed) dp(54) else normalHeight
                bars.visibility = if (isCollapsed) View.GONE else View.VISIBLE
                isMaximized = false
            }
            try { VisualizerOverlay.windowManager?.updateViewLayout(this, lp) } catch (_: Exception) { }
        }

        fun setSpectrum(values: FloatArray) { bars.setValues(values) }

        fun refreshSettings() {
            val newBg = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(19, 26, 42), Color.rgb(9, 13, 22))
            ).apply {
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.argb(115, 143, 166, 211))
                alpha = (prefs.opacity * 2.55f).toInt().coerceIn(90, 255)
            }
            background = newBg
            bars.invalidate()
        }

        private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    }

    private class SpectrumBarsView(context: Context) : View(context) {
        private val prefs = Prefs.from(context)
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        private val peakPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val baselinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var values = FloatArray(prefs.barCount)
        private var peakValues = FloatArray(prefs.barCount)

        init {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            baselinePaint.color = Color.argb(45, 150, 180, 230)
            peakPaint.strokeWidth = dp(2f)
        }

        fun setValues(next: FloatArray) {
            if (values.size != prefs.barCount) {
                values = FloatArray(prefs.barCount)
                peakValues = FloatArray(prefs.barCount)
            }
            for (i in values.indices) values[i] = next.getOrElse(i) { 0f }.coerceIn(0f, 1f)
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val count = prefs.barCount
            val pad = dp(4f)
            val totalW = width - pad * 2
            val gap = min(dp(prefs.barGap.toFloat()), totalW / (count * 4.5f))
            val barW = ((totalW - gap * (count - 1)) / count).coerceAtLeast(dp(1f))
            val floor = height - dp(6f)
            val maxH = (height - dp(12f)) * prefs.barHeight / 100f
            canvas.drawLine(pad, floor, width - pad, floor, baselinePaint)
            for (i in 0 until count) {
                val x = pad + i * (barW + gap)
                val level = values.getOrElse(i) { 0f }
                peakValues[i] = max(level, peakValues[i] - 0.012f)
                val h = max(dp(2f), maxH * level)
                val rect = RectF(x, floor - h, x + barW, floor)
                val color = colorAt(i, count)
                fill.color = color
                fill.alpha = 238
                fill.clearShadowLayer()
                if (prefs.glow) fill.setShadowLayer(dp(7f), 0f, 0f, color)
                val radius = min(barW / 2f, dp(5f))
                canvas.drawRoundRect(rect, radius, radius, fill)
                fill.clearShadowLayer()
                if (prefs.peaks) {
                    peakPaint.color = color
                    peakPaint.alpha = 215
                    val peakY = floor - max(dp(3f), maxH * peakValues[i]) - dp(3f)
                    canvas.drawLine(x, peakY, x + barW, peakY, peakPaint)
                }
            }
        }

        private fun colorAt(index: Int, count: Int): Int {
            return when (prefs.colorMode) {
                "TRI" -> {
                    val t = if (count <= 1) 0f else index.toFloat() / (count - 1)
                    if (t < 0.5f) blend(prefs.c1, prefs.c2, t * 2f) else blend(prefs.c2, prefs.c3, (t - 0.5f) * 2f)
                }
                "MONO" -> prefs.c1
                else -> {
                    val hsv = floatArrayOf((275f - (index.toFloat() / max(1, count - 1)) * 285f + 360f) % 360f, 0.72f, 1f)
                    Color.HSVToColor(hsv)
                }
            }
        }

        private fun blend(a: Int, b: Int, t: Float): Int {
            val f = t.coerceIn(0f, 1f)
            return Color.rgb(
                (Color.red(a) * (1 - f) + Color.red(b) * f).toInt(),
                (Color.green(a) * (1 - f) + Color.green(b) * f).toInt(),
                (Color.blue(a) * (1 - f) + Color.blue(b) * f).toInt()
            )
        }

        private fun dp(v: Float) = v * resources.displayMetrics.density
    }
}
