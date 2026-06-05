package com.jss.smsotpextractor

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.jss.smsotpextractor.otp.OtpProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var status: TextView
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        status = TextView(this).apply {
            textSize = 15f
            setTextIsSelectable(true)
        }
        output = TextView(this).apply {
            textSize = 15f
            setTextIsSelectable(true)
        }

        val permissionButton = Button(this).apply {
            text = "Grant SMS Permission"
            setOnClickListener { requestNeededPermissions() }
        }
        val sampleButton = Button(this).apply {
            text = "Run Sample SMS"
            setOnClickListener { runSample() }
        }
        val copyButton = Button(this).apply {
            text = "Copy Latest Code"
            setOnClickListener { copyLatestCode() }
        }
        val refreshButton = Button(this).apply {
            text = "Refresh Debug"
            setOnClickListener { refresh() }
        }

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 40, 32, 32)
            addView(status, matchWrap())
            addView(permissionButton, matchWrap(top = 16))
            addView(sampleButton, matchWrap(top = 8))
            addView(copyButton, matchWrap(top = 8))
            addView(refreshButton, matchWrap(top = 8))
            addView(output, matchWrap(top = 24))
        }

        setContentView(ScrollView(this).apply { addView(content) })
        requestNeededPermissions()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
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

    private fun runSample() {
        val sms = "RandomApp12345: Your verification code is 483920. Ref 20260605."
        output.text = "Processing sample..."
        scope.launch {
            val decision = OtpProcessor(LiteRtAiOtpSelector(this@MainActivity)).process(sms)
            ResultStore.save(this@MainActivity, sms, decision)
            OtpClipboard.copyIfDetected(this@MainActivity, decision)
            OtpNotificationHelper.showIfDetected(this@MainActivity, decision)
            refresh()
        }
    }

    private fun copyLatestCode() {
        val code = ResultStore.latestCode(this) ?: return
        OtpClipboard.copy(this, code)
        refresh()
    }

    private fun refresh() {
        val smsGranted = checkSelfPermission(Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED
        val notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        status.text = buildString {
            appendLine("SMS permission: ${if (smsGranted) "granted" else "missing"}")
            appendLine("Notification permission: ${if (notificationGranted) "granted" else "missing"}")
            appendLine("Package: $packageName")
        }
        output.text = ResultStore.latestText(this)
    }

    private fun matchWrap(top: Int = 0): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = top }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val REQUEST_PERMISSIONS = 10
    }
}
