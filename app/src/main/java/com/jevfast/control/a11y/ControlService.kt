package com.jevfast.control.a11y

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.jevfast.control.R
import com.jevfast.control.StopAgentReceiver
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The accessibility bridge: captures the screen tree and performs actions.
 * Exposed as a singleton once the user enables the service in Settings.
 */
class ControlService : AccessibilityService() {

    companion object {
        const val ACTION_STOP = "com.jevfast.control.STOP_AGENT"
        private const val NOTIF_CHANNEL = "agent_control"
        private const val NOTIF_ID = 42

        @Volatile
        var instance: ControlService? = null
            private set
    }

    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        hideControlIndicator()
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun screen(): Screen = ScreenCapture.capture(this)

    fun fingerprint(): Int = screen().fingerprint

    // ---- AI-control indicator (bluish edge glow) ---------------------------
    // TYPE_ACCESSIBILITY_OVERLAY needs no extra permission; the view is a
    // separate non-focusable window so it never enters the captured node tree.

    private val mainHandler = Handler(Looper.getMainLooper())
    private var indicator: BorderGlowView? = null

    fun showControlIndicator() {
        mainHandler.post {
            if (indicator != null) return@post
            val wm = getSystemService(WindowManager::class.java) ?: return@post
            val v = BorderGlowView(this)
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    // Keep the device awake while the agent drives — the
                    // overlay lives exactly as long as the run does.
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                PixelFormat.TRANSLUCENT,
            )
            if (Build.VERSION.SDK_INT >= 28) {
                lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            try {
                wm.addView(v, lp)
                indicator = v
            } catch (_: Exception) {}
            showAgentNotification()
        }
    }

    // Ongoing notification with a STOP action — the explicit stop control
    // lives in the shade, never occluding screen content the agent needs.
    // IMPORTANCE_LOW: no sound, no heads-up peek — a banner would cover the
    // top of the screen and hide elements (e.g. search bars) from capture.
    private fun showAgentNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            // Channel importance is immutable once created — recreate so the
            // low-importance setting applies even after the old high one.
            nm.deleteNotificationChannel(NOTIF_CHANNEL)
            val ch = NotificationChannel(
                NOTIF_CHANNEL, "Agent control", NotificationManager.IMPORTANCE_LOW
            )
            nm.createNotificationChannel(ch)
        }
        val stopPi = PendingIntent.getBroadcast(
            this, 0,
            Intent(this, StopAgentReceiver::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notification.Builder(this, NOTIF_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("JevFast agent is controlling this device")
            .setContentText("Tap STOP to end the run")
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "STOP", stopPi).build())
            .build()
        getSystemService(NotificationManager::class.java).notify(NOTIF_ID, n)
    }

    fun hideControlIndicator() {
        mainHandler.post {
            val wm = getSystemService(WindowManager::class.java)
            indicator?.let {
                try { wm?.removeView(it) } catch (_: Exception) {}
            }
            indicator = null
            getSystemService(NotificationManager::class.java)?.cancel(NOTIF_ID)
        }
    }

    suspend fun tap(x: Int, y: Int): Boolean {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        return dispatchGestureAwait(gesture)
    }

    suspend fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, durationMs: Long = 300): Boolean {
        val path = Path().apply { moveTo(x1.toFloat(), y1.toFloat()); lineTo(x2.toFloat(), y2.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, durationMs))
            .build()
        return dispatchGestureAwait(gesture)
    }

    /**
     * Re-resolve an element against a fresh capture. AccessibilityNodeInfo
     * handles go stale fast (Compose rebuilds semantics on focus/IME events),
     * so node-based actions must re-find the target before mutating.
     */
    fun resolve(el: El): El? {
        val fresh = screen().elements
        fresh.firstOrNull { it.key == el.key }?.let { return it }
        fresh.firstOrNull { it.index == el.index && it.editable == el.editable }
            ?.let { return it }
        return fresh.minByOrNull {
            val dx = it.cx - el.cx
            val dy = it.cy - el.cy
            dx * dx + dy * dy
        }?.takeIf {
            val dx = it.cx - el.cx
            val dy = it.cy - el.cy
            dx * dx + dy * dy < 150 * 150
        }
    }

    /**
     * Direct text entry: focus, ACTION_SET_TEXT, then a clipboard-paste
     * fallback for fields that reject set-text.
     */
    suspend fun setText(el: El, text: String): Boolean {
        val node = el.node
        if (!node.refresh()) return false
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return true
        // Fallback: clipboard + ACTION_PASTE for fields rejecting SET_TEXT.
        return try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("jevfast", text))
            node.refresh() && node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        } catch (e: Exception) {
            false
        }
    }

    fun pressEnter(el: El?): Boolean {
        val target = el?.let { resolve(it) } ?: return false
        if (Build.VERSION.SDK_INT < 30) return false
        return target.node.performAction(
            AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id
        )
    }

    fun scrollForward(el: El?): Boolean = scrollOn(el, AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    fun scrollBackward(el: El?): Boolean = scrollOn(el, AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)

    private fun scrollOn(el: El?, action: Int): Boolean {
        // Prefer the chosen target; otherwise take any scrollable element on screen.
        var node = el?.let { resolve(it) }?.node
            ?: screen().elements.firstOrNull { it.scrollable }?.node
        var hops = 0
        // Walk up to the nearest scrollable ancestor.
        while (node != null && hops < 8) {
            if (node.isScrollable) {
                node.refresh()
                return node.performAction(action)
            }
            node = node.parent
            hops++
        }
        return false
    }

    /** Display-sized vertical swipe used when no scrollable node exists. */
    suspend fun swipeVertical(down: Boolean): Boolean {
        val dm = resources.displayMetrics
        val cx = dm.widthPixels / 2
        val top = (dm.heightPixels * 0.30f).toInt()
        val bottom = (dm.heightPixels * 0.70f).toInt()
        return if (down) swipe(cx, bottom, cx, top) else swipe(cx, top, cx, bottom)
    }

    fun global(action: Int) = performGlobalAction(action)

    private suspend fun dispatchGestureAwait(gesture: GestureDescription): Boolean =
        suspendCancellableCoroutine { cont ->
            dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            }, null)
        }
}
