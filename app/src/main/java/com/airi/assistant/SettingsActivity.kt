package com.airi.assistant

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val etApiKey = findViewById<EditText>(R.id.etApiKey)
        val etPersonaName = findViewById<EditText>(R.id.etPersonaName)
        val etPersonaDesc = findViewById<EditText>(R.id.etPersonaDesc)
        val etModel = findViewById<EditText>(R.id.etModel)

        etApiKey.setText(Prefs.getApiKey(this))
        etPersonaName.setText(Prefs.getPersonaName(this))
        etPersonaDesc.setText(Prefs.getPersonaDesc(this))
        etModel.setText(Prefs.getModel(this))

        findViewById<Button>(R.id.btnSave).setOnClickListener {
            Prefs.setApiKey(this, etApiKey.text.toString().trim())
            Prefs.setPersonaName(this, etPersonaName.text.toString().trim().ifBlank { "Айри" })
            Prefs.setPersonaDesc(this, etPersonaDesc.text.toString().trim())
            Prefs.setModel(this, etModel.text.toString().trim()
                .ifBlank { "meta-llama/llama-3.2-11b-vision-instruct:free" })
            Toast.makeText(this, "Сохранено!", Toast.LENGTH_SHORT).show()
            finish()
        }
    }
}
