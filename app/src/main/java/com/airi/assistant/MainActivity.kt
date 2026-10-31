package com.airi.assistant

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnLaunch).setOnClickListener {
            when {
                !Settings.canDrawOverlays(this) -> {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                }
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED -> {
                    ActivityCompat.requestPermissions(this,
                        arrayOf(Manifest.permission.RECORD_AUDIO), 1)
                }
                Prefs.getApiKey(this).isBlank() -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
                else -> {
                    startService(Intent(this, FloatingService::class.java))
                    finish()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val overlayOk = Settings.canDrawOverlays(this)
        val audioOk = ContextCompat.checkSelfPermission(this,
            Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val apiOk = Prefs.getApiKey(this).isNotBlank()

        findViewById<TextView>(R.id.tvStatus).text = buildString {
            appendLine(if (overlayOk) "✓ Overlay" else "✗ Нужно разрешение Overlay")
            appendLine(if (audioOk) "✓ Микрофон" else "✗ Нужен доступ к микрофону")
            append(if (apiOk) "✓ API ключ" else "✗ Нужен API ключ (Настройки)")
        }
        findViewById<TextView>(R.id.tvAssistantName).text = Prefs.getPersonaName(this)
    }

    override fun onRequestPermissionsResult(requestCode: Int,
        permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED &&
            Prefs.getApiKey(this).isNotBlank()) {
            startService(Intent(this, FloatingService::class.java))
            finish()
        }
    }
}
