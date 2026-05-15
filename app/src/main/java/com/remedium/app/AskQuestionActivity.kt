package com.remedium.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.Locale

class AskQuestionActivity : Activity() {

    private val RECORD_AUDIO_REQUEST_CODE = 1001

    private lateinit var tvMedicineName: TextView
    private lateinit var btnClose: ImageButton
    private lateinit var etQuestion: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var btnMic: ImageButton
    private lateinit var loadingOverlay: FrameLayout
    private lateinit var tvLoading: TextView
    private lateinit var chatRecyclerView: RecyclerView

    private var medicineName: String = ""
    private var medicineUses: String = ""
    private var medicineDose: String = ""
    private var medicineMaxDose: String = ""
    private var medicineSchedule: String = ""
    private var medicinePregnancy: String = ""
    private var medicineContra: String = ""
    private var currentLanguage: String = "en"
    private var gemmaReady: Boolean = false
    private var gemmaReasoner: GemmaReasoner = GemmaReasoner.getInstance(this)
    private var ttsReady: Boolean = false
    private var tts: TextToSpeech? = null

    private lateinit var chatAdapter: ChatAdapter
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_ask_question)

        window?.setLayout(
            android.view.WindowManager.LayoutParams.MATCH_PARENT,
            android.view.WindowManager.LayoutParams.MATCH_PARENT
        )

        medicineName = intent.getStringExtra("medicineName") ?: ""
        medicineUses = intent.getStringExtra("medicineUses") ?: ""
        medicineDose = intent.getStringExtra("medicineDose") ?: ""
        medicineMaxDose = intent.getStringExtra("medicineMaxDose") ?: ""
        medicineSchedule = intent.getStringExtra("medicineSchedule") ?: ""
        medicinePregnancy = intent.getStringExtra("medicinePregnancy") ?: ""
        medicineContra = intent.getStringExtra("medicineContra") ?: ""
        currentLanguage = intent.getStringExtra("language") ?: "en"
        gemmaReady = intent.getBooleanExtra("gemmaReady", false)
        ttsReady = intent.getBooleanExtra("ttsReady", false)

        initViews()
        setupChatRecycler()
        setupListeners()
        initTts()
        initSpeechRecognizer()
    }

    private fun initViews() {
        tvMedicineName = findViewById(R.id.tvMedicineName)
        btnClose = findViewById(R.id.btnClose)
        etQuestion = findViewById(R.id.etQuestion)
        btnSend = findViewById(R.id.btnSend)
        btnMic = findViewById(R.id.btnMic)
        loadingOverlay = findViewById(R.id.loadingOverlay)
        tvLoading = findViewById(R.id.tvLoading)
        chatRecyclerView = findViewById(R.id.chatRecyclerView)

        tvMedicineName.text = if (currentLanguage == "hi")
            "$medicineName के बारे में पूछें"
        else
            "Ask about $medicineName"

        tvLoading.text = if (currentLanguage == "hi")
            "Gemma 4 सोच रहा है..."
        else
            "Gemma is thinking..."

        etQuestion.hint = if (currentLanguage == "hi")
            "आपका प्रश्न..."
        else
            "Your question..."

        btnMic.contentDescription = if (currentLanguage == "hi")
            "बोलें"
        else
            "Speak"
    }

    private fun setupChatRecycler() {
        chatAdapter = ChatAdapter { answer ->
            speakAnswer(answer)
        }
        chatRecyclerView.layoutManager = LinearLayoutManager(this)
        chatRecyclerView.adapter = chatAdapter
    }

    private fun setupListeners() {
        btnClose.setOnClickListener {
            stopListening()
            tts?.stop()
            finish()
        }

        btnSend.setOnClickListener {
            val question = etQuestion.text.toString().trim()
            if (question.isNotEmpty()) {
                sendQuestion(question)
            }
        }

        btnMic.setOnClickListener {
            if (isListening) {
                stopListening()
            } else {
                startListening()
            }
        }
    }

    private fun initTts() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                val locale = if (currentLanguage == "hi") Locale("hi", "IN") else Locale.US
                tts?.language = locale
                Log.d("AskQuestion", "TTS initialized with locale: $locale")
            } else {
                Log.e("AskQuestion", "TTS initialization failed")
            }
        }
    }

    private var isSpeaking: Boolean = false
    private var currentSpeakingText: String = ""

    private fun speakAnswer(text: String) {
        if (tts == null || !ttsReady) {
            Toast.makeText(
                this,
                if (currentLanguage == "hi") "आवाज़ उपलब्ध नहीं है"
                else "Voice not available",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        // If already speaking, stop first (toggle behavior)
        if (isSpeaking) {
            tts?.stop()
            isSpeaking = false
            Log.d("AskQuestion", "Stopped speaking")
            return
        }

        // Clean text - remove asterisks, markdown, special characters
        val cleanedText = cleanTextForSpeech(text)
        if (cleanedText.isBlank()) {
            Toast.makeText(
                this,
                if (currentLanguage == "hi") "पढ़ने के लिए कुछ नहीं है"
                else "Nothing to read",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        currentSpeakingText = cleanedText
        isSpeaking = true

        // Set language - prefer Indian locale for Hindi
        val locale = if (currentLanguage == "hi") Locale("hi", "IN") else Locale.US

        // Try to use higher quality voice
        tts?.language = locale
        tts?.setSpeechRate(0.85f) // Slightly slower for clarity
        tts?.setPitch(1.0f)

        // Try to set language with country-specific variant for better accent
        if (currentLanguage == "hi") {
            try {
                // Try different locale variants for better Hindi
                val hiLocale = Locale("hi", "IN")
                val result = tts?.setLanguage(hiLocale)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    // Fallback to generic Hindi
                    tts?.setLanguage(Locale("hi"))
                }
            } catch (e: Exception) {
                Log.e("AskQuestion", "Could not set Hindi language: ${e.message}")
            }
        }

        tts?.speak(cleanedText, TextToSpeech.QUEUE_FLUSH, null, "chat_answer_${System.currentTimeMillis()}")
        Log.d("AskQuestion", "Speaking cleaned text: ${cleanedText.take(50)}...")

        // Listen for completion
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                isSpeaking = false
                runOnUiThread {
                    // Update UI if needed
                }
            }

            override fun onError(utteranceId: String?) {
                isSpeaking = false
            }
        })
    }

    private fun cleanTextForSpeech(text: String): String {
        return text
            .replace(Regex("\\*+"), "") // Remove asterisks
            .replace(Regex("#+"), "")   // Remove hash symbols
            .replace(Regex("_+"), "")   // Remove underscores
            .replace(Regex("`+"), "")  // Remove backticks
            .replace(Regex("\\n+"), " ") // Replace newlines with spaces
            .replace(Regex("\\s+"), " ") // Multiple spaces to single
            .replace(Regex("\\[([^\\]]+)\\]\\([^)]+\\)"), "$1") // Remove markdown links, keep text
            .trim()
    }

    private fun initSpeechRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {}
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    isListening = false
                    updateMicButton()
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH -> if (currentLanguage == "hi") "कुछ नहीं समझा" else "Didn't understand"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> if (currentLanguage == "hi") "कोई आवाज नहीं" else "No speech input"
                        else -> if (currentLanguage == "hi") "त्रुटि" else "Error"
                    }
                    Toast.makeText(this@AskQuestionActivity, errorMsg, Toast.LENGTH_SHORT).show()
                }

                override fun onResults(results: Bundle?) {
                    isListening = false
                    updateMicButton()
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        etQuestion.setText(matches[0])
                        sendQuestion(matches[0])
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        } else {
            btnMic.visibility = View.GONE
            Toast.makeText(this, "Voice input not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startListening() {
        if (speechRecognizer == null) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_REQUEST_CODE
            )
            return
        }

        isListening = true
        updateMicButton()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (currentLanguage == "hi") "hi-IN" else "en-US")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }

        try {
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            Log.e("AskQuestion", "Speech recognition error: ${e.message}")
            isListening = false
            updateMicButton()
        }
    }

    private fun stopListening() {
        isListening = false
        speechRecognizer?.stopListening()
        updateMicButton()
    }

    private fun updateMicButton() {
        runOnUiThread {
            btnMic.alpha = if (isListening) 0.5f else 1.0f
            btnMic.setImageResource(
                if (isListening) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_btn_speak_now
            )
        }
    }

    private fun sendQuestion(question: String) {
        if (!gemmaReady) {
            Toast.makeText(
                this,
                if (currentLanguage == "hi") "Gemma 4 तैयार नहीं है।"
                else "Gemma is not ready. Please wait.",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        loadingOverlay.visibility = View.VISIBLE
        btnSend.isEnabled = false
        etQuestion.isEnabled = false
        etQuestion.text.clear()

        val medContext = buildString {
            appendLine("MEDICINE: $medicineName")
            if (medicineUses.isNotBlank()) appendLine("USES: $medicineUses")
            if (medicineDose.isNotBlank()) appendLine("DOSE: $medicineDose")
            if (medicineMaxDose.isNotBlank()) appendLine("MAX_DAILY: $medicineMaxDose")
            if (medicineSchedule.isNotBlank()) appendLine("SCHEDULE: $medicineSchedule")
            if (medicinePregnancy.isNotBlank()) appendLine("PREGNANCY_CAT: $medicinePregnancy")
            if (medicineContra.isNotBlank()) appendLine("CONTRAINDICATIONS: $medicineContra")
        }

        val prompt = """
You are Remedium's medicine assistant. Answer questions using ONLY
the verified information provided below.

RULES:
- If the answer is not in the provided information, say EXACTLY:
  "I don't have verified information about that. Please ask your pharmacist or doctor."
- NEVER invent medical facts
- NEVER recommend changing doses
- NEVER diagnose conditions
- Answer in the same language as the QUESTION field

VERIFIED MEDICINE DATA:
$medContext

PATIENT CONTEXT:
PATIENT_AGE_GROUP: adult

QUESTION: $question
""".trimIndent()

        val responseBuilder = StringBuilder()

        gemmaReasoner?.reason(
            prompt = prompt,
            onToken = { token ->
                responseBuilder.append(token)
                runOnUiThread {
                    // Update last message in chat if exists
                    val lastAnswer = chatAdapter.getLastAnswer()
                    if (lastAnswer.isNotEmpty()) {
                        chatAdapter.notifyItemChanged(chatAdapter.itemCount - 1)
                    }
                }
            },
            onDone = {
                runOnUiThread {
                    loadingOverlay.visibility = View.GONE
                    btnSend.isEnabled = true
                    etQuestion.isEnabled = true

                    val fullResponse = responseBuilder.toString()

                    // Add to chat history
                    chatAdapter.addMessage(question, fullResponse)

                    // Scroll to bottom
                    chatRecyclerView.post {
                        chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                    }

                    // Speak the answer
                    speakAnswer(fullResponse)
                }
            },
            onTimeout = {
                runOnUiThread {
                    loadingOverlay.visibility = View.GONE
                    btnSend.isEnabled = true
                    etQuestion.isEnabled = true

                    val timeoutMsg = if (currentLanguage == "hi")
                        "समय समाप्त। कृपया पुनः प्रयास करें।"
                    else
                        "Timed out. Please try again."

                    // Add timeout message to chat
                    chatAdapter.addMessage(question, timeoutMsg)

                    chatRecyclerView.post {
                        chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
                    }

                    Toast.makeText(this, timeoutMsg, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == RECORD_AUDIO_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening()
            } else {
                Toast.makeText(
                    this,
                    if (currentLanguage == "hi") "माइक्रोफ़ोन अनुमति चाहिए"
                    else "Microphone permission required",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        speechRecognizer?.destroy()
        tts?.stop()
        tts?.shutdown()
    }

    companion object {
        fun createIntent(
            context: android.content.Context,
            medicine: MedicineInfo,
            language: String,
            gemmaReady: Boolean,
            ttsReady: Boolean
        ): android.content.Intent {
            return Intent(context, AskQuestionActivity::class.java).apply {
                putExtra("medicineName", medicine.genericName)
                putExtra("medicineUses", medicine.uses ?: "")
                putExtra("medicineDose", medicine.dose ?: "")
                putExtra("medicineMaxDose", medicine.maxDose ?: "")
                putExtra("medicineSchedule", medicine.legalSchedule ?: "")
                putExtra("medicinePregnancy", medicine.fdaPregnancyCat ?: "")
                putExtra("medicineContra", medicine.contraindications ?: "")
                putExtra("language", language)
                putExtra("gemmaReady", gemmaReady)
                putExtra("ttsReady", ttsReady)
            }
        }
    }
}