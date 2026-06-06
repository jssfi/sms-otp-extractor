package com.jss.smsotpextractor

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
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
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val animationHandler = Handler(Looper.getMainLooper())
    private var dotFrame = 0
    private lateinit var listeningLabel: TextView
    private lateinit var historyList: LinearLayout
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

        historyList = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22.dp, 96.dp, 22.dp, 30.dp)
            addView(hero(), matchWrap())
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
        requestNeededPermissions()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        animationHandler.removeCallbacks(listeningAnimation)
        animationHandler.post(listeningAnimation)
        refresh()
    }

    override fun onPause() {
        animationHandler.removeCallbacks(listeningAnimation)
        super.onPause()
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

    private fun requestNeededPermissions() {
        val permissions = buildList {
            if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED) {
                add(Manifest.permission.RECEIVE_SMS)
            }
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), REQUEST_PERMISSIONS)
        }
        refresh()
    }

    private fun copyLatestCode() {
        val code = ResultStore.latestCode(this) ?: return
        OtpClipboard.copy(this, code)
        refresh()
    }

    private fun refresh() {
        renderHistory()
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

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private companion object {
        const val REQUEST_PERMISSIONS = 10

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
}
