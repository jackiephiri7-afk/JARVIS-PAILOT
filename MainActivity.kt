package com.pilot.jarvis

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var chat: LinearLayout
    private lateinit var input: EditText
    private lateinit var key: EditText
    private lateinit var status: TextView
    private var tts: TextToSpeech? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(5, 16, 28)
        window.navigationBarColor = android.graphics.Color.rgb(5, 16, 28)
        tts = TextToSpeech(this, this)
        buildUi()
        addMessage("JARVIS", "Hello. I'm JARVIS PAILOT. Add your Gemini API key to start chatting. You can also use the microphone for speech input.")
    }

    private fun buildUi() {
        val bg = android.graphics.Color.rgb(7, 18, 31)
        val cyan = android.graphics.Color.rgb(48, 220, 205)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 18, 18, 12)
            setBackgroundColor(bg)
        }
        val title = TextView(this).apply {
            text = "J.A.R.V.I.S  /  PAILOT"
            textSize = 22f
            setTextColor(cyan)
            gravity = Gravity.CENTER
            setPadding(4, 12, 4, 14)
        }
        root.addView(title)
        key = EditText(this).apply {
            hint = "Gemini API key (not saved)"
            setSingleLine(true)
            setTextColor(-1)
            setHintTextColor(0xff9aabbc.toInt())
            inputType = 0x00000081
            setBackgroundColor(0xff14283b.toInt())
            setPadding(12, 8, 12, 8)
        }
        root.addView(key, LinearLayout.LayoutParams(-1, 52))
        status = TextView(this).apply {
            text = "Ready"
            textSize = 12f
            setTextColor(0xff9aabbc.toInt())
            setPadding(4, 8, 4, 8)
        }
        root.addView(status)
        val scroll = ScrollView(this)
        chat = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(2, 4, 2, 12)
        }
        scroll.addView(chat)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        input = EditText(this).apply {
            hint = "Ask JARVIS something..."
            setTextColor(-1)
            setHintTextColor(0xff9aabbc.toInt())
            setBackgroundColor(0xff14283b.toInt())
            setPadding(12, 8, 12, 8)
        }
        root.addView(input, LinearLayout.LayoutParams(-1, 54))
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val mic = Button(this).apply { text = "MIC"; setOnClickListener { startListening() } }
        val send = Button(this).apply { text = "SEND"; setOnClickListener { sendMessage() } }
        buttons.addView(mic, LinearLayout.LayoutParams(0, 52, 1f))
        buttons.addView(send, LinearLayout.LayoutParams(0, 52, 1f))
        root.addView(buttons)
        setContentView(root)
    }

    private fun addMessage(who: String, message: String) {
        val tv = TextView(this).apply {
            text = "$who\n$message"
            textSize = 15f
            setTextColor(if (who == "JARVIS") 0xffd9f4f2.toInt() else -1)
            setBackgroundColor(if (who == "JARVIS") 0xff123344.toInt() else 0xff243247.toInt())
            setPadding(14, 12, 14, 12)
        }
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.setMargins(0, 6, 0, 6)
        chat.addView(tv, lp)
    }

    private fun sendMessage() {
        val prompt = input.text.toString().trim()
        val apiKey = key.text.toString().trim()
        if (prompt.isEmpty()) return
        if (apiKey.isEmpty()) {
            addMessage("JARVIS", "Please enter your Gemini API key first. Keep it private.")
            return
        }
        input.setText("")
        addMessage("YOU", prompt)
        status.text = "Thinking..."
        thread {
            var conn: HttpURLConnection? = null
            try {
                val endpoint = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=$apiKey")
                conn = (endpoint.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 20000
                    readTimeout = 30000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                val part = JSONObject().put("text", "You are JARVIS, a helpful, concise personal AI assistant. Be honest about limitations. User says: $prompt")
                val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(part))))
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) throw Exception("AI service returned HTTP $code. Check your API key and model access.")
                val answer = JSONObject(response).getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts").getJSONObject(0).getString("text")
                runOnUiThread {
                    addMessage("JARVIS", answer)
                    status.text = "Ready"
                    tts?.speak(answer, TextToSpeech.QUEUE_FLUSH, null, "jarvis_reply")
                }
            } catch (e: Exception) {
                runOnUiThread {
                    addMessage("JARVIS", "I couldn't reach the AI service: ${e.message ?: "unknown error"}")
                    status.text = "Connection problem"
                }
            } finally {
                conn?.disconnect()
            }
        }
    }

    private fun startListening() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            addMessage("JARVIS", "Speech recognition isn't available on this phone.")
            return
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to JARVIS")
        }
        try { startActivityForResult(intent, 101) } catch (_: Exception) {
            addMessage("JARVIS", "Couldn't start speech input.")
        }
    }

    @Deprecated("Deprecated by Android, retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 101 && resultCode == RESULT_OK) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                input.setText(spoken)
                sendMessage()
            }
        }
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) tts?.language = Locale.getDefault()
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}
