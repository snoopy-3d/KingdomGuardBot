package kingdomguard.bot

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var prefs: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences("bot_prefs", MODE_PRIVATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(1, createNotification())
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (overlayView == null) createOverlay()
        return START_STICKY
    }

    private fun createOverlay() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = inflater.inflate(R.layout.overlay_button, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 300

        overlayView?.let { view ->
            windowManager.addView(view, params)
            view.findViewById<Button>(R.id.btnToggle).setOnClickListener { toggleBot() }
            setupDrag(view, params)
            updateStatusText()
        }
    }

    private fun toggleBot() {
        val isRunning = prefs.getBoolean("bot_running", false)
        prefs.edit().putBoolean("bot_running", !isRunning).apply()
        val intent = Intent(this, KingdomGuardBotService::class.java).apply {
            action = if (!isRunning) "START_BOT" else "STOP_BOT"
        }
        startService(intent)
        updateStatusText()
    }

    private fun updateStatusText() {
        overlayView?.let { view ->
            val isRunning = prefs.getBoolean("bot_running", false)
            val lastLog = prefs.getString("last_log", "Ожидание...")
            view.findViewById<TextView>(R.id.tvStatus).text =
                if (isRunning) "🟢 Работает" else "🔴 Остановлен"
            view.findViewById<TextView>(R.id.tvLog).text = lastLog
        }
    }

    private fun setupDrag(view: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        view.findViewById<View>(R.id.dragHandle).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun createNotification(): Notification {
        val channelId = "kingdom_bot_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Kingdom Bot", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        return Notification.Builder(this, channelId)
            .setContentTitle("Kingdom Guard Bot")
            .setContentText("Бот активен")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        overlayView?.let { windowManager.removeView(it) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
