package com.jss.smsotpextractor

import android.Manifest
import android.app.Activity
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val animationHandler = Handler(Looper.getMainLooper())
    private var dotFrame = 0
    private lateinit var listeningLabel: TextView
    private lateinit var historyList: LinearLayout
    private var modelStatusTitle: TextView? = null
    private var modelStatusDetail: TextView? = null
    private var observingHistory = false
    private val historyChangeListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (!ResultStore.isHistoryKey(key)) return@OnSharedPreferenceChangeListener
        animationHandler.post {
            if (::historyList.isInitialized) {
                refresh()
            }
        }
    }
    private val listeningAnimation = object : Runnable {
        override fun run() {
            listeningLabel.text = listeningText(dotFrame % 4)
            dotFrame += 1
            animationHandler.postDelayed(this, 420L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        window.statusBarColor = Colors.background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }

        if (missingPermissions().isNotEmpty()) {
            showPermissionOnboarding()
        } else if (!onboardingComplete()) {
            showDemoOnboarding()
        } else {
            showMainContent()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::listeningLabel.isInitialized) {
            startObservingHistory()
            animationHandler.removeCallbacks(listeningAnimation)
            animationHandler.post(listeningAnimation)
            refresh()
        }
    }

    override fun onPause() {
        animationHandler.removeCallbacks(listeningAnimation)
        stopObservingHistory()
        super.onPause()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_PERMISSIONS) return

        if (missingPermissions().isEmpty()) {
            showDemoOnboarding()
        } else {
            showPermissionOnboarding(permissionDenied = true)
        }
    }

    private fun showMainContent() {
        historyList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22.dp, 96.dp, 22.dp, 30.dp)
            addView(hero(), matchWrap())
            if (!BuildConfig.BUNDLED_MODEL) {
                addView(modelStatusSection(), matchWrap(top = 24.dp))
            }
            addView(actions(), matchWrap(top = 26.dp))
            addView(sectionHeader(), matchWrap(top = 30.dp))
            addView(historyList, matchWrap(top = 12.dp))
            setOnApplyWindowInsetsListener { view, insets ->
                val topInset = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    insets.getInsets(WindowInsets.Type.statusBars()).top
                } else {
                    @Suppress("DEPRECATION")
                    insets.systemWindowInsetTop
                }
                view.setPadding(22.dp, topInset + 42.dp, 22.dp, 30.dp)
                insets
            }
        }

        setContentView(
            ScrollView(this).apply {
                setBackgroundColor(Colors.background)
                clipToPadding = false
                addView(content)
            },
        )
        startObservingHistory()
        refresh()
        refreshModelStatus()
        animationHandler.removeCallbacks(listeningAnimation)
        animationHandler.post(listeningAnimation)
    }

    private fun hero(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(2.dp, 0, 2.dp, 0)
            addView(appHeader(), matchWrap())
            addView(
                TextView(this@MainActivity).apply {
                    text = "Incoming codes are copied when detected."
                    textSize = 16f
                    setTextColor(Colors.onSurfaceMuted)
                    setLineSpacing(3.dp.toFloat(), 1f)
                },
                matchWrap(top = 12.dp),
            )
        }
    }

    private fun appHeader(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                TextView(this@MainActivity).apply {
                    text = "SMS OTP Extractor"
                    textSize = 22f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Colors.onSurface)
                    includeFontPadding = false
                },
                weightWrap(weight = 1f),
            )
            addView(listeningStatus(), wrapContent(left = 14.dp))
        }
    }

    private fun listeningStatus(): TextView {
        return TextView(this).apply {
            listeningLabel = this
            text = listeningText(0)
            textSize = 13f
            gravity = Gravity.CENTER_VERTICAL
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Colors.primary)
        }
    }

    private fun listeningText(visibleDots: Int): SpannableString {
        return SpannableString("Listening...").apply {
            val firstDot = "Listening".length
            for (index in 0 until 3) {
                val color = if (index < visibleDots) Colors.primary else Colors.invisibleText
                setSpan(ForegroundColorSpan(color), firstDot + index, firstDot + index + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
    }

    private fun actions(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                actionButton("Copy latest", Colors.primary, Colors.onPrimary) {
                    copyLatestCode()
                },
                weightWrap(weight = 1f),
            )
            addView(
                actionButton("Clear", Colors.secondaryContainer, Colors.onSecondaryContainer) {
                    ResultStore.clearHistory(this@MainActivity)
                    refresh()
                },
                weightWrap(left = 10.dp, weight = 1f),
            )
        }
    }

    private fun modelStatusSection(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Colors.emptyContainer, 24.dp, Colors.outline, 1.dp)
            setPadding(18.dp, 16.dp, 18.dp, 16.dp)
            addView(
                TextView(this@MainActivity).apply {
                    modelStatusTitle = this
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Colors.onSurface)
                },
                matchWrap(),
            )
            addView(
                TextView(this@MainActivity).apply {
                    modelStatusDetail = this
                    textSize = 14f
                    setTextColor(Colors.onSurfaceMuted)
                    setLineSpacing(3.dp.toFloat(), 1f)
                },
                matchWrap(top = 6.dp),
            )
            addView(
                TextView(this@MainActivity).apply {
                    text = "Heuristics only"
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(Colors.primary)
                    background = rounded(Colors.secondaryContainer, 24.dp)
                    minHeight = 48.dp
                },
                matchWrap(top = 14.dp),
            )
        }
    }

    private fun refreshModelStatus(
        statusOverride: String? = null,
        detailOverride: String? = null,
    ) {
        if (BuildConfig.BUNDLED_MODEL) return
        modelStatusTitle?.text = statusOverride ?: "Heuristics-only build"
        modelStatusDetail?.text = detailOverride ?: "No LiteRT model is bundled or imported. Ambiguous messages are handled by local rules only."
    }

    private fun renderHistory() {
        historyList.removeAllViews()
        val history = ResultStore.history(this)
        if (history.isEmpty()) {
            historyList.addView(
                TextView(this).apply {
                    text = "Waiting for an incoming SMS. Detected codes will show up here with the reason they were accepted or skipped."
                    textSize = 15f
                    setTextColor(Colors.onSurfaceMuted)
                    background = rounded(Colors.emptyContainer, 28.dp, Colors.outline, 1.dp)
                    setPadding(18.dp, 18.dp, 18.dp, 18.dp)
                },
                matchWrap(),
            )
            return
        }

        history.forEachIndexed { index, item ->
            historyList.addView(historyCard(item), matchWrap(top = if (index == 0) 0 else 10.dp))
        }
    }

    private fun historyCard(item: ResultStore.HistoryItem): View {
        val detected = item.code != null
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(if (detected) Colors.detectedContainer else Colors.blockedContainer, 26.dp)
            setPadding(18.dp, 16.dp, 18.dp, 16.dp)
            addView(
                TextView(this@MainActivity).apply {
                    text = "${timeLabel(item.receivedAtMs)}  /  ${item.title}"
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Colors.onSurface)
                },
            )
            addView(
                TextView(this@MainActivity).apply {
                    text = item.detail
                    textSize = 14f
                    setTextColor(Colors.onSurface)
                },
                matchWrap(top = 6.dp),
            )
            if (item.candidates.isNotEmpty()) {
                addView(
                    TextView(this@MainActivity).apply {
                        text = "Candidates: ${item.candidates.joinToString()}"
                        textSize = 12f
                        setTextColor(Colors.onSurfaceMuted)
                    },
                    matchWrap(top = 8.dp),
                )
            }
            addView(
                TextView(this@MainActivity).apply {
                    text = item.sms
                    textSize = 13f
                    setTextColor(Colors.onSurfaceMuted)
                    setTextIsSelectable(true)
                },
                matchWrap(top = 10.dp),
            )
        }
    }

    private fun showPermissionOnboarding(permissionDenied: Boolean = false) {
        animationHandler.removeCallbacks(listeningAnimation)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 80.dp, 24.dp, 28.dp)
            setBackgroundColor(Colors.background)
            addView(
                TextView(this@MainActivity).apply {
                    text = "Welcome! Let's get you set up"
                    textSize = 31f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Colors.onSurface)
                    includeFontPadding = false
                    setLineSpacing(2.dp.toFloat(), 1f)
                },
                matchWrap(),
            )
            addView(
                TextView(this@MainActivity).apply {
                    text = "We'll need a couple permissions to start."
                    textSize = 16f
                    setTextColor(Colors.onSurfaceMuted)
                    setLineSpacing(4.dp.toFloat(), 1f)
                },
                matchWrap(top = 14.dp),
            )
            addView(permissionCard(), matchWrap(top = 36.dp))
            if (permissionDenied) {
                addView(
                    TextView(this@MainActivity).apply {
                        text = "SMS permission is needed so incoming codes can be detected and copied automatically."
                        textSize = 14f
                        setTextColor(Colors.onBlocked)
                    },
                    matchWrap(top = 16.dp),
                )
            }
            addView(Space(this@MainActivity), verticalSpacer())
            addView(
                actionButton("Grant permissions", Colors.primary, Colors.onPrimary) {
                    val permissions = missingPermissions()
                    if (permissions.isEmpty()) {
                        showDemoOnboarding()
                    } else {
                        requestPermissions(permissions.toTypedArray(), REQUEST_PERMISSIONS)
                    }
                },
                matchWrap(top = 24.dp),
            )
        }

        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(Colors.background)
                addView(content, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ))
            },
        )
    }

    private fun permissionCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = rounded(Colors.emptyContainer, 26.dp, Colors.outline, 1.dp)
            setPadding(18.dp, 18.dp, 18.dp, 18.dp)
            addView(
                TextView(this@MainActivity).apply {
                    text = "SMS access"
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Colors.onSurface)
                },
            )
            addView(
                TextView(this@MainActivity).apply {
                    text = "Watches for incoming messages that may contain one-time codes."
                    textSize = 14f
                    setTextColor(Colors.onSurfaceMuted)
                },
                matchWrap(top = 5.dp),
            )
        }
    }

    private fun showDemoOnboarding() {
        animationHandler.removeCallbacks(listeningAnimation)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dp, 56.dp, 24.dp, 28.dp)
            setBackgroundColor(Colors.background)
            addView(
                TextView(this@MainActivity).apply {
                    text = "Here's the magic"
                    textSize = 30f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Colors.onSurface)
                    includeFontPadding = false
                },
                matchWrap(),
            )
            addView(
                TextView(this@MainActivity).apply {
                    text = "When a text arrives, the app checks it locally, finds the OTP, copies it, and shows a brief confirmation."
                    textSize = 16f
                    setTextColor(Colors.onSurfaceMuted)
                    setLineSpacing(4.dp.toFloat(), 1f)
                },
                matchWrap(top = 12.dp),
            )
            addView(
                OtpDemoView(this@MainActivity).apply {
                    minimumHeight = 460.dp
                },
                fillRemaining(top = 24.dp),
            )
            addView(
                actionButton("Start using app", Colors.primary, Colors.onPrimary) {
                    markOnboardingComplete()
                    showMainContent()
                },
                matchWrap(top = 24.dp),
            )
        }

        setContentView(
            FrameLayout(this).apply {
                setBackgroundColor(Colors.background)
                addView(content, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ))
            },
        )
    }

    private fun missingPermissions(): List<String> {
        return buildList {
            if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.RECEIVE_SMS)
            }
        }
    }

    private fun onboardingComplete(): Boolean {
        return getPreferences(MODE_PRIVATE).getBoolean(PREF_ONBOARDING_COMPLETE, false)
    }

    private fun markOnboardingComplete() {
        getPreferences(MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_ONBOARDING_COMPLETE, true)
            .apply()
    }

    private fun copyLatestCode() {
        val code = ResultStore.latestCode(this) ?: return
        OtpClipboard.copy(this, code)
        OtpToastHelper.showCodeCopied(this)
        refresh()
    }

    private fun refresh() {
        renderHistory()
        refreshModelStatus()
    }

    private fun startObservingHistory() {
        if (observingHistory) return
        ResultStore.registerHistoryListener(this, historyChangeListener)
        observingHistory = true
    }

    private fun stopObservingHistory() {
        if (!observingHistory) return
        ResultStore.unregisterHistoryListener(this, historyChangeListener)
        observingHistory = false
    }

    private fun sectionHeader(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(2.dp, 0, 2.dp, 0)
            addView(
                TextView(this@MainActivity).apply {
                    text = "Recent activity"
                    textSize = 22f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(Colors.onSurface)
                    includeFontPadding = false
                },
            )
            addView(
                TextView(this@MainActivity).apply {
                    text = "The latest processed messages stay local on this device."
                    textSize = 14f
                    setTextColor(Colors.onSurfaceMuted)
                },
                matchWrap(top = 5.dp),
            )
        }
    }

    private fun actionButton(
        label: String,
        backgroundColor: Int,
        textColor: Int,
        onClick: () -> Unit,
    ): Button {
        return Button(this).apply {
            text = label
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            setTextColor(textColor)
            background = rounded(backgroundColor, 24.dp)
            minHeight = 56.dp
            setOnClickListener { onClick() }
        }
    }

    private fun rounded(
        color: Int,
        radius: Int,
        strokeColor: Int? = null,
        strokeWidth: Int = 0,
    ): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius.toFloat()
            if (strokeColor != null && strokeWidth > 0) {
                setStroke(strokeWidth, strokeColor)
            }
        }
    }

    private fun timeLabel(timestampMs: Long): String {
        if (timestampMs <= 0L) return "just now"
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(timestampMs))
    }

    private fun matchWrap(top: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = top }
    }

    private fun weightWrap(left: Int = 0, weight: Float): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            weight,
        ).apply { leftMargin = left }
    }

    private fun wrapContent(left: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { leftMargin = left }
    }

    private fun verticalSpacer(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        )
    }

    private fun fillRemaining(top: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f,
        ).apply { topMargin = top }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQUEST_PERMISSIONS = 10
        const val PREF_ONBOARDING_COMPLETE = "onboarding_complete"

        object Colors {
            val background = Color.rgb(255, 248, 242)
            val outline = Color.rgb(226, 213, 206)
            val primary = Color.rgb(13, 95, 116)
            val onPrimary = Color.WHITE
            val invisibleText = Color.TRANSPARENT
            val secondaryContainer = Color.rgb(255, 218, 185)
            val onSecondaryContainer = Color.rgb(75, 36, 0)
            val emptyContainer = Color.rgb(246, 238, 232)
            val detectedContainer = Color.rgb(209, 250, 229)
            val blockedContainer = Color.rgb(255, 218, 214)
            val onDetected = Color.rgb(6, 78, 59)
            val onBlocked = Color.rgb(123, 30, 24)
            val onSurface = Color.rgb(31, 31, 35)
            val onSurfaceMuted = Color.rgb(88, 84, 93)
        }
    }

    private class OtpDemoView(context: Activity) : View(context) {
        private val density = resources.displayMetrics.density
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        private val mutedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val startMs = System.currentTimeMillis()
        private val rect = RectF()
        private val messageRect = RectF()

        init {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val elapsed = ((System.currentTimeMillis() - startMs) % 4400L).toFloat()
            val progress = elapsed / 4400f
            val w = width.toFloat()
            val centerX = w / 2f
            val top = 2.dpF
            val phoneW = (w * 0.86f).coerceAtMost(330.dpF)
            val phoneH = (height - 8.dpF).coerceAtLeast(430.dpF)
            val phoneLeft = centerX - phoneW / 2f

            drawPhone(canvas, phoneLeft, top, phoneW, phoneH)
            drawIncomingMessage(canvas, phoneLeft, top, phoneW, progress)
            drawScanner(canvas, progress)
            drawCopiedCode(canvas, phoneLeft, top, phoneW, phoneH, progress)
            postInvalidateOnAnimation()
        }

        private fun drawPhone(canvas: Canvas, left: Float, top: Float, width: Float, height: Float) {
            paint.color = Color.argb(42, 31, 31, 35)
            rect.set(left + 5.dpF, top + 8.dpF, left + width + 5.dpF, top + height + 8.dpF)
            canvas.drawRoundRect(rect, 34.dpF, 34.dpF, paint)

            paint.color = Color.rgb(31, 31, 35)
            rect.set(left, top, left + width, top + height)
            canvas.drawRoundRect(rect, 34.dpF, 34.dpF, paint)

            paint.color = Color.rgb(252, 249, 244)
            rect.set(left + 12.dpF, top + 14.dpF, left + width - 12.dpF, top + height - 14.dpF)
            canvas.drawRoundRect(rect, 24.dpF, 24.dpF, paint)

            paint.color = Color.rgb(31, 31, 35)
            rect.set(left + width / 2f - 30.dpF, top + 24.dpF, left + width / 2f + 30.dpF, top + 30.dpF)
            canvas.drawRoundRect(rect, 4.dpF, 4.dpF, paint)
        }

        private fun drawIncomingMessage(canvas: Canvas, phoneLeft: Float, phoneTop: Float, phoneW: Float, progress: Float) {
            val fadeIn = ease(segment(progress, 0.08f, 0.24f))
            val fadeOut = 1f - ease(segment(progress, 0.82f, 0.96f))
            val alpha = (fadeIn * fadeOut * 255).toInt().coerceIn(0, 255)
            val left = phoneLeft + 26.dpF
            val top = phoneTop + 62.dpF
            val right = phoneLeft + phoneW - 26.dpF
            val bottom = top + 104.dpF
            messageRect.set(left, top, right, bottom)

            paint.alpha = alpha
            paint.color = Color.rgb(255, 255, 255)
            rect.set(left, top, right, bottom)
            canvas.drawRoundRect(rect, 20.dpF, 20.dpF, paint)

            paint.color = Color.rgb(209, 250, 229)
            rect.set(left + 14.dpF, top + 16.dpF, left + 44.dpF, top + 46.dpF)
            canvas.drawOval(rect, paint)

            textPaint.color = Color.rgb(31, 31, 35)
            textPaint.alpha = alpha
            textPaint.textSize = 13.dpF
            canvas.drawText("New message", left + 54.dpF, top + 29.dpF, textPaint)

            mutedTextPaint.color = Color.rgb(88, 84, 93)
            mutedTextPaint.alpha = alpha
            mutedTextPaint.textSize = 12.dpF
            canvas.drawText("Your login code is 482913", left + 18.dpF, top + 66.dpF, mutedTextPaint)

            paint.alpha = 255
            textPaint.alpha = 255
            mutedTextPaint.alpha = 255
        }

        private fun drawScanner(canvas: Canvas, progress: Float) {
            val scanProgress = segment(progress, 0.30f, 0.62f)
            if (scanProgress <= 0f) return

            val alphaOut = 1f - ease(segment(progress, 0.62f, 0.72f))
            val alpha = (alphaOut * 255).toInt().coerceIn(0, 255)
            val left = messageRect.left - 8.dpF
            val top = messageRect.top - 8.dpF
            val right = messageRect.right + 8.dpF
            val bottom = messageRect.bottom + 8.dpF

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.dpF
            paint.color = Color.rgb(13, 95, 116)
            paint.alpha = alpha
            rect.set(left, top, right, bottom)
            canvas.drawRoundRect(rect, 18.dpF, 18.dpF, paint)
            paint.style = Paint.Style.FILL

            val lineY = top + ease(scanProgress) * (bottom - top)
            paint.color = Color.rgb(13, 95, 116)
            paint.alpha = alpha
            rect.set(left + 12.dpF, lineY - 2.dpF, right - 12.dpF, lineY + 2.dpF)
            canvas.drawRoundRect(rect, 2.dpF, 2.dpF, paint)

            mutedTextPaint.color = Color.rgb(88, 84, 93)
            mutedTextPaint.alpha = alpha
            mutedTextPaint.textSize = 12.dpF
            canvas.drawText("Finding OTP...", left + 18.dpF, bottom + 24.dpF, mutedTextPaint)

            paint.alpha = 255
            mutedTextPaint.alpha = 255
        }

        private fun drawCopiedCode(
            canvas: Canvas,
            phoneLeft: Float,
            phoneTop: Float,
            phoneW: Float,
            phoneH: Float,
            progress: Float,
        ) {
            val popIn = ease(segment(progress, 0.66f, 0.76f))
            val fadeOut = 1f - ease(segment(progress, 0.88f, 0.98f))
            val copiedProgress = popIn * fadeOut
            if (copiedProgress <= 0f) return

            val left = phoneLeft + 42.dpF
            val top = phoneTop + phoneH - 118.dpF + (1f - popIn) * 34.dpF
            val right = phoneLeft + phoneW - 42.dpF
            val bottom = top + 64.dpF
            val alpha = (copiedProgress * 255).toInt().coerceIn(0, 255)

            paint.color = Color.rgb(13, 95, 116)
            paint.alpha = alpha
            rect.set(left, top, right, bottom)
            canvas.drawRoundRect(rect, 20.dpF, 20.dpF, paint)

            textPaint.color = Color.WHITE
            textPaint.alpha = alpha
            textPaint.textSize = 15.dpF
            canvas.drawText("Code copied to clipboard", left + 18.dpF, top + 39.dpF, textPaint)

            paint.alpha = 255
            textPaint.alpha = 255
        }

        private fun segment(value: Float, start: Float, end: Float): Float {
            return ((value - start) / (end - start)).coerceIn(0f, 1f)
        }

        private fun ease(value: Float): Float {
            return 1f - (1f - value) * (1f - value)
        }

        private val Int.dpF: Float
            get() = this * density
    }
}
