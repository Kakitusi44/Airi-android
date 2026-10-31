package com.airi.assistant

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.util.Locale

class FloatingService : Service(), TextToSpeech.OnInitListener {

    private lateinit var wm: WindowManager
    private lateinit var root: View
    private lateinit var tts: TextToSpeech
    private var sr: SpeechRecognizer? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private lateinit var tvStatus: TextView
    private lateinit var tvResponse: TextView
    private lateinit var btnMic: ImageButton
    private lateinit var layoutPanel: View
    private var params: WindowManager.LayoutParams? = null

    private var isTtsReady = false
    private var isListening = false

    companion object {
        const val CH = "airi_ch"
        var pendingScreenshot: ByteArray? = null
    }

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        createChannel()
        startForeground(1, buildNotif())
        showBubble()
    }

    private fun showBubble() {
        root = LayoutInflater.from(this).inflate(R.layout.floating_bubble, null)
        tvStatus = root.findViewById(R.id.tvStatus)
        tvResponse = root.findViewById(R.id.tvResponse)
        btnMic = root.findViewById(R.id.btnMic)
        layoutPanel = root.findViewById(R.id.layoutPanel)
        root.findViewById<TextView>(R.id.tvName).text = Prefs.getPersonaName(this)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30; y = 200
        }

        setupTouch()
        root.findViewById<ImageButton>(R.id.btnClose).setOnClickListener { stopSelf() }
        root.findViewById<ImageButton>(R.id.btnCollapse).setOnClickListener {
            layoutPanel.visibility = View.GONE
        }
        root.findViewById<ImageButton>(R.id.btnClear).setOnClickListener {
            AIClient.clearHistory()
            tvResponse.text = ""
            tvStatus.text = "История очищена"
        }
        btnMic.setOnClickListener {
            if (isListening) stopListen() else startListen()
        }

        layoutPanel.visibility = View.GONE
        wm.addView(root, params)
    }

    private var ix = 0; private var iy = 0
    private var tx = 0f; private var ty = 0f
    private var moved = false

    private fun setupTouch() {
        root.findViewById<View>(R.id.bubble).setOnTouchListener { _, ev ->
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    ix = params!!.x; iy = params!!.y
                    tx = ev.rawX; ty = ev.rawY; moved = false; true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - tx).toInt(); val dy = (ev.rawY - ty).toInt()
                    if (dx * dx + dy * dy > 25) moved = true
                    params!!.x = ix + dx; params!!.y = iy + dy
                    wm.updateViewLayout(root, params); true
                }
                MotionEvent.ACTION_UP -> { if (!moved) togglePanel(); true }
                else -> false
            }
        }
    }

    private fun togglePanel() {
        layoutPanel.visibility =
            if (layoutPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
    }

    private fun startListen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            tvStatus.text = "Речь недоступна"; return
        }
        isListening = true
        tvStatus.text = "Слушаю..."
        sr = SpeechRecognizer.createSpeechRecognizer(this)
        sr!!.setRecognitionListener(object : RecognitionListener {
            override fun onResults(r: Bundle?) {
                val text = r?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                isListening = false
                if (!text.isNullOrBlank()) { tvStatus.text = "Вы: $text"; ask(text) }
                else tvStatus.text = "Не расслышала, попробуйте ещё"
            }
            override fun onError(e: Int) { isListening = false; tvStatus.text = "Ошибка ($e)" }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() { tvStatus.text = "Думаю..." }
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        sr!!.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        })
    }

    private fun stopListen() { sr?.stopListening(); isListening = false }

    private fun ask(text: String) {
        val key = Prefs.getApiKey(this)
        if (key.isBlank()) { tvStatus.text = "Нет API ключа!"; return }
        val name = Prefs.getPersonaName(this)
        val shot = pendingScreenshot.also { pendingScreenshot = null }
        scope.launch {
            try {
                val reply = AIClient.chat(
                    apiKey = key,
                    model = Prefs.getModel(this@FloatingService),
                    systemPrompt = "Тебя зовут $name. ${Prefs.getPersonaDesc(this@FloatingService)}",
                    userMessage = text,
                    screenshotBytes = shot
                )
                tvResponse.text = reply
                tvStatus.text = name
                if (isTtsReady) tts.speak(reply, TextToSpeech.QUEUE_FLUSH, null, null)
            } catch (e: Exception) {
                tvStatus.text = "Ошибка: ${e.message}"
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale("ru", "RU")
            isTtsReady = true
        }
    }

    private fun createChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CH, "Айри", NotificationManager.IMPORTANCE_LOW)
        )
    }

    private fun buildNotif() = NotificationCompat.Builder(this, CH)
        .setContentTitle("${Prefs.getPersonaName(this)} активна")
        .setContentText("Нажмите на пузырь для разговора")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .build()

    override fun onBind(i: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel(); tts.shutdown(); sr?.destroy()
        if (::root.isInitialized) wm.removeView(root)
        super.onDestroy()
    }
}
