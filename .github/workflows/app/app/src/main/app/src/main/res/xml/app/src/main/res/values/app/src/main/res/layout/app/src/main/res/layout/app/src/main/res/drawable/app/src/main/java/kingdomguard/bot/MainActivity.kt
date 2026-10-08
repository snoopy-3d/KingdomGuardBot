package kingdomguard.bot

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val OVERLAY_PERMISSION_REQUEST = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnStart).setOnClickListener { checkPermissions() }
        findViewById<Button>(R.id.btnCalibrate).setOnClickListener { showCalibrationHelp() }

        updateStatus()
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
            return
        }
        if (!isAccessibilityEnabled()) {
            AlertDialog.Builder(this)
                .setTitle("Включите Accessibility Service")
                .setMessage("Откройте «Установленные приложения» → Kingdom Guard Bot → «Служба специальных возможностей» → включите.\n\nЗатем вернитесь сюда и нажмите «Старт» снова.")
                .setPositiveButton("Открыть настройки") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Отмена", null)
                .show()
            return
        }
        startOverlay()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains("$packageName/.KingdomGuardBotService")
    }

    private fun startOverlay() {
        startService(Intent(this, OverlayService::class.java))
        Toast.makeText(this, "✅ Кнопка поверх игры активна", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun updateStatus() {
        val status = findViewById<TextView>(R.id.tvStatus)
        val overlayOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
        val accOk = isAccessibilityEnabled()

        status.text = when {
            !overlayOk -> "❌ Нет разрешения на поверхностные окна"
            !accOk -> "❌ Нет разрешения Accessibility Service"
            else -> "✅ Всё готово — нажмите «Старт»"
        }
    }

    private fun showCalibrationHelp() {
        AlertDialog.Builder(this)
            .setTitle("Калибровка координат")
            .setMessage(
                "Откройте BotEngine.kt и настройте CONFIG:\n\n" +
                "• MONSTER_1_X, MONSTER_1_Y — позиция 1-го монстра\n" +
                "• GATHER_BTN_X/Y — кнопка «Собрать»\n" +
                "• SEND_BTN_X/Y — кнопка «Отправиться»\n" +
                "• TROOP_1_X/Y … TROOP_5_X/Y — слоты 5 войск\n\n" +
                "Сделайте скриншот, откройте в редакторе, посмотрите координаты пикселей."
            )
            .setPositiveButton("Понял", null)
            .show()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        checkPermissions()
    }
}
