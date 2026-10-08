package kingdomguard.bot

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.SharedPreferences
import android.view.accessibility.AccessibilityEvent

class KingdomGuardBotService : AccessibilityService() {

    private lateinit var engine: BotEngine
    private lateinit var prefs: SharedPreferences

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        prefs = getSharedPreferences("bot_prefs", MODE_PRIVATE)
        engine = BotEngine(this, prefs)
        prefs.edit().putString("status", "Сервис готов").apply()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        engine.stop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START_BOT" -> {
                engine.stop()
                engine.start()
            }
            "STOP_BOT" -> engine.stop()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        engine.stop()
        super.onDestroy()
    }

    companion object {
        var instance: KingdomGuardBotService? = null
    }
}
