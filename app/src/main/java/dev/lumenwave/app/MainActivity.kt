package dev.lumenwave.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var nav: LinearLayout
    private var currentTab = "HOME"
    private var splashShown = false
    private var renderingSetup = false
    private var headerStatus: TextView? = null

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_REQUEST) {
            runOnUiThread {
                Toast.makeText(this, if (grantResult == PackageManager.PERMISSION_GRANTED) "Shizuku authorized" else "Shizuku permission not granted", Toast.LENGTH_SHORT).show()
                if (renderingSetup) renderSetup() else renderTab(currentTab)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs.from(this)
        window.statusBarColor = Color.rgb(8, 11, 18)
        window.navigationBarColor = Color.rgb(8, 11, 18)
        Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        showSplash()
    }

    override fun onResume() {
        super.onResume()
        if (splashShown && ::root.isInitialized) {
            if (renderingSetup) renderSetup() else { refreshServiceStatus(); renderTab(currentTab) }
        }
    }

    override fun onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener) } catch (_: Exception) { }
        super.onDestroy()
    }

    private fun showSplash() {
        splashShown = false
        val splash = FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(9, 15, 27), Color.rgb(15, 27, 47), Color.rgb(8, 11, 18))
            )
        }
        val stack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val icon = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        stack.addView(icon, LinearLayout.LayoutParams(dp(90), dp(90)))
        val title = TextView(this).apply {
            text = "LUMENWAVE"
            textSize = 24f
            letterSpacing = 0.20f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(Color.rgb(239, 245, 255))
            gravity = Gravity.CENTER
        }
        stack.addView(title, marginParams(-1, -2, top = 18))
        val subtitle = TextView(this).apply {
            text = "AUDIO, IN MOTION."
            textSize = 10f
            letterSpacing = 0.24f
            setTextColor(Color.rgb(126, 157, 202))
            gravity = Gravity.CENTER
        }
        stack.addView(subtitle, marginParams(-1, -2, top = 7))
        val progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            progressTintList = android.content.res.ColorStateList.valueOf(CYAN)
            indeterminateTintList = android.content.res.ColorStateList.valueOf(CYAN)
        }
        stack.addView(progress, LinearLayout.LayoutParams(dp(110), dp(3)).apply { topMargin = dp(30) })
        splash.addView(stack, FrameLayout.LayoutParams(-1, -1))
        setContentView(splash)
        stack.alpha = 0f
        stack.translationY = dp(12).toFloat()
        stack.animate().alpha(1f).translationY(0f).setDuration(580).start()
        splash.postDelayed({
            splashShown = true
            if (!prefs.setupDone) renderSetup() else showMainShell()
        }, 1050)
    }

    private fun renderSetup() {
        renderingSetup = true
        buildShell(showNav = false)
        val page = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(eyebrow("FIRST-RUN CONFIGURATION"))
        page.addView(heading("Prepare your visualizer"))
        page.addView(body("Complete the access checks below. Green means ready; amber means an action is still needed."), marginParams(-1, -2, top = 7, bottom = 18))

        val shizukuReady = isShizukuAuthorized()
        val overlayReady = Settings.canDrawOverlays(this)
        val batteryReady = isIgnoringBatteryOptimizations()
        val notificationReady = hasNotificationPermission()

        page.addView(permissionCard(
            "01", "Authorize Shizuku", "Connect to the Shizuku service and approve LumenWave.",
            shizukuReady, if (shizukuReady) "AUTHORIZED" else if (isShizukuInstalled()) "ACTION REQUIRED" else "INSTALL SHIZUKU",
            "Configure" ) { handleShizukuSetup() })
        page.addView(permissionCard(
            "02", "Allow floating windows", "Let the visualizer appear above games and other apps.",
            overlayReady, if (overlayReady) "GRANTED" else "ACTION REQUIRED", "Configure"
        ) { openOverlaySettings() })
        page.addView(permissionCard(
            "03", "Allow background operation", "Exclude LumenWave from battery optimization for a more reliable session.",
            batteryReady, if (batteryReady) "UNRESTRICTED" else "ACTION REQUIRED", "Configure"
        ) { requestBatteryExemption() })
        page.addView(permissionCard(
            "04", "Allow notifications", "Keep the active-session notification visible while capture is running.",
            notificationReady, if (notificationReady) "GRANTED" else "ACTION REQUIRED", "Configure"
        ) { requestNotificationPermission() })

        val criticalReady = shizukuReady && overlayReady && batteryReady && notificationReady
        val next = actionButton("NEXT  →", enabled = criticalReady) {
            prefs.setupDone = true
            renderingSetup = false
            showMainShell()
        }
        page.addView(next, marginParams(-1, 54, top = 18))
        page.addView(body("Audio capture consent is requested separately when you start a session. LumenWave does not record or save audio."), marginParams(-1, -2, top = 12, bottom = 28))
        setShellContent(page)
    }

    private fun showMainShell() {
        renderingSetup = false
        buildShell(showNav = true)
        renderTab(currentTab)
    }

    private fun buildShell(showNav: Boolean) {
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            setPadding(dp(18), dp(12), dp(18), 0)
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(dp(18), dp(12) + insets.systemWindowInsetTop, dp(18), insets.systemWindowInsetBottom)
                insets
            }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val logo = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(38), dp(38)))
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(10), 0, 0, 0)
        }
        brand.addView(TextView(this).apply {
            text = "LUMENWAVE"
            textSize = 15f
            letterSpacing = 0.12f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(TEXT)
        })
        brand.addView(TextView(this).apply {
            text = "FLOATING AUDIO VISUALIZER"
            textSize = 8.5f
            letterSpacing = 0.13f
            setTextColor(MUTED)
        })
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val live = statusPill(if (AudioCaptureService.running) "LIVE" else "READY", AudioCaptureService.running)
        headerStatus = live
        header.addView(live)
        root.addView(header, LinearLayout.LayoutParams(-1, dp(50)))

        if (showNav) {
            val divider = View(this).apply { setBackgroundColor(STROKE) }
            root.addView(divider, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(11); bottomMargin = dp(10) })
            val horizontal = HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                clipToPadding = false
            }
            nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            horizontal.addView(nav)
            root.addView(horizontal, LinearLayout.LayoutParams(-1, dp(42)))
            listOf("HOME", "VISUALIZER", "APPEARANCE", "ACCESS", "ABOUT").forEach { tab ->
                val item = TextView(this).apply {
                    text = tab
                    textSize = 9f
                    letterSpacing = 0.08f
                    gravity = Gravity.CENTER
                    setPadding(dp(13), 0, dp(13), 0)
                    setOnClickListener { currentTab = tab; renderTab(tab) }
                }
                nav.addView(item, LinearLayout.LayoutParams(-2, -1).apply { marginEnd = dp(5) })
            }
            refreshNavSelection()
        } else {
            val divider = View(this).apply { setBackgroundColor(STROKE) }
            root.addView(divider, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(12); bottomMargin = dp(12) })
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            setPadding(0, dp(10), 0, dp(26))
        }
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content, ViewGroup.LayoutParams(-1, -2))
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = TextView(this).apply {
            text = "LUMENWAVE  /  v1.0.0  /  ANDROID 10–16"
            textSize = 8f
            letterSpacing = 0.08f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(77, 91, 117))
            setPadding(0, dp(10), 0, dp(10))
        }
        root.addView(footer, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)
    }

    private fun refreshServiceStatus() {
        val live = AudioCaptureService.running
        headerStatus?.apply {
            text = if (live) "●  LIVE" else "●  READY"
            setTextColor(if (live) GREEN else AMBER)
            background = rounded(if (live) Color.rgb(19, 48, 43) else Color.rgb(50, 39, 25), 20)
        }
    }

    private fun refreshNavSelection() {
        if (!::nav.isInitialized) return
        for (i in 0 until nav.childCount) {
            val item = nav.getChildAt(i) as TextView
            val active = item.text.toString() == currentTab
            item.setTextColor(if (active) Color.rgb(227, 239, 255) else MUTED)
            item.background = if (active) rounded(Color.rgb(31, 47, 73), 11, Color.rgb(53, 76, 112)) else transparent()
        }
    }

    private fun renderTab(tab: String) {
        if (!::content.isInitialized) return
        content.removeAllViews()
        refreshNavSelection()
        when (tab) {
            "VISUALIZER" -> renderVisualizerTab()
            "APPEARANCE" -> renderAppearanceTab()
            "ACCESS" -> renderAccessTab()
            "ABOUT" -> renderAboutTab()
            else -> renderHomeTab()
        }
    }

    private fun renderHomeTab() {
        content.addView(eyebrow("CONTROL CENTER  /  01"))
        content.addView(heading("Sound, in motion."))
        content.addView(body("A desktop-inspired spectrum layer that floats above your apps."), marginParams(-1, -2, top = 6, bottom = 16))

        val hero = card()
        hero.addView(TextView(this).apply {
            text = if (AudioCaptureService.running) "SESSION RUNNING" else "READY WHEN YOU ARE"
            textSize = 9f; letterSpacing = 0.14f; setTextColor(if (AudioCaptureService.running) GREEN else CYAN)
        })
        hero.addView(TextView(this).apply {
            text = if (AudioCaptureService.running) "Your spectrum is live" else "Bring your audio to life"
            textSize = 22f; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); setTextColor(TEXT)
        }, marginParams(-1, -2, top = 7))
        hero.addView(TextView(this).apply {
            text = if (AudioCaptureService.running) "The floating window can be moved, resized, minimized, or expanded." else "Start a capture session, then leave LumenWave open in the background while you play music or a compatible game."
            textSize = 11f; setTextColor(MUTED); setLineSpacing(dp(3).toFloat(), 1f)
        }, marginParams(-1, -2, top = 7, bottom = 14))
        val primary = actionButton(if (AudioCaptureService.running) "STOP VISUALIZER" else "START VISUALIZER  →", enabled = true) {
            if (AudioCaptureService.running) stopVisualizer() else beginVisualizer()
        }
        hero.addView(primary, LinearLayout.LayoutParams(-1, dp(48)))
        content.addView(hero)

        val preview = card()
        preview.addView(rowLabel("LIVE STYLE PREVIEW", "${prefs.barCount} BANDS"))
        preview.addView(PreviewSpectrum(this), LinearLayout.LayoutParams(-1, dp(96)).apply { topMargin = dp(12) })
        preview.addView(body("The preview is decorative. Live bars react to captured playback after you grant the system capture prompt."), marginParams(-1, -2, top = 6))
        content.addView(preview, marginParams(-1, -2, top = 13))

        val stats = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        stats.addView(statCard("ANDROID", "10–16"), LinearLayout.LayoutParams(0, dp(75), 1f))
        stats.addView(statCard("BARS", prefs.barCount.toString()), LinearLayout.LayoutParams(0, dp(75), 1f).apply { marginStart = dp(9) })
        stats.addView(statCard("CAPTURE", "SYSTEM"), LinearLayout.LayoutParams(0, dp(75), 1f).apply { marginStart = dp(9) })
        content.addView(stats, marginParams(-1, -2, top = 12, bottom = 12))

        val note = card()
        note.addView(rowLabel("CAPTURE NOTE", "READ THIS"))
        note.addView(body("Android only shares playback from apps that permit capture. Protected streaming apps, calls, and some games may return silence. Shizuku cannot override those restrictions."), marginParams(-1, -2, top = 9))
        content.addView(note)
    }

    private fun renderVisualizerTab() {
        content.addView(eyebrow("ENGINE SETTINGS  /  02"))
        content.addView(heading("Visualizer engine"))
        content.addView(body("Tune the response curve and geometry of your spectrum."), marginParams(-1, -2, top = 6, bottom = 14))
        val control = card()
        addSwitchRow(control, "Visualizer enabled", "Turn this off to stop the active capture session.", prefs.visualizerEnabled) { enabled ->
            prefs.visualizerEnabled = enabled
            if (!enabled && AudioCaptureService.running) {
                AudioCaptureService.requestStop(this)
                root.postDelayed({ refreshServiceStatus(); renderTab("VISUALIZER") }, 250)
            } else {
                AudioCaptureService.requestUpdate(this)
            }
        }
        content.addView(control)

        val tuning = card()
        tuning.addView(rowLabel("SPECTRUM", "SIGNAL RESPONSE"))
        addSeekSetting(tuning, "Bar count", "${prefs.barCount} bars", 12, 72, prefs.barCount) { prefs.barCount = it }
        addSeekSetting(tuning, "Sensitivity", "${"%.1f".format(prefs.sensitivity)}×", 5, 35, (prefs.sensitivity * 10).toInt()) { prefs.sensitivity = it / 10f }
        addSeekSetting(tuning, "Smoothing", "${(prefs.smoothing * 100).toInt()}%", 10, 95, (prefs.smoothing * 100).toInt()) { prefs.smoothing = it / 100f }
        addSeekSetting(tuning, "Maximum bar height", "${prefs.barHeight}%", 30, 100, prefs.barHeight) { prefs.barHeight = it }
        addSeekSetting(tuning, "Bar spacing", "${prefs.barGap} px", 1, 9, prefs.barGap) { prefs.barGap = it }
        content.addView(tuning, marginParams(-1, -2, top = 12))

        val window = card()
        window.addView(rowLabel("FLOATING WINDOW", "DRAG + RESIZE"))
        val maxWindowDp = ((resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt() - 24).coerceAtLeast(220)
        val safeWindowWidth = prefs.windowWidthDp.coerceIn(220, maxWindowDp)
        addSeekSetting(window, "Window width", "$safeWindowWidth dp", 220, maxWindowDp, safeWindowWidth) { prefs.windowWidthDp = it }
        addSeekSetting(window, "Window height", "${prefs.windowHeightDp} dp", 110, 320, prefs.windowHeightDp) { prefs.windowHeightDp = it }
        window.addView(body("Drag the header to move the panel. Pull the lower-right corner to resize it. Red stops, yellow minimizes, green expands."), marginParams(-1, -2, top = 8))
        content.addView(window, marginParams(-1, -2, top = 12))
        content.addView(actionButton("APPLY TO FLOATING WINDOW", true) { AudioCaptureService.requestUpdate(this); Toast.makeText(this, "Settings applied", Toast.LENGTH_SHORT).show() }, marginParams(-1, 48, top = 13))
    }

    private fun renderAppearanceTab() {
        content.addView(eyebrow("VISUAL IDENTITY  /  03"))
        content.addView(heading("Make it yours."))
        content.addView(body("Choose a spectrum palette and refine the glass-like panel."), marginParams(-1, -2, top = 6, bottom = 14))
        val modes = card()
        modes.addView(rowLabel("COLOR ENGINE", "PALETTE MODE"))
        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("RAINBOW" to "Rainbow", "TRI" to "3-tone", "MONO" to "Single").forEach { (key, label) ->
            val b = smallButton(label, prefs.colorMode == key) {
                prefs.colorMode = key; AudioCaptureService.requestUpdate(this); renderTab("APPEARANCE")
            }
            modeRow.addView(b, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(6) })
        }
        modes.addView(modeRow, marginParams(-1, -2, top = 12))
        val preview = PreviewSpectrum(this)
        modes.addView(preview, LinearLayout.LayoutParams(-1, dp(84)).apply { topMargin = dp(13) })
        content.addView(modes)

        val colors = card()
        colors.addView(rowLabel("CUSTOM THREE-TONE", "EDIT COLORS"))
        addColorRow(colors, "COLOR 01  /  LOW", 1, prefs.c1)
        addColorRow(colors, "COLOR 02  /  MID", 2, prefs.c2)
        addColorRow(colors, "COLOR 03  /  HIGH", 3, prefs.c3)
        colors.addView(body("Three-tone mode blends the three selected colors smoothly across the spectrum."), marginParams(-1, -2, top = 8))
        content.addView(colors, marginParams(-1, -2, top = 12))

        val effects = card()
        effects.addView(rowLabel("MATERIAL + FX", "PANEL STYLE"))
        addSeekSetting(effects, "Panel opacity", "${prefs.opacity}%", 35, 100, prefs.opacity) { prefs.opacity = it }
        addSwitchRow(effects, "Soft glow", "Add a subtle luminous edge to each bar.", prefs.glow) { prefs.glow = it }
        addSwitchRow(effects, "Peak indicators", "Show the short peak-hold markers above bars.", prefs.peaks) { prefs.peaks = it }
        content.addView(effects, marginParams(-1, -2, top = 12))
        content.addView(actionButton("APPLY APPEARANCE", true) { AudioCaptureService.requestUpdate(this); renderTab("APPEARANCE") }, marginParams(-1, 48, top = 13))
    }

    private fun renderAccessTab() {
        content.addView(eyebrow("SYSTEM INTEGRATION  /  04"))
        content.addView(heading("Permissions & access"))
        content.addView(body("Android controls each permission. You can revisit these settings here at any time."), marginParams(-1, -2, top = 6, bottom = 14))
        content.addView(permissionCard("01", "Shizuku service", "ADB or root-backed authorization used for integration status.", isShizukuAuthorized(), if (isShizukuAuthorized()) "AUTHORIZED" else "NOT READY", "Manage") { handleShizukuSetup() })
        content.addView(permissionCard("02", "Display over other apps", "Required to place the draggable visualizer above games.", Settings.canDrawOverlays(this), if (Settings.canDrawOverlays(this)) "GRANTED" else "NOT GRANTED", "Manage") { openOverlaySettings() })
        content.addView(permissionCard("03", "Background execution", "Battery optimization exemption is recommended for long sessions.", isIgnoringBatteryOptimizations(), if (isIgnoringBatteryOptimizations()) "UNRESTRICTED" else "OPTIMIZED", "Manage") { requestBatteryExemption() })
        content.addView(permissionCard("04", "Notifications", "Shows an ongoing service notification with a stop action.", hasNotificationPermission(), if (hasNotificationPermission()) "GRANTED" else "NOT GRANTED", "Manage") { requestNotificationPermission() })
        val audioPermissionReady = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        content.addView(permissionCard("05", "Android audio permission", "Required by Android playback capture. This permission is not used to listen through the microphone.", audioPermissionReady, if (audioPermissionReady) "GRANTED" else "NOT GRANTED", "Manage") {
            requestAudioPermission(AUDIO_PERMISSION_MANAGE_REQUEST)
        })
        content.addView(permissionCard("06", "MediaProjection consent", "Android asks for capture approval each time a new visualizer session starts.", true, "PER SESSION", "Details") {
            AlertDialog.Builder(this).setTitle("Playback capture")
                .setMessage("LumenWave uses Android AudioPlaybackCapture. It requires RECORD_AUDIO permission and a MediaProjection confirmation. Only media/game playback that allows capture can be received. The audio stream is processed in memory and is not saved.")
                .setPositiveButton("GOT IT", null).show()
        })
        content.addView(body("The overlay stays active after you leave the app while its foreground service is running. Force-stop, reboot, system cleanup, or manufacturer battery controls can still end the session."), marginParams(-1, -2, top = 13, bottom = 18))
    }

    private fun renderAboutTab() {
        content.addView(eyebrow("PROJECT DETAILS  /  05"))
        content.addView(heading("Built for the background."))
        content.addView(body("LumenWave Visualizer is a native Android project with a floating spectrum surface and live playback analysis."), marginParams(-1, -2, top = 6, bottom = 14))
        val details = card()
        listOf(
            "APP NAME" to "LumenWave Visualizer",
            "PROJECT" to "LumenWave",
            "VERSION" to "1.0.0 · build 1",
            "ANDROID SUPPORT" to "Android 10–16 (API 29–36)",
            "AUDIO SOURCE" to "Android Playback Capture",
            "PRIVILEGE BRIDGE" to "Shizuku API 13.1.5",
            "UI STYLE" to "macOS × Linux-inspired"
        ).forEach { (k, v) ->
            details.addView(rowLabel(k, v))
            if (k != "UI STYLE") details.addView(View(this).apply { setBackgroundColor(STROKE) }, LinearLayout.LayoutParams(-1, dp(1)).apply { topMargin = dp(9); bottomMargin = dp(9) })
        }
        content.addView(details)
        val limitations = card()
        limitations.addView(rowLabel("IMPORTANT LIMITATIONS", "PLATFORM RULES"))
        limitations.addView(body("• Internal audio capture requires Android 10 or newer.\n• Capture is blocked by apps with restrictive playback policies.\n• A fresh MediaProjection confirmation is required for each new capture session.\n• Closing the app UI is supported; force-stop is not.\n• Shizuku does not bypass Android's protected-audio policy."), marginParams(-1, -2, top = 10))
        content.addView(limitations, marginParams(-1, -2, top = 12))
        content.addView(actionButton("OPEN SHIZUKU DOWNLOAD PAGE", true) {
            openUrl("https://shizuku.rikka.app/download/")
        }, marginParams(-1, 48, top = 13))
    }

    private fun addSeekSetting(parent: LinearLayout, title: String, value: String, minValue: Int, maxValue: Int, current: Int, onChange: (Int) -> Unit) {
        val block = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val label = rowLabel(title, value)
        block.addView(label)
        val seek = SeekBar(this).apply {
            max = maxValue - minValue
            progress = (current - minValue).coerceIn(0, max)
            progressTintList = android.content.res.ColorStateList.valueOf(CYAN)
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.rgb(222, 235, 255))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(48, 61, 83))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val actual = minValue + progress
                        (label as LinearLayout).getChildAt(1).let { (it as TextView).text = when (title) {
                            "Sensitivity" -> "${"%.1f".format(actual / 10f)}×"
                            "Smoothing" -> "$actual%"
                            "Bar spacing" -> "$actual px"
                            "Maximum bar height" -> "$actual%"
                            "Panel opacity" -> "$actual%"
                            "Window width" -> "$actual dp"
                            "Window height" -> "$actual dp"
                            "Bar count" -> "$actual bars"
                            else -> actual.toString()
                        } }
                        onChange(actual)
                        AudioCaptureService.requestUpdate(this@MainActivity)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }
        block.addView(seek, LinearLayout.LayoutParams(-1, dp(34)))
        parent.addView(block, marginParams(-1, -2, top = 11))
    }

    private fun addSwitchRow(parent: LinearLayout, title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val textColumn = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        textColumn.addView(TextView(this).apply { text = title; textSize = 12f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setTextColor(TEXT) })
        textColumn.addView(TextView(this).apply { text = description; textSize = 10f; setTextColor(MUTED); setLineSpacing(dp(2).toFloat(), 1f) }, marginParams(-1, -2, top = 3))
        row.addView(textColumn, LinearLayout.LayoutParams(0, -2, 1f))
        val toggle = Switch(this).apply {
            isChecked = checked
            thumbTintList = android.content.res.ColorStateList.valueOf(if (checked) CYAN else Color.rgb(154, 166, 187))
            setOnCheckedChangeListener { _, value -> onChange(value); thumbTintList = android.content.res.ColorStateList.valueOf(if (value) CYAN else Color.rgb(154, 166, 187)) }
        }
        row.addView(toggle)
        parent.addView(row, marginParams(-1, -2, top = 11, bottom = 6))
    }

    private fun addColorRow(parent: LinearLayout, label: String, which: Int, color: Int) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(this).apply { text = label; textSize = 10f; letterSpacing = 0.05f; setTextColor(MUTED) }, LinearLayout.LayoutParams(0, dp(42), 1f))
        val swatch = View(this).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; cornerRadius = dp(8).toFloat(); setColor(color); setStroke(dp(1), Color.argb(110, 230, 240, 255)) }
            setOnClickListener { chooseColor(which) }
        }
        row.addView(swatch, LinearLayout.LayoutParams(dp(40), dp(25)))
        val edit = smallButton("EDIT", false) { chooseColor(which) }
        row.addView(edit, LinearLayout.LayoutParams(dp(62), dp(34)).apply { marginStart = dp(9) })
        parent.addView(row, marginParams(-1, -2, top = 8))
    }

    private fun chooseColor(which: Int) {
        val names = arrayOf("Ice cyan", "Sky blue", "Electric blue", "Violet", "Magenta", "Rose", "Amber", "Lime", "Mint", "White")
        val colors = intArrayOf(
            Color.rgb(91, 226, 214), Color.rgb(90, 180, 255), Color.rgb(84, 120, 255), Color.rgb(151, 112, 255),
            Color.rgb(218, 105, 255), Color.rgb(255, 112, 153), Color.rgb(255, 183, 92), Color.rgb(190, 232, 112),
            Color.rgb(102, 222, 167), Color.rgb(239, 245, 255)
        )
        AlertDialog.Builder(this).setTitle("Choose color ${which.toString().padStart(2, '0')}")
            .setItems(names) { _, index ->
                when (which) { 1 -> prefs.c1 = colors[index]; 2 -> prefs.c2 = colors[index]; else -> prefs.c3 = colors[index] }
                prefs.colorMode = "TRI"
                AudioCaptureService.requestUpdate(this)
                renderTab("APPEARANCE")
            }.setNegativeButton("CANCEL", null).show()
    }

    private fun permissionCard(number: String, title: String, description: String, ready: Boolean, status: String, buttonText: String, action: () -> Unit): LinearLayout {
        val box = card()
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = number
            textSize = 10f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            setTextColor(if (ready) GREEN else AMBER)
            gravity = Gravity.CENTER
            background = rounded(if (ready) Color.rgb(20, 52, 48) else Color.rgb(55, 43, 26), 8)
        }, LinearLayout.LayoutParams(dp(35), dp(30)))
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(11), 0, 0, 0) }
        column.addView(TextView(this).apply { text = title; textSize = 12f; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); setTextColor(TEXT) })
        column.addView(TextView(this).apply { text = status; textSize = 8f; letterSpacing = 0.10f; setTextColor(if (ready) GREEN else AMBER) }, marginParams(-1, -2, top = 3))
        top.addView(column, LinearLayout.LayoutParams(0, -2, 1f))
        val btn = smallButton(buttonText, false, action)
        top.addView(btn, LinearLayout.LayoutParams(dp(91), dp(34)))
        box.addView(top)
        box.addView(body(description), marginParams(-1, -2, top = 9))
        box.tag = ready
        box.layoutParams = marginParams(-1, -2, bottom = 9)
        return box
    }

    private fun card(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(15), dp(14), dp(15), dp(14))
        background = roundedCard()
    }

    private fun statCard(title: String, value: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(11), dp(10), dp(8), dp(10))
        background = roundedCard()
        addView(TextView(this@MainActivity).apply { text = title; textSize = 8f; letterSpacing = 0.1f; setTextColor(MUTED) })
        addView(TextView(this@MainActivity).apply { text = value; textSize = 16f; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); setTextColor(TEXT) }, marginParams(-1, -2, top = 4))
    }

    private fun rowLabel(left: String, right: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        addView(TextView(this@MainActivity).apply { text = left; textSize = 9f; letterSpacing = 0.10f; typeface = Typeface.create("sans-serif-medium", Typeface.BOLD); setTextColor(MUTED) }, LinearLayout.LayoutParams(0, -2, 1f))
        addView(TextView(this@MainActivity).apply { text = right; textSize = 9f; letterSpacing = 0.05f; setTextColor(CYAN); gravity = Gravity.END })
    }

    private fun eyebrow(value: String) = TextView(this).apply {
        text = value
        textSize = 9f
        letterSpacing = 0.16f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setTextColor(CYAN)
        setPadding(0, dp(3), 0, dp(5))
    }

    private fun heading(value: String) = TextView(this).apply {
        text = value
        textSize = 24f
        letterSpacing = -0.02f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setTextColor(TEXT)
    }

    private fun body(value: String) = TextView(this).apply {
        text = value
        textSize = 11f
        setTextColor(MUTED)
        setLineSpacing(dp(3).toFloat(), 1f)
    }

    private fun actionButton(label: String, enabled: Boolean = true, action: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 11f
        letterSpacing = 0.08f
        typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        setTextColor(if (enabled) Color.rgb(9, 17, 27) else Color.rgb(130, 139, 153))
        background = rounded(if (enabled) CYAN else Color.rgb(46, 55, 71), 13)
        isEnabled = enabled
        alpha = if (enabled) 1f else 0.62f
        setOnClickListener { if (enabled) action() }
    }

    private fun smallButton(label: String, selected: Boolean, action: () -> Unit) = TextView(this).apply {
        text = label
        textSize = 9f
        gravity = Gravity.CENTER
        letterSpacing = 0.03f
        setTextColor(if (selected) TEXT else MUTED)
        background = rounded(if (selected) Color.rgb(34, 58, 85) else Color.rgb(20, 28, 43), 10,
            if (selected) Color.rgb(80, 128, 166) else STROKE)
        setOnClickListener { action() }
    }

    private fun statusPill(value: String, ready: Boolean) = TextView(this).apply {
        text = "●  $value"
        textSize = 8f
        letterSpacing = 0.06f
        setTextColor(if (ready) GREEN else AMBER)
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(7), dp(10), dp(7))
        background = rounded(if (ready) Color.rgb(19, 48, 43) else Color.rgb(50, 39, 25), 20)
    }

    private fun rounded(color: Int, radius: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
        if (stroke != null) setStroke(dp(1), stroke)
    }

    private fun roundedCard() = GradientDrawable().apply {
        setColor(CARD)
        cornerRadius = dp(17).toFloat()
        setStroke(dp(1), STROKE)
    }

    private fun transparent() = GradientDrawable().apply { setColor(Color.TRANSPARENT); cornerRadius = dp(11).toFloat() }

    private fun marginParams(width: Int, height: Int, top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(width, height).apply {
        if (top != 0) topMargin = dp(top)
        if (bottom != 0) bottomMargin = dp(bottom)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun setShellContent(view: View) {
        content.removeAllViews()
        content.addView(view, LinearLayout.LayoutParams(-1, -2))
    }

    private fun buildPageLayout() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

    private fun hasNotificationPermission(): Boolean = Build.VERSION.SDK_INT < 33 ||
        checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun isIgnoringBatteryOptimizations(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        return power.isIgnoringBatteryOptimizations(packageName)
    }

    private fun isShizukuInstalled(): Boolean = try {
        packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
        true
    } catch (_: Exception) { false }

    private fun isShizukuAuthorized(): Boolean = try {
        Shizuku.pingBinder() && !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    private fun handleShizukuSetup() {
        if (!isShizukuInstalled()) {
            openUrl("https://shizuku.rikka.app/download/")
            return
        }
        val launch = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
        if (!Shizuku.pingBinder()) {
            if (launch != null) startActivity(launch)
            Toast.makeText(this, "Start the Shizuku service, then return here", Toast.LENGTH_LONG).show()
            return
        }
        try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Shizuku is already authorized", Toast.LENGTH_SHORT).show()
            } else {
                Shizuku.requestPermission(SHIZUKU_REQUEST)
            }
        } catch (_: Exception) {
            if (launch != null) startActivity(launch)
        }
    }

    private fun openOverlaySettings() {
        try { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
        catch (_: Exception) { startActivity(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS)) }
    }

    private fun requestBatteryExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || isIgnoringBatteryOptimizations()) return
        try {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        }
    }

    private fun requestAudioPermission(requestCode: Int) {
        val permission = Manifest.permission.RECORD_AUDIO
        val store = getSharedPreferences("lumenwave_permissions", MODE_PRIVATE)
        val askedBefore = store.getBoolean("audio_asked", false)
        val permanentlyDenied = askedBefore && !shouldShowRequestPermissionRationale(permission)
        if (permanentlyDenied) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS))
            }
        } else {
            store.edit().putBoolean("audio_asked", true).apply()
            requestPermissions(arrayOf(permission), requestCode)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !hasNotificationPermission()) {
            val askedBefore = getSharedPreferences("lumenwave_permissions", MODE_PRIVATE).getBoolean("notification_asked", false)
            val permanentlyDenied = askedBefore && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
            if (permanentlyDenied) {
                try {
                    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
                } catch (_: Exception) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
                }
            } else {
                getSharedPreferences("lumenwave_permissions", MODE_PRIVATE).edit().putBoolean("notification_asked", true).apply()
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
            }
        }
    }

    private fun beginVisualizer() {
        if (AudioCaptureService.running) { Toast.makeText(this, "Visualizer is already running", Toast.LENGTH_SHORT).show(); return }
        if (!prefs.visualizerEnabled) {
            Toast.makeText(this, "Visualizer is switched off in Visualizer settings", Toast.LENGTH_SHORT).show()
            currentTab = "VISUALIZER"; renderTab(currentTab); return
        }
        if (!isShizukuAuthorized()) {
            Toast.makeText(this, "Authorize Shizuku first", Toast.LENGTH_LONG).show()
            currentTab = "ACCESS"; renderTab(currentTab); return
        }
        if (!Settings.canDrawOverlays(this)) {
            openOverlaySettings(); return
        }
        if (Build.VERSION.SDK_INT >= 23 && !isIgnoringBatteryOptimizations()) {
            Toast.makeText(this, "Allow background operation first", Toast.LENGTH_LONG).show()
            requestBatteryExemption(); return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestAudioPermission(AUDIO_PERMISSION_REQUEST)
            return
        }
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(manager.createScreenCaptureIntent(), MEDIA_PROJECTION_REQUEST)
    }

    private fun stopVisualizer() {
        AudioCaptureService.requestStop(this)
        currentTab = "HOME"
        root.postDelayed({ refreshServiceStatus(); renderTab(currentTab) }, 250)
    }

    @Deprecated("Activity result is used for the Android MediaProjection consent flow")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MEDIA_PROJECTION_REQUEST) {
            if (resultCode == RESULT_OK && data != null) {
                val service = Intent(this, AudioCaptureService::class.java).apply {
                    action = AudioCaptureService.ACTION_START
                    putExtra(AudioCaptureService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(AudioCaptureService.EXTRA_RESULT_DATA, data)
                }
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service) else startService(service)
                    Toast.makeText(this, "Starting LumenWave…", Toast.LENGTH_SHORT).show()
                    root.postDelayed({ refreshServiceStatus(); renderTab("HOME") }, 900)
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not start capture: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(this, "Playback capture was not approved", Toast.LENGTH_SHORT).show()
            }
            currentTab = "HOME"; renderTab(currentTab)
        }
    }

    @Deprecated("Legacy permission callback keeps compatibility with min API 29")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            AUDIO_PERMISSION_REQUEST -> {
                if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) beginVisualizer()
                else Toast.makeText(this, "Audio permission is required for playback capture", Toast.LENGTH_LONG).show()
            }
            AUDIO_PERMISSION_MANAGE_REQUEST -> {
                if (grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) Toast.makeText(this, "Audio permission was not granted", Toast.LENGTH_SHORT).show()
                renderTab(currentTab)
            }
            NOTIFICATION_REQUEST -> {
                if (!hasNotificationPermission()) Toast.makeText(this, "Notifications are disabled. The visualizer may still run, but its ongoing notice can be hidden.", Toast.LENGTH_LONG).show()
                if (renderingSetup) renderSetup() else renderTab(currentTab)
            }
        }
    }

    private fun openUrl(url: String) {
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: Exception) { Toast.makeText(this, "No browser available", Toast.LENGTH_SHORT).show() }
    }

    private class PreviewSpectrum(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: android.graphics.Canvas) {
            super.onDraw(canvas)
            val n = 34
            val gap = width * 0.009f
            val bar = (width - gap * (n - 1)) / n
            for (i in 0 until n) {
                val wave = kotlin.math.abs(kotlin.math.sin(i * 0.43)) * 0.57 + kotlin.math.abs(kotlin.math.cos(i * 0.19)) * 0.28
                val h = (height * (0.16 + wave * 0.72)).toFloat()
                val hue = (178f + i * 4.4f) % 360f
                paint.color = Color.HSVToColor(floatArrayOf(hue, 0.60f, 1f))
                val left = i * (bar + gap)
                canvas.drawRoundRect(RectF(left, height - h, left + bar, height.toFloat()), bar / 2f, bar / 2f, paint)
            }
        }
    }

    companion object {
        private const val SHIZUKU_REQUEST = 1001
        private const val AUDIO_PERMISSION_REQUEST = 1002
        private const val NOTIFICATION_REQUEST = 1003
        private const val MEDIA_PROJECTION_REQUEST = 1004
        private const val AUDIO_PERMISSION_MANAGE_REQUEST = 1005
        private val BG = Color.rgb(8, 11, 18)
        private val CARD = Color.rgb(16, 22, 34)
        private val STROKE = Color.rgb(35, 47, 68)
        private val TEXT = Color.rgb(231, 239, 253)
        private val MUTED = Color.rgb(139, 154, 180)
        private val CYAN = Color.rgb(111, 224, 215)
        private val GREEN = Color.rgb(101, 218, 158)
        private val AMBER = Color.rgb(236, 184, 96)
    }
}
