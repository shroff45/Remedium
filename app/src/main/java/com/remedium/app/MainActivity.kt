package com.remedium.app

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Typeface
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.View
import android.widget.Button
import com.google.android.material.floatingactionbutton.FloatingActionButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import android.widget.EditText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var cameraPreview: PreviewView
    private lateinit var statusText: TextView
    private lateinit var captureButton: Button
    private lateinit var imageCapture: ImageCapture
    private lateinit var resultPanel: ConstraintLayout
    private lateinit var resultText: TextView
    private lateinit var resultScroll: ScrollView
    private lateinit var scanAgainButton: Button
    private lateinit var languageToggleButton: Button
    private lateinit var speakButton: Button
    private lateinit var zoomButton: Button
    private lateinit var askQuestionButton: FloatingActionButton
    private lateinit var medicineLookup: MedicineLookup
    private var gemmaReasoner: GemmaReasoner? = null
    private var gemmaReady = false
    private var hasTier1A = false  // Track if current result has Tier 1A
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val ioScope = CoroutineScope(Dispatchers.IO)

    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    private var currentLanguage = "en"
    private lateinit var prefs: SharedPreferences
    private var lastOcrText: String = ""
    private var lastMedicines: List<MedicineInfo> = emptyList()

    // ← NEW: store last Tier 2 results for language-toggle rebuild
    private var lastTier2Results: List<Tier2Info> = emptyList()

    private var camera: androidx.camera.core.Camera? = null
    private var currentZoom = 0f

    private val CAMERA_PERMISSION_REQUEST = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Load persisted language preference
        prefs = getSharedPreferences("remedium_prefs", Context.MODE_PRIVATE)
        currentLanguage = prefs.getString("language", "en") ?: "en"

        cameraPreview         = findViewById(R.id.cameraPreview)
        statusText            = findViewById(R.id.statusText)
        captureButton         = findViewById(R.id.captureButton)
        resultPanel           = findViewById(R.id.resultPanel)
        resultText            = findViewById(R.id.resultText)
        resultScroll          = findViewById(R.id.resultScroll)
        scanAgainButton       = findViewById(R.id.scanAgainButton)
        languageToggleButton  = findViewById(R.id.languageToggleButton)
        speakButton           = findViewById(R.id.speakButton)
        zoomButton            = findViewById(R.id.zoomButton)
        askQuestionButton    = findViewById(R.id.askQuestionButton)

        medicineLookup = MedicineLookup(this)

        // Initialize Gemma 4 reasoner
        gemmaReasoner = GemmaReasoner.getInstance(this)
        gemmaReasoner!!.initAsync(
            onReady = {
                gemmaReady = true
                Log.i("Remedium", "Gemma 4 ready for questions")
            },
            onError = { error ->
                Log.w("Remedium", "Gemma 4 init failed: $error")
                gemmaReady = false
            }
        )

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                ttsReady = true
                setTtsLanguage()
            } else {
                Log.w("Remedium", "TTS init failed with status $status")
            }
        }

        captureButton.setOnClickListener { takePhoto() }
        scanAgainButton.setOnClickListener { hideResultPanel() }

        languageToggleButton.setOnClickListener {
            if (currentLanguage == "en") {
                currentLanguage = "hi"
                languageToggleButton.text = "EN"
            } else {
                currentLanguage = "en"
                languageToggleButton.text = "\u0939\u093F\u0902"
            }
            prefs.edit().putString("language", currentLanguage).apply()
            setTtsLanguage()
            // Update ask question button text if visible
            if (askQuestionButton.visibility == View.VISIBLE) {
                askQuestionButton.contentDescription = if (currentLanguage == "hi") "\u092A\u094D\u0930\u0936\u094D\u0928 \u092A\u0942\u091B\u0947\u0902" else "Ask a question"
            }
            if (resultPanel.visibility == View.VISIBLE) {
                rebuildCurrentResult()
            }
        }

        speakButton.setOnClickListener {
            if (!ttsReady) {
                statusText.text = "Speech engine not ready."
                return@setOnClickListener
            }
            val textToSpeak = buildSpeakableText(lastMedicines)
            tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "remedium_tts")
        }

        askQuestionButton.setOnClickListener {
            if (!gemmaReady) {
                statusText.text = "Gemma 4 not ready. Please wait..."
                return@setOnClickListener
            }
            if (lastMedicines.isEmpty()) {
                statusText.text = "No verified medicine to ask about."
                return@setOnClickListener
            }
            showQuestionDialog()
        }

        zoomButton.setOnClickListener {
            currentZoom = when {
                currentZoom < 0.3f -> 0.3f
                currentZoom < 0.6f -> 0.6f
                else -> 0f
            }
            val label = when (currentZoom) {
                0f    -> "1x"
                0.3f  -> "1.5x"
                else  -> "2.5x"
            }
            zoomButton.text = label
            camera?.cameraControl?.setLinearZoom(currentZoom)
        }

        if (hasCameraPermission()) startCamera() else requestCameraPermission()
    }

    private fun setTtsLanguage() {
        if (!::tts.isInitialized) return
        try {
            val locale = if (currentLanguage == "hi") Locale("hi", "IN") else Locale.ENGLISH
            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w("Remedium", "TTS locale not supported: $locale")
            }
        } catch (e: Exception) {
            Log.e("Remedium", "TTS locale error", e)
        }
    }

    private fun hasCameraPermission() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(
            this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startCamera()
            } else {
                statusText.text = "Camera permission denied"
            }
        }
    }

    private fun startCamera() {
        statusText.text = "Starting camera..."
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(cameraPreview.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setTargetRotation(cameraPreview.display.rotation)
                .build()
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                )
                cameraPreview.setOnTouchListener { _, event ->
                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                        val factory = cameraPreview.meteringPointFactory
                        val point   = factory.createPoint(event.x, event.y)
                        val action  = androidx.camera.core.FocusMeteringAction.Builder(
                            point, androidx.camera.core.FocusMeteringAction.FLAG_AF
                        ).setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS).build()
                        camera?.cameraControl?.startFocusAndMetering(action)
                        cameraPreview.performClick()
                    }
                    true
                }
                statusText.text = "Tap to focus \u2192 Use 1x/1.5x/2.5x for small strips \u2192 Scan."
            } catch (e: Exception) {
                statusText.text = "Camera failed: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        statusText.text = "Focusing..."
        captureButton.isEnabled = false
        val factory     = cameraPreview.meteringPointFactory
        val centerPoint = factory.createPoint(
            cameraPreview.width / 2f, cameraPreview.height / 2f
        )
        val focusAction = androidx.camera.core.FocusMeteringAction.Builder(
            centerPoint, androidx.camera.core.FocusMeteringAction.FLAG_AF
        ).disableAutoCancel().build()
        val focusFuture = camera?.cameraControl?.startFocusAndMetering(focusAction)
        if (focusFuture != null) {
            focusFuture.addListener({
                runOnUiThread { statusText.text = "Capturing..." }
                doActualCapture()
            }, ContextCompat.getMainExecutor(this))
        } else {
            doActualCapture()
        }
    }

    private fun doActualCapture() {
        imageCapture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val bitmap = imageProxyToBitmap(image)
                    image.close()
                    runOnUiThread { statusText.text = "Processing image..." }
                    runOCR(bitmap)
                }
                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        statusText.text = "Capture failed: ${exception.message}"
                        captureButton.isEnabled = true
                    }
                }
            }
        )
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val buffer = image.planes[0].buffer
        val bytes  = ByteArray(buffer.remaining())
        buffer.get(bytes)
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        val maxSide = 3200
        var sample  = 1
        val longest = maxOf(opts.outWidth, opts.outHeight)
        while (longest / sample > maxSide) sample *= 2
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize     = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        var bitmap: Bitmap? = null
        var rotated: Bitmap? = null
        try {
            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
            val matrix = Matrix()
            matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
            rotated = Bitmap.createBitmap(bitmap!!, 0, 0, bitmap.width, bitmap.height, matrix, true)
            return rotated
        } finally {
            // Always recycle original bitmap, regardless of rotation success/failure
            if (rotated != bitmap && bitmap != null && !bitmap.isRecycled) {
                bitmap.recycle()
            }
        }
    }

    private fun runOCR(bitmap: Bitmap) {
        val recognizer  = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val inputImage  = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val extractedText = visionText.text
                Log.i("RemediumOCR", "RAW OCR TEXT:\n$extractedText\n--END--")
                runOnUiThread {
                    if (extractedText.isBlank()) {
                        statusText.text =
                            "No text detected. Try better lighting or move closer."
                        captureButton.isEnabled = true
                    } else {
                        showResultPanel(extractedText)
                    }
                }
                bitmap.recycle()
                recognizer.close()
            }
            .addOnFailureListener { e ->
                runOnUiThread {
                    statusText.text = "OCR failed: ${e.message}"
                    captureButton.isEnabled = true
                    Log.e("Remedium", "OCR error", e)
                }
                bitmap.recycle()
                recognizer.close()
            }
    }

    // ─────────────────────────────────────────────────────────
    // RESULT PANEL  ← CHANGED: uses SearchResult, stores Tier 2
    // ─────────────────────────────────────────────────────────

    private fun showResultPanel(text: String) {
        // Run database search on IO thread to prevent ANR
        ioScope.launch {
            val searchResult = medicineLookup.searchWithAmbiguity(text)

            withContext(Dispatchers.Main) {
                lastOcrText        = text
                lastMedicines      = searchResult.confirmed
                lastTier2Results   = searchResult.tier2Results
                hasTier1A          = searchResult.confirmed.isNotEmpty()

                // Show/hide "Ask a question" button based on Tier 1A presence
                askQuestionButton.visibility = if (hasTier1A) View.VISIBLE else View.GONE
                if (hasTier1A) {
                    askQuestionButton.contentDescription = if (currentLanguage == "hi") "प्रश्न पूछें" else "Ask a question"
                }

                val display = buildDisplayText(text, searchResult.confirmed, searchResult.tier2Results)
                resultText.text    = display
                resultPanel.visibility = View.VISIBLE
                statusText.text    = "Camera ready. Tap Scan."
                captureButton.isEnabled = true

                if (searchResult.confirmed.any {
                        it.legalSchedule?.uppercase()?.contains("H1") == true }) {
                    showScheduleH1Dialog()
                }
            }
        }
    }

    private fun rebuildCurrentResult() {
        val display = buildDisplayText(lastOcrText, lastMedicines, lastTier2Results)
        resultText.text = display
    }

    private fun hideResultPanel() {
        resultPanel.visibility = View.GONE
        statusText.text = "Camera ready. Tap Scan."
        if (::tts.isInitialized) tts.stop()
    }

    // ─────────────────────────────────────────────────────────
    // ASK QUESTION (Gemma 4)  ← NEW (Task 5)
    // ─────────────────────────────────────────────────────────

    private fun showQuestionDialog() {
        val med = lastMedicines.firstOrNull() ?: return

        val intent = AskQuestionActivity.createIntent(
            context = this,
            medicine = med,
            language = currentLanguage,
            gemmaReady = gemmaReady,
            ttsReady = ttsReady
        )
        startActivity(intent)
    }

    private fun showScheduleH1Dialog() {
        val title: String; val message: String
        val positive: String; val negative: String
        if (currentLanguage == "hi") {
            title    = "\u092A\u0930\u094D\u091A\u093E \u0906\u0935\u0936\u094D\u092F\u0915 \u0939\u0948"
            message  = "\u092F\u0939 Schedule H1 \u0926\u0935\u093E \u0939\u0948 \u2014 \u090F\u0915 \u0909\u091A\u094D\u091A-\u092A\u094D\u0930\u092D\u093E\u0935\u0936\u093E\u0932\u0940 \u090F\u0902\u091F\u0940\u092C\u093E\u092F\u0949\u091F\u093F\u0915\u0964 " +
                    "\u092D\u093E\u0930\u0924\u0940\u092F \u0915\u093E\u0928\u0942\u0928 \u0915\u0947 \u0905\u0928\u0941\u0938\u093E\u0930 \u0915\u0947\u092E\u093F\u0938\u094D\u091F \u0915\u094B \u0926\u0935\u093E \u0926\u0947\u0928\u0947 \u0938\u0947 \u092A\u0939\u0932\u0947 \u0906\u092A\u0915\u093E \u0935\u093F\u0935\u0930\u0923 \u0926\u0930\u094D\u091C \u0915\u0930\u0928\u093E \u0939\u094B\u0917\u093E\u0964 " +
                    "\u0915\u094D\u092F\u093E \u092F\u0939 \u0926\u0935\u093E \u0921\u0949\u0915\u094D\u091F\u0930 \u0928\u0947 \u0906\u092A\u0915\u094B \u0932\u093F\u0916\u0940 \u0939\u0948?"
            positive = "\u0939\u093E\u0901, \u092E\u0947\u0930\u0947 \u092A\u093E\u0938 \u092A\u0930\u094D\u091A\u093E \u0939\u0948"
            negative = "\u0928\u0939\u0940\u0902"
        } else {
            title    = "Prescription Required"
            message  = "This is a Schedule H1 medicine \u2014 a high-potency antibiotic. " +
                    "Under Indian law, the chemist must record your details before dispensing. " +
                    "Have you been prescribed this by a doctor?"
            positive = "Yes, I have a prescription"
            negative = "No"
        }
        AlertDialog.Builder(this)
            .setTitle(title).setMessage(message)
            .setPositiveButton(positive) { d, _ -> d.dismiss() }
            .setNegativeButton(negative)  { d, _ -> d.dismiss() }
            .setCancelable(false).show()
    }

    // ─────────────────────────────────────────────────────────
    // DISPLAY BUILDER  ← CHANGED: accepts tier2Results param
    // ─────────────────────────────────────────────────────────

    private fun buildDisplayText(
        rawText: String,
        medicines: List<MedicineInfo>,
        tier2Results: List<Tier2Info> = emptyList()   // ← NEW param
    ): CharSequence {
        val sb = SpannableStringBuilder()

        // ── Case 1: No results at all ─────────────────────────
        if (medicines.isEmpty() && tier2Results.isEmpty()) {
            appendTierBadge(sb, ResultTier.NOT_FOUND)   // ← NEW: grey badge
            sb.append("\n\n")
            if (currentLanguage == "hi") {
                appendBold(sb, "\u0907\u0938 \u091B\u0935\u093F \u092E\u0947\u0902 \u0915\u094B\u0908 \u091C\u094D\u091E\u093E\u0924 \u0926\u0935\u093E \u0928\u0939\u0940\u0902 \u092E\u093F\u0932\u0940\n\n")
                sb.append("\u0939\u092E \u0915\u093F\u0938\u0940 \u092D\u0940 \u0926\u0935\u093E \u0915\u094B \u0905\u092A\u0928\u0947 \u0921\u0947\u091F\u093E\u092C\u0947\u0938 \u0938\u0947 \u092E\u093F\u0932\u093E \u0928\u0939\u0940\u0902 \u0938\u0915\u0947\u0964\n\n")
                appendBold(sb, "\u0928\u093F\u0915\u093E\u0932\u093E \u0917\u092F\u093E \u091F\u0947\u0915\u094D\u0938\u094D\u091F:\n")
                sb.append(rawText).append("\n\n")
                appendItalicGrey(
                    sb,
                    "\u0905\u0938\u094D\u0935\u0940\u0915\u0930\u0923: \u092F\u0939 \u091C\u093E\u0928\u0915\u093E\u0930\u0940 \u0915\u0947\u0935\u0932 \u0936\u0948\u0915\u094D\u0937\u093F\u0915 \u0909\u0926\u094D\u0926\u0947\u0936\u094D\u092F\u094B\u0902 \u0915\u0947 \u0932\u093F\u090F \u0939\u0948\u0964 " +
                            "\u0915\u093F\u0938\u0940 \u092D\u0940 \u0926\u0935\u093E \u0915\u093E \u0938\u0947\u0935\u0928 \u0915\u0930\u0928\u0947 \u0938\u0947 \u092A\u0939\u0932\u0947 \u0921\u0949\u0915\u094D\u091F\u0930 \u0938\u0947 \u092A\u0930\u093E\u092E\u0930\u094D\u0936 \u091C\u0930\u0942\u0930 \u0932\u0947\u0902\u0964"
                )
            } else {
                appendBold(sb, "No known medicines found in this image\n\n")
                sb.append("We could not match any text to our medicine database.\n\n")
                appendBold(sb, "Extracted Text:\n")
                sb.append(rawText).append("\n\n")
                appendItalicGrey(
                    sb,
                    "Disclaimer: This information is for educational purposes only. " +
                            "Always consult a doctor before taking any medicine. " +
                            "Self-medication can be dangerous."
                )
            }
            return sb
        }

        // ── Case 2: Tier 1A results ───────────────────────────
        for ((index, med) in medicines.withIndex()) {
            if (index > 0) sb.append("\n\n\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\n\n")
            appendMedicineBlock(sb, med)
        }

        // ── Case 3: Tier 2 results (appended after Tier 1A) ← NEW
        for ((index, t2) in tier2Results.withIndex()) {
            if (medicines.isNotEmpty() || index > 0) sb.append("\n\n\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\n\n")
            appendTier2Block(sb, t2)
        }

        return sb
    }

    // ─────────────────────────────────────────────────────────
    // TIER 1A BLOCK  ← CHANGED: badge added at top, alternatives at bottom
    // ─────────────────────────────────────────────────────────

    private fun appendMedicineBlock(sb: SpannableStringBuilder, med: MedicineInfo) {
        val template = med.category?.let { medicineLookup.getTemplate(currentLanguage, it) }

        // ── Tier badge ← NEW ──────────────────────────────────
        appendTierBadge(sb, ResultTier.TIER_1A)
        sb.append("\n\n")

        // ── Critical warnings (before header, unchanged) ──────
        if (med.criticalWarnings.isNotEmpty()) {
            val critStart = sb.length
            appendColored(
                sb,
                if (currentLanguage == "hi") "\u26A0 \u0905\u0924\u094D\u092F\u0902\u0924 \u092E\u0939\u0924\u094D\u0935\u092A\u0942\u0930\u094D\u0923:\n" else "\u26A0 CRITICAL:\n",
                "#B71C1C"
            )
            for (w in med.criticalWarnings) {
                sb.append("\u2022 ").append(pickLang(w.textEn, w.textHi)).append("\n")
            }
            val tail = if (currentLanguage == "hi")
                "\u0938\u0902\u0926\u0947\u0939 \u0939\u094B \u0924\u094B \u0928 \u0932\u0947\u0902\u0964 \u0924\u0941\u0930\u0902\u0924 \u0921\u0949\u0915\u094D\u091F\u0930 \u0938\u0947 \u092E\u093F\u0932\u0947\u0902\u0964\n"
            else
                "If in doubt, do not take. Consult a doctor immediately.\n"
            appendColored(sb, tail, "#B71C1C")
            val critEnd = sb.length
            sb.setSpan(
                BackgroundColorSpan(Color.parseColor("#FFEBEE")),
                critStart, critEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            sb.append("\n")
        }

        // ── Header & core fields (unchanged) ──────────────────
        appendHeader(sb, med.brandName ?: med.genericName)
        sb.append("\n")
        if (med.brandName != null) {
            val label = if (currentLanguage == "hi") "\u091C\u0947\u0928\u0947\u0930\u093F\u0915 \u0928\u093E\u092E: " else "Generic name: "
            appendBold(sb, label)
            sb.append(med.genericName).append("\n")
        }
        if (!med.drugClass.isNullOrBlank()) {
            val label = if (currentLanguage == "hi") "\u092A\u094D\u0930\u0915\u093E\u0930: " else "Type: "
            appendBold(sb, label)
            sb.append(med.drugClass).append("\n")
        }
        if (!med.legalSchedule.isNullOrBlank() && med.legalSchedule != "OTC") {
            sb.append("\n")
            val raw = template?.scheduleLabel
                ?: if (currentLanguage == "hi")
                    "Schedule {SCHEDULE}: \u092D\u093E\u0930\u0924\u0940\u092F \u0915\u093E\u0928\u0942\u0928 \u0915\u0947 \u0905\u0928\u0941\u0938\u093E\u0930 \u0935\u0948\u0927 \u092A\u0930\u094D\u091A\u093E \u0906\u0935\u0936\u094D\u092F\u0915 \u0939\u0948\u0964"
                else
                    "Schedule {SCHEDULE}: Prescription required under Indian law."
            val text  = raw.replace("{SCHEDULE}", med.legalSchedule)
            val color = if (med.legalSchedule.uppercase().contains("H1")) "#E65100" else "#1565C0"
            appendColored(sb, text, color)
            sb.append("\n")
        }
        if (!med.uses.isNullOrBlank()) {
            sb.append("\n")
            val label = if (currentLanguage == "hi") "\u0915\u093F\u0938 \u0915\u093E\u092E \u0906\u0924\u0940 \u0939\u0948:\n" else "What it is for:\n"
            appendBold(sb, label)
            sb.append(med.uses).append("\n")
        }
        if (!med.dose.isNullOrBlank()) {
            sb.append("\n")
            val raw  = template?.doseLabel
                ?: if (currentLanguage == "hi") "\u0938\u093E\u092E\u093E\u0928\u094D\u092F \u0916\u0941\u0930\u093E\u0915: {DOSE}" else "Usual adult dose: {DOSE}"
            val text = raw.replace("{DOSE}", med.dose)
            appendBold(sb, text)
            sb.append("\n")
        }
        val timing = if (currentLanguage == "hi" && !med.timingNoteHi.isNullOrBlank())
            med.timingNoteHi else med.timingNote
        if (!timing.isNullOrBlank()) {
            sb.append("\n")
            val label = if (currentLanguage == "hi") "\u0915\u092C \u0932\u0947\u0902: " else "When to take: "
            appendBold(sb, label)
            sb.append(timing).append("\n")
        }
        if (med.alcoholWarning) {
            sb.append("\n")
            val text = template?.alcoholLabel
                ?: if (currentLanguage == "hi") "\uD83D\uDEAB \u0936\u0930\u093E\u092C \u0915\u0947 \u0938\u093E\u0925 \u0928 \u0932\u0947\u0902\u0964" else "\uD83D\uDEAB Do not take with alcohol."
            appendColored(sb, if (text.startsWith("\uD83D\uDEAB")) text else "\uD83D\uDEAB $text", "#E65100")
            sb.append("\n")
        }
        if (!med.fdaPregnancyCat.isNullOrBlank()) {
            sb.append("\n")
            val cat     = med.fdaPregnancyCat.trim().uppercase()
            val meaning = pregnancyMeaning(cat, currentLanguage)
            val label   = if (currentLanguage == "hi") "\u0917\u0930\u094D\u092D\u093E\u0935\u0938\u094D\u0925\u093E \u0936\u094D\u0930\u0947\u0923\u0940: " else "Pregnancy Category: "
            val full    = "$label$cat \u2014 $meaning"
            when (cat) {
                "X"  -> appendColored(sb, full, "#B71C1C")
                "D"  -> appendColored(sb, full, "#E65100")
                else -> appendBold(sb, full)
            }
            sb.append("\n")
        }
        if (!med.sideEffects.isNullOrBlank()) {
            sb.append("\n")
            val label = if (currentLanguage == "hi") "\u0938\u093E\u092E\u093E\u0928\u094D\u092F \u0926\u0941\u0937\u094D\u092A\u094D\u0930\u092D\u093E\u0935:\n" else "Common side effects:\n"
            appendBold(sb, label)
            sb.append(med.sideEffects).append("\n")
        }
        if (med.highWarnings.isNotEmpty()) {
            sb.append("\n")
            val label = if (currentLanguage == "hi") "\u092E\u0939\u0924\u094D\u0935\u092A\u0942\u0930\u094D\u0923:\n" else "Important:\n"
            appendColored(sb, label, "#E65100")
            for (w in med.highWarnings) {
                sb.append("\u2022 ").append(pickLang(w.textEn, w.textHi)).append("\n")
            }
        }
        if (med.mediumWarnings.isNotEmpty()) {
            sb.append("\n")
            val label = if (currentLanguage == "hi") "\u0927\u094D\u092F\u093E\u0928 \u0926\u0947\u0902:\n" else "Note:\n"
            appendColored(sb, label, "#424242")
            for (w in med.mediumWarnings) {
                sb.append("\u2022 ").append(pickLang(w.textEn, w.textHi)).append("\n")
            }
        }
        if (!med.contraindications.isNullOrBlank()) {
            sb.append("\n")
            val label = if (currentLanguage == "hi") "\u0907\u0928\u094D\u0939\u0947\u0902 \u0928 \u0932\u0947\u0902 \u0905\u0917\u0930:\n" else "Do NOT take if:\n"
            appendBold(sb, label)
            sb.append(med.contraindications).append("\n")
        }

        // ── Alternative brands ← NEW (Part 3) ────────────────
        appendAlternativesBlock(sb, med)

        // ── Disclaimer (unchanged position) ──────────────────
        sb.append("\n")
        val disclaimer = if (currentLanguage == "hi")
            "\u0905\u0938\u094D\u0935\u0940\u0915\u0930\u0923: \u092F\u0939 \u091C\u093E\u0928\u0915\u093E\u0930\u0940 \u0915\u0947\u0935\u0932 \u0936\u0948\u0915\u094D\u0937\u093F\u0915 \u0909\u0926\u094D\u0926\u0947\u0936\u094D\u092F\u094B\u0902 \u0915\u0947 \u0932\u093F\u090F \u0939\u0948\u0964 " +
                    "\u0915\u093F\u0938\u0940 \u092D\u0940 \u0926\u0935\u093E \u0915\u093E \u0938\u0947\u0935\u0928 \u0915\u0930\u0928\u0947 \u0938\u0947 \u092A\u0939\u0932\u0947 \u0921\u0949\u0915\u094D\u091F\u0930 \u0938\u0947 \u092A\u0930\u093E\u092E\u0930\u094D\u0936 \u091C\u0930\u0942\u0930 \u0932\u0947\u0902\u0964 " +
                    "\u0938\u094D\u0935-\u091A\u093F\u0915\u093F\u0924\u094D\u0938\u093E \u091C\u094B\u0916\u093F\u092E \u092D\u0930\u093E \u0939\u094B \u0938\u0915\u0924\u093E \u0939\u0948\u0964"
        else
            "Disclaimer: This information is for educational purposes only. " +
                    "Always consult a doctor before taking any medicine. " +
                    "Self-medication can be dangerous."
        appendItalicGrey(sb, disclaimer)
    }

    // ─────────────────────────────────────────────────────────
    // TIER 2 BLOCK  ← NEW (Part 2)
    // Shows brand info only. No dose, no clinical fields.
    // ─────────────────────────────────────────────────────────

    private fun appendTier2Block(sb: SpannableStringBuilder, t2: Tier2Info) {
        appendTierBadge(sb, ResultTier.TIER_2)
        sb.append("\n\n")

        appendHeader(sb, t2.brandName)
        sb.append("\n")

        if (!t2.saltComposition.isNullOrBlank()) {
            val label = if (currentLanguage == "hi") "\u0938\u093E\u0932\u094D\u091F: " else "Salt composition: "
            appendBold(sb, label)
            sb.append(t2.saltComposition).append("\n")
        }
        if (!t2.manufacturer.isNullOrBlank()) {
            val label = if (currentLanguage == "hi") "\u0928\u093F\u0930\u094D\u092E\u093E\u0924\u093E: " else "Manufacturer: "
            appendBold(sb, label)
            sb.append(t2.manufacturer).append("\n")
        }
        if (!t2.mrp.isNullOrBlank()) {
            val label = if (currentLanguage == "hi") "MRP: \u20B9" else "MRP: \u20B9"
            appendBold(sb, label)
            sb.append(t2.mrp).append("\n")
        }
        if (!t2.subCategory.isNullOrBlank()) {
            val label = if (currentLanguage == "hi") "\u0936\u094D\u0930\u0947\u0923\u0940: " else "Category: "
            appendBold(sb, label)
            sb.append(t2.subCategory).append("\n")
        }

        // Tier 2 disclaimer
        sb.append("\n")
        val t2disclaimer = if (currentLanguage == "hi")
            "\u2139 \u092F\u0939 \u092C\u094D\u0930\u093E\u0902\u0921 \u0939\u092E\u093E\u0930\u0947 \u092A\u0939\u091A\u093E\u0928 \u0921\u0947\u091F\u093E\u092C\u0947\u0938 \u092E\u0947\u0902 \u0939\u0948 \u0932\u0947\u0915\u093F\u0928 \u0939\u092E\u093E\u0930\u0947 \u0938\u0924\u094D\u092F\u093E\u092A\u093F\u0924 \u0915\u094D\u0932\u093F\u0928\u093F\u0915\u0932 \u0921\u0947\u091F\u093E\u092C\u0947\u0938 \u092E\u0947\u0902 \u0928\u0939\u0940\u0902\u0964 " +
                    "\u0916\u0941\u0930\u093E\u0915, \u091F\u093E\u0907\u092E\u093F\u0902\u0917 \u0914\u0930 \u0926\u0941\u0937\u094D\u092A\u094D\u0930\u092D\u093E\u0935 \u0915\u0940 \u091C\u093E\u0928\u0915\u093E\u0930\u0940 \u092F\u0939\u093E\u0901 \u0928\u0939\u0940\u0902 \u0926\u0940 \u0917\u0908 \u0939\u0948\u0964 " +
                    "\u092A\u0942\u0930\u0940 \u091C\u093E\u0928\u0915\u093E\u0930\u0940 \u0915\u0947 \u0932\u093F\u090F \u0905\u092A\u0928\u0947 \u092B\u093E\u0930\u094D\u092E\u093E\u0938\u093F\u0938\u094D\u091F \u092F\u093E \u0921\u0949\u0915\u094D\u091F\u0930 \u0938\u0947 \u092A\u0930\u093E\u092E\u0930\u094D\u0936 \u0915\u0930\u0947\u0902\u0964"
        else
            "\u2139 This brand is in our identification database but not our verified clinical database. " +
                    "Dose, timing, and side-effect information are not shown here. " +
                    "Consult your pharmacist or doctor for full information."
        appendItalicGrey(sb, t2disclaimer)
    }

    // ─────────────────────────────────────────────────────────
    // ALTERNATIVES BLOCK  ← NEW (Part 3)
    // ─────────────────────────────────────────────────────────

    private fun appendAlternativesBlock(sb: SpannableStringBuilder, med: MedicineInfo) {
        // Safety gate: H1 check happens inside getAlternatives(),
        // but we double-check here so the UI never even queries
        val schedule = med.legalSchedule?.uppercase() ?: ""
        if (schedule.contains("H1")) return

        val alternatives = medicineLookup.getAlternatives(med)
        if (alternatives.isEmpty()) return

        sb.append("\n")

        val sectionLabel = if (currentLanguage == "hi")
            "\u0907\u0938\u0940 \u0926\u0935\u093E \u0914\u0930 \u0916\u0941\u0930\u093E\u0915 \u0935\u093E\u0932\u0947 \u0935\u0948\u0915\u0932\u094D\u092A\u093F\u0915 \u092C\u094D\u0930\u093E\u0902\u0921:"
        else
            "Alternative brands with the same medicine and dose:"
        appendBold(sb, sectionLabel)
        sb.append("\n")

        for (alt in alternatives) {
            sb.append("\u2022 ")
            appendBold(sb, alt.brandName)
            if (!alt.manufacturer.isNullOrBlank()) {
                sb.append("  ")
                appendItalicGrey(sb, "(${alt.manufacturer})")
            }
            sb.append("\n")
        }

        sb.append("\n")
        val altDisclaimer = if (currentLanguage == "hi")
            "\u26A0 \u092C\u0926\u0932\u0928\u0947 \u0938\u0947 \u092A\u0939\u0932\u0947 \u0939\u092E\u0947\u0936\u093E \u092B\u093E\u0930\u094D\u092E\u093E\u0938\u093F\u0938\u094D\u091F \u0938\u0947 \u092A\u0941\u0937\u094D\u091F\u093F \u0915\u0930\u0947\u0902\u0964"
        else
            "\u26A0 Always confirm with a pharmacist before substituting."
        appendItalicGrey(sb, altDisclaimer)
        sb.append("\n")
    }

    // ─────────────────────────────────────────────────────────
    // TIER BADGE RENDERER  ← NEW
    // ─────────────────────────────────────────────────────────

    private fun appendTierBadge(sb: SpannableStringBuilder, tier: ResultTier) {
        val (badgeText, bgColor, fgColor) = when (tier) {
            ResultTier.TIER_1A   -> Triple("  \u2713 VERIFIED  ",   "#E8F5E9", "#1B5E20")
            ResultTier.TIER_2    -> Triple("  \u26A0 IDENTIFIED  ", "#FFFDE7", "#E65100")
            ResultTier.NOT_FOUND -> Triple("  \u2717 NOT FOUND  ",  "#F5F5F5", "#757575")
        }
        val start = sb.length
        sb.append(badgeText)
        val end = sb.length
        sb.setSpan(
            BackgroundColorSpan(Color.parseColor(bgColor)),
            start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        sb.setSpan(
            ForegroundColorSpan(Color.parseColor(fgColor)),
            start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        sb.setSpan(
            StyleSpan(Typeface.BOLD),
            start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        sb.setSpan(
            RelativeSizeSpan(0.85f),
            start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    // ─────────────────────────────────────────────────────────
    // SPEAKABLE TEXT  (unchanged)
    // ─────────────────────────────────────────────────────────

    private fun buildSpeakableText(medicines: List<MedicineInfo>): String {
        if (medicines.isEmpty()) {
            return if (currentLanguage == "hi")
                "\u0907\u0938 \u091B\u0935\u093F \u092E\u0947\u0902 \u0915\u094B\u0908 \u091C\u094D\u091E\u093E\u0924 \u0926\u0935\u093E \u0928\u0939\u0940\u0902 \u092E\u093F\u0932\u0940\u0964 \u0915\u0943\u092A\u092F\u093E \u0905\u092A\u0928\u0947 \u092B\u093E\u0930\u094D\u092E\u093E\u0938\u093F\u0938\u094D\u091F \u0938\u0947 \u092A\u0930\u093E\u092E\u0930\u094D\u0936 \u0915\u0930\u0947\u0902\u0964"
            else
                "No known medicine was found in this image. Please consult your pharmacist."
        }
        val parts = mutableListOf<String>()
        for (med in medicines) {
            val template = med.category?.let { medicineLookup.getTemplate(currentLanguage, it) }
            val sb = StringBuilder()
            if (med.criticalWarnings.isNotEmpty()) {
                val w    = med.criticalWarnings.first()
                val text = pickLang(w.textEn, w.textHi)
                sb.append(
                    if (currentLanguage == "hi") "\u0905\u0924\u094D\u092F\u0902\u0924 \u092E\u0939\u0924\u094D\u0935\u092A\u0942\u0930\u094D\u0923: " else "Critical warning: "
                )
                sb.append(text).append(". ")
            }
            val intro = template?.introTemplate?.replace("{GENERIC}", med.genericName)
                ?: if (currentLanguage == "hi") "\u092F\u0939 ${med.genericName} \u0939\u0948\u0964"
                else "This is ${med.genericName}."
            sb.append(intro).append(" ")
            if (!med.dose.isNullOrBlank()) {
                val doseLine = template?.doseLabel?.replace("{DOSE}", med.dose)
                    ?: if (currentLanguage == "hi") "\u0938\u093E\u092E\u093E\u0928\u094D\u092F \u0916\u0941\u0930\u093E\u0915: ${med.dose}"
                    else "Usual adult dose: ${med.dose}"
                sb.append(doseLine).append(". ")
            }
            val timing = if (currentLanguage == "hi" && !med.timingNoteHi.isNullOrBlank())
                med.timingNoteHi else med.timingNote
            if (!timing.isNullOrBlank()) sb.append(timing).append(" ")
            sb.append(
                if (currentLanguage == "hi")
                    "\u0915\u0943\u092A\u092F\u093E \u0932\u0947\u0928\u0947 \u0938\u0947 \u092A\u0939\u0932\u0947 \u0921\u0949\u0915\u094D\u091F\u0930 \u0938\u0947 \u092A\u0930\u093E\u092E\u0930\u094D\u0936 \u0915\u0930\u0947\u0902\u0964"
                else
                    "Please consult a doctor before taking this medicine."
            )
            parts.add(sb.toString())
        }
        return parts.joinToString("\n\n")
    }

    // ─────────────────────────────────────────────────────────
    // HELPERS  (unchanged)
    // ─────────────────────────────────────────────────────────

    private fun pickLang(en: String, hi: String?): String =
        if (currentLanguage == "hi" && !hi.isNullOrBlank()) hi else en

    private fun pregnancyMeaning(cat: String, lang: String): String {
        return if (lang == "hi") {
            when (cat) {
                "A"  -> "\u0938\u093E\u092E\u093E\u0928\u094D\u092F \u0916\u0941\u0930\u093E\u0915 \u092A\u0930 \u0938\u0941\u0930\u0915\u094D\u0937\u093F\u0924"
                "B"  -> "\u0915\u094B\u0908 \u091C\u094D\u091E\u093E\u0924 \u0916\u0924\u0930\u093E \u0928\u0939\u0940\u0902"
                "C"  -> "\u0915\u0947\u0935\u0932 \u091C\u0930\u0942\u0930\u0924 \u092A\u0930 \u0921\u0949\u0915\u094D\u091F\u0930 \u0915\u0940 \u0938\u0932\u093E\u0939 \u0938\u0947"
                "D"  -> "\u092C\u091A\u094D\u091A\u0947 \u0915\u094B \u0916\u0924\u0930\u093E, \u0915\u0947\u0935\u0932 \u0906\u0935\u0936\u094D\u092F\u0915 \u0939\u094B\u0928\u0947 \u092A\u0930"
                "X"  -> "\u0917\u0930\u094D\u092D\u093E\u0935\u0938\u094D\u0925\u093E \u092E\u0947\u0902 \u092C\u093F\u0932\u094D\u0915\u0941\u0932 \u0928 \u0932\u0947\u0902"
                else -> "\u091C\u093E\u0928\u0915\u093E\u0930\u0940 \u0905\u0928\u0941\u092A\u0932\u092C\u094D\u0927"
            }
        } else {
            when (cat) {
                "A"  -> "Safe at normal doses"
                "B"  -> "No known risk"
                "C"  -> "Use only if benefit outweighs risk"
                "D"  -> "Known risk to baby, use only if essential"
                "X"  -> "Do NOT use in pregnancy"
                else -> "Information not available"
            }
        }
    }

    private fun appendHeader(sb: SpannableStringBuilder, text: String) {
        val start = sb.length
        sb.append(text)
        val end = sb.length
        sb.setSpan(RelativeSizeSpan(1.3f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(
            ForegroundColorSpan(Color.parseColor("#6200EE")),
            start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun appendBold(sb: SpannableStringBuilder, text: String) {
        val start = sb.length
        sb.append(text)
        val end = sb.length
        sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    private fun appendColored(sb: SpannableStringBuilder, text: String, colorHex: String) {
        val start = sb.length
        sb.append(text)
        val end = sb.length
        sb.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(
            ForegroundColorSpan(Color.parseColor(colorHex)),
            start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    private fun appendItalicGrey(sb: SpannableStringBuilder, text: String) {
        val start = sb.length
        sb.append(text)
        val end = sb.length
        sb.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(
            ForegroundColorSpan(Color.parseColor("#757575")),
            start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::tts.isInitialized) { tts.stop(); tts.shutdown() }
        gemmaReasoner?.close()
        cameraExecutor.shutdown()
    }
}
