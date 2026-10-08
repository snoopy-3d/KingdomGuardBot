package kingdomguard.bot

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.SharedPreferences
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

class BotEngine(
    private val service: AccessibilityService,
    private val prefs: SharedPreferences
) {

    companion object {
        private const val TAG = "KingdomBot"

        // ========== КООРДИНАТЫ ДЛЯ КАЛИБРОВКИ ==========
        val CONFIG = mapOf(
            // Монстры на карте (3 точки по умолчанию)
            "MONSTER_1_X" to 500, "MONSTER_1_Y" to 400,
            "MONSTER_2_X" to 700, "MONSTER_2_Y" to 350,
            "MONSTER_3_X" to 300, "MONSTER_3_Y" to 500,
            // Кнопка «Собрать» (жёлтая внизу)
            "GATHER_BTN_X" to 270, "GATHER_BTN_Y" to 1300,
            // Кнопка «Отправиться» (жёлтая внизу)
            "SEND_BTN_X" to 540, "SEND_BTN_Y" to 1400,
            // Слоты войск (5 штук) — подбирай под своё разрешение
            "TROOP_1_X" to 540, "TROOP_1_Y" to 700,
            "TROOP_2_X" to 540, "TROOP_2_Y" to 800,
            "TROOP_3_X" to 540, "TROOP_3_Y" to 900,
            "TROOP_4_X" to 540, "TROOP_4_Y" to 1000,
            "TROOP_5_X" to 540, "TROOP_5_Y" to 1100,
        )
    }

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var currentMonsterIndex = 0

    private val monsterPositions = listOf(
        "MONSTER_1" to 4,
        "MONSTER_2" to 4,
        "MONSTER_3" to 5,
    )

    fun start() {
        running = true
        currentMonsterIndex = 0
        log("🤖 Бот запущен")
        runCycle()
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        log("⏹ Бот остановлен")
    }

    fun isRunning() = running

    private fun runCycle() {
        if (!running) return
        log("🔍 Ищу монстра (уровень 4-5)...")
        findAndClickMonster()
    }

    private fun findAndClickMonster() {
        if (!running) return
        val pos = monsterPositions[currentMonsterIndex]
        val x = CONFIG["${pos.first}_X"] ?: 500
        val y = CONFIG["${pos.first}_Y"] ?: 400
        log("👾 Кликаю по монстру (${pos.second}) на ($x, $y)")
        performClick(x, y)
        handler.postDelayed({ checkForGatherButton() }, 2000)
    }

    private fun checkForGatherButton() {
        if (!running) return
        val button = findViewByText("Собрать")
        if (button != null) {
            log("✅ Нашёл «Собрать» — нажимаю")
            button.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            handler.postDelayed({ selectTroopAndSend() }, 2000)
        } else {
            log("«Собрать» не найден, ищу «Отправиться»...")
            handler.postDelayed({ selectTroopAndSend() }, 1500)
        }
    }

    private fun selectTroopAndSend() {
        if (!running) return
        val freeCount = countFreeTroops()
        log("⚔ Свободных войск: $freeCount из 5")
        if (freeCount > 0) {
            val troopIdx = 5 - freeCount + 1
            performClick(
                CONFIG["TROOP_${troopIdx}_X"] ?: 540,
                CONFIG["TROOP_${troopIdx}_Y"] ?: 700
            )
            log("Выбрал войско #$troopIdx")
        }
        handler.postDelayed({ sendTroops() }, 1500)
    }

    private fun countFreeTroops(): Int {
        val root = service.rootInActiveWindow ?: return 5
        val timerNodes = mutableListOf<AccessibilityNodeInfo>()
        collectNodesWithText(root, Regex("\\d{2}:\\d{2}:\\d{2}"), timerNodes)
        return (5 - timerNodes.size).coerceIn(0, 5)
    }

    private fun sendTroops() {
        if (!running) return
        val sendBtn = findViewByText("Отправиться")
        if (sendBtn != null) {
            log("🚀 Нажимаю «Отправиться»")
            sendBtn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        } else {
            performClick(
                CONFIG["SEND_BTN_X"] ?: 540,
                CONFIG["SEND_BTN_Y"] ?: 1400
            )
            log("🚀 Нажал «Отправиться» по координатам")
        }
        handler.postDelayed({ waitForReturn() }, 5000)
    }

    private fun waitForReturn() {
        if (!running) return
        currentMonsterIndex = (currentMonsterIndex + 1) % monsterPositions.size
        checkTroopsTimer()
    }

    private fun checkTroopsTimer() {
        if (!running) return
        val root = service.rootInActiveWindow
        val timers = mutableListOf<AccessibilityNodeInfo>()
        if (root != null) {
            collectNodesWithText(root, Regex("\\d{2}:\\d{2}:\\d{2}"), timers)
        }
        if (timers.isEmpty()) {
            log("✅ Войска вернулись! Новый цикл.")
            handler.postDelayed({ runCycle() }, 3000)
        } else {
            log("⏳ Занято: ${timers.size}. Жду...")
            handler.postDelayed({ checkTroopsTimer() }, 15000)
        }
    }

    private fun performClick(x: Int, y: Int) {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
            lineTo(x.toFloat(), y.toFloat())
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()
        service.dispatchGesture(gesture, null, null)
    }

    private fun findViewByText(text: String): AccessibilityNodeInfo? {
        val root = service.rootInActiveWindow ?: return null
        return root.findAccessibilityNodeInfosByText(text)?.firstOrNull()
    }

    private fun collectNodesWithText(
        node: AccessibilityNodeInfo,
        regex: Regex,
        result: MutableList<AccessibilityNodeInfo>
    ) {
        if (node.text?.toString()?.matches(regex) == true) {
            result.add(node)
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { collectNodesWithText(it, regex, result) }
        }
    }

    private fun log(msg: String) {
        prefs.edit().putString("last_log", msg).apply()
        Log.d(TAG, msg)
    }
}
