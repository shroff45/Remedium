package com.remedium.app

import android.Manifest
import android.app.AlertDialog
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
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var cameraPreview: PreviewView
    private lateinit var statusText: TextView
    private lateinit var captureButton: Button
    private lateinit var imageCapture: ImageCapture
    private lateinit var resultPanel: androidx.constraintlayout.widget.ConstraintLayout
    private lateinit var resultText: TextView
    private lateinit var scanAgainButton: Button
    private lateinit var languageToggleButton: Button
    private lateinit var speakButton: Button
    private lateinit var zoomButton: Button
    private lateinit var medicineLookup: MedicineLookup
    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var tts: TextToSpeech
    private var ttsReady = false

    private var currentLanguage = "en"
    private var lastOcrText: String = ""
    private var lastMedicines: List<MedicineInfo> = emptyList()

    private var camera: androidx.camera.core.Camera? = null
    private var currentZoom = 0f  // 0.0 = 1x, 0.3 = ~1.5x, 0.6 = ~2.5x

    private val CAMERA_PERMISSION_REQUEST = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        cameraPreview = findViewById(R.id.cameraPreview)
        statusText = findViewById(R.id.statusText)
        captureButton = findViewById(R.id.captureButton)
        resultPanel = findViewById(R.id.resultPanel)
        resultText = findViewById(R.id.resultText)
        scanAgainButton = findViewById(R.id.scanAgainButton)
        languageToggleButton = findViewById(R.id.languageToggleButton)
        speakButton = findViewById(R.id.speakButton)
        zoomButton = findViewById(R.id.zoomButton)

        medicineLookup = MedicineLookup(this)

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
                languageToggleButton.text = "हिं"
            }
            setTtsLanguage()
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

        zoomButton.setOnClickListener {
            currentZoom = when {
                currentZoom < 0.3f -> 0.3f
                currentZoom < 0.6f -> 0.6f
                else -> 0f
            }
            val label = when (currentZoom) {
                0f -> "1x"
                0.3f -> "1.5x"
                else -> "2.5x"
            }
            zoomButton.text = label
            camera?.cameraControl?.setLinearZoom(currentZoom)
        }

        if (hasCameraPermission()) {
            startCamera()
        } else {
            requestCameraPermission()
        }
    }

    private fun setTtsLanguage() {
        if (!::tts.isInitialized) return
        try {
            val locale = if (currentLanguage == "hi") Locale("hi", "IN") else Locale.ENGLISH
            val result = tts.setLanguage(locale)
            if (result == TextToSpeech.LANG_MISSING_DATA ||
                result == TextToSpeech.LANG_NOT_SUPPORTED
            ) {
                Log.w("Remedium", "TTS locale not supported: $locale")
            }
        } catch (e: Exception) {
            Log.e("Remedium", "TTS locale error", e)
        }
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

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
                grantResults[0] == PackageManager.PERMISSION_GRANTED
            ) {
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
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageCapture
                )

                // Tap-to-focus on preview
                cameraPreview.setOnTouchListener { _, event ->
                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                        val factory = cameraPreview.meteringPointFactory
                        val point = factory.createPoint(event.x, event.y)
                        val action = androidx.camera.core.FocusMeteringAction.Builder(
                            point, androidx.camera.core.FocusMeteringAction.FLAG_AF
                        ).setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS).build()
                        camera?.cameraControl?.startFocusAndMetering(action)
                        cameraPreview.performClick()
                    }
                    true
                }

                statusText.text = "Tap to focus → Use 1x/1.5x/2.5x for small strips → Scan."
            } catch (e: Exception) {
                statusText.text = "Camera failed: ${e.message}"
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        statusText.text = "Focusing..."
        captureButton.isEnabled = false

        // Step 1: Force autofocus at center of preview
        val factory = cameraPreview.meteringPointFactory
        val centerPoint = factory.createPoint(
            cameraPreview.width / 2f,
            cameraPreview.height / 2f
        )
        val focusAction = androidx.camera.core.FocusMeteringAction.Builder(
            centerPoint, androidx.camera.core.FocusMeteringAction.FLAG_AF
        ).disableAutoCancel().build()

        val focusFuture = camera?.cameraControl?.startFocusAndMetering(focusAction)

        // Step 2: After focus completes, take the picture
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
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)

        // Decode with downscale to save memory
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)

        // Larger target for small-strip detail (memory still safe with RGB_565)
        val maxSide = 3200
        var sample = 1
        val longest = maxOf(opts.outWidth, opts.outHeight)
        while (longest / sample > maxSide) sample *= 2

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.RGB_565
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)

        val matrix = Matrix()
        matrix.postRotate(image.imageInfo.rotationDegrees.toFloat())
        val rotated = Bitmap.createBitmap(
            bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
        )
        if (rotated != bitmap) bitmap.recycle()
        return rotated
    }

    private fun runOCR(bitmap: Bitmap) {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val extractedText = visionText.text
                Log.i("RemediumOCR", "RAW OCR TEXT:\n$extractedText\n--END--")
                runOnUiThread {
                    if (extractedText.isBlank()) {
                        statusText.text = "No text detected. Try better lighting or move closer."
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

    private fun showResultPanel(text: String) {
        val medicines = medicineLookup.searchMedicines(text)
        lastOcrText = text
        lastMedicines = medicines

        val display = buildDisplayText(text, medicines)
        resultText.text = display
        resultPanel.visibility = View.VISIBLE
        statusText.text = "Camera ready. Tap Scan."
        captureButton.isEnabled = true

        if (medicines.any { it.legalSchedule == "Schedule H1" }) {
            showScheduleH1Dialog()
        }
    }

    private fun rebuildCurrentResult() {
        val display = buildDisplayText(lastOcrText, lastMedicines)
        resultText.text = display
    }

    private fun hideResultPanel() {
        resultPanel.visibility = View.GONE
        statusText.text = "Camera ready. Tap Scan."
        if (::tts.isInitialized) tts.stop()
    }

    private fun showScheduleH1Dialog() {
        val title: String
        val message: String
        val positive: String
        val negative: String

        if (currentLanguage == "hi") {
            title = "पर्चा आवश्यक है"
            message = "यह Schedule H1 दवा है — एक उच्च-प्रभावशाली एंटीबायोटिक। " +
                    "भारतीय कानून के अनुसार केमिस्ट को दवा देने से पहले आपका विवरण दर्ज करना होगा। " +
                    "क्या यह दवा डॉक्टर ने आपको लिखी है?"
            positive = "हाँ, मेरे पास पर्चा है"
            negative = "नहीं"
        } else {
            title = "Prescription Required"
            message = "This is a Schedule H1 medicine — a high-potency antibiotic. " +
                    "Under Indian law, the chemist must record your details before dispensing. " +
                    "Have you been prescribed this by a doctor?"
            positive = "Yes, I have a prescription"
            negative = "No"
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positive) { d, _ -> d.dismiss() }
            .setNegativeButton(negative) { d, _ -> d.dismiss() }
            .setCancelable(false)
            .show()
    }

    // ============================================================
    // DISPLAY BUILDER
    // ============================================================

    private fun buildDisplayText(
        rawText: String,
        medicines: List<MedicineInfo>
    ): CharSequence {
        val sb = SpannableStringBuilder()

        if (medicines.isEmpty()) {
            if (currentLanguage == "hi") {
                appendBold(sb, "इस छवि में कोई ज्ञात दवा नहीं मिली\n\n")
                sb.append("हम किसी भी दवा को अपने डेटाबेस से मिला नहीं सके।\n\n")
                appendBold(sb, "निकाला गया टेक्स्ट:\n")
                sb.append(rawText)
                sb.append("\n\n")
                appendItalicGrey(
                    sb,
                    "अस्वीकरण: यह जानकारी केवल शैक्षिक उद्देश्यों के लिए है। " +
                            "किसी भी दवा का सेवन करने से पहले डॉक्टर से परामर्श जरूर लें। " +
                            "स्व-चिकित्सा जोखिम भरा हो सकता है।"
                )
            } else {
                appendBold(sb, "No known medicines found in this image\n\n")
                sb.append("We could not match any text to our medicine database.\n\n")
                appendBold(sb, "Extracted Text:\n")
                sb.append(rawText)
                sb.append("\n\n")
                appendItalicGrey(
                    sb,
                    "Disclaimer: This information is for educational purposes only. " +
                            "Always consult a doctor before taking any medicine. " +
                            "Self-medication can be dangerous."
                )
            }
            return sb
        }

        for ((index, med) in medicines.withIndex()) {
            if (index > 0) sb.append("\n\n────────────────\n\n")
            appendMedicineBlock(sb, med)
        }
        return sb
    }

    private fun appendMedicineBlock(sb: SpannableStringBuilder, med: MedicineInfo) {
        val template = med.category?.let { medicineLookup.getTemplate(currentLanguage, it) }

        if (med.criticalWarnings.isNotEmpty()) {
            val critStart = sb.length
            appendColored(
                sb,
                if (currentLanguage == "hi") "⚠ अत्यंत महत्वपूर्ण:\n" else "⚠ CRITICAL:\n",
                "#B71C1C"
            )
            for (w in med.criticalWarnings) {
                sb.append("• ")
                sb.append(pickLang(w.textEn, w.textHi))
                sb.append("\n")
            }
            val tail = if (currentLanguage == "hi")
                "संदेह हो तो न लें। तुरंत डॉक्टर से मिलें।\n"
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

        appendHeader(sb, med.brandName ?: med.genericName)
        sb.append("\n")
        if (med.brandName != null) {
            val label = if (currentLanguage == "hi") "जेनेरिक नाम: " else "Generic name: "
            appendBold(sb, label)
            sb.append(med.genericName)
            sb.append("\n")
        }

        if (!med.drugClass.isNullOrBlank()) {
            val label = if (currentLanguage == "hi") "प्रकार: " else "Type: "
            appendBold(sb, label)
            sb.append(med.drugClass)
            sb.append("\n")
        }

        if (!med.legalSchedule.isNullOrBlank() && med.legalSchedule != "OTC") {
            sb.append("\n")
            val raw = template?.scheduleLabel
                ?: if (currentLanguage == "hi")
                    "Schedule {SCHEDULE}: भारतीय कानून के अनुसार वैध पर्चा आवश्यक है।"
                else
                    "Schedule {SCHEDULE}: Prescription required under Indian law."
            val text = raw.replace("{SCHEDULE}", med.legalSchedule)
            val color = if (med.legalSchedule == "Schedule H1") "#E65100" else "#1565C0"
            appendColored(sb, text, color)
            sb.append("\n")
        }

        if (!med.uses.isNullOrBlank()) {
            sb.append("\n")
            val label = if (currentLanguage == "hi") "किस काम आती है:\n" else "What it is for:\n"
            appendBold(sb, label)
            sb.append(med.uses)
            sb.append("\n")
        }

        if (!med.dose.isNullOrBlank()) {
            sb.append("\n")
            val raw = template?.doseLabel
                ?: if (currentLanguage == "hi") "सामान्य खुराक: {DOSE}" else "Usual adult dose: {DOSE}"
            val text = raw.replace("{DOSE}", med.dose)
            appendBold(sb, text)
            sb.append("\n")
        }

        val timing = if (currentLanguage == "hi" && !med.timingNoteHi.isNullOrBlank())
            med.timingNoteHi else med.timingNote
        if (!timing.isNullOrBlank()) {
            sb.append("\n")
            val label = if (currentLanguage == "hi") "कब लें: " else "When to take: "
            appendBold(sb, label)
            sb.append(timing)
            sb.append("\n")
        }

        if (med.alcoholWarning) {
            sb.append("\n")
            val text = template?.alcoholLabel
                ?: if (currentLanguage == "hi")
                    "🚫 शराब के साथ न लें।"
                else
                    "🚫 Do not take with alcohol."
            val finalText = if (text.startsWith("🚫")) text else "🚫 $text"
            appendColored(sb, finalText, "#E65100")
            sb.append("\n")
        }

        if (!med.fdaPregnancyCat.isNullOrBlank()) {
            sb.append("\n")
            val cat = med.fdaPregnancyCat.trim().uppercase()
            val meaning = pregnancyMeaning(cat, currentLanguage)
            val label = if (currentLanguage == "hi") "गर्भावस्था श्रेणी: " else "Pregnancy Category: "
            val full = "$label$cat — $meaning"
            when (cat) {
                "X" -> appendColored(sb, full, "#B71C1C")
                "D" -> appendColored(sb, full, "#E65100")
                else -> appendBold(sb, full)
            }
            sb.append("\n")
        }

        if (!med.sideEffects.isNullOrBlank()) {
            sb.append("\n")
            val label = if (currentLanguage == "hi") "सामान्य दुष्प्रभाव:\n" else "Common side effects:\n"
            appendBold(sb, label)
            sb.append(med.sideEffects)
            sb.append("\n")
        }

        if (med.highWarnings.isNotEmpty()) {
            sb.append("\n")
            val label = if (currentLanguage == "hi") "महत्वपूर्ण:\n" else "Important:\n"
            appendColored(sb, label, "#E65100")
            for (w in med.highWarnings) {
                sb.append("• ")
                sb.append(pickLang(w.textEn, w.textHi))
                sb.append("\n")
            }
        }

        if (med.mediumWarnings.isNotEmpty()) {
            sb.append("\n")
            val label = if (currentLanguage == "hi") "ध्यान दें:\n" else "Note:\n"
            appendColored(sb, label, "#424242")
            for (w in med.mediumWarnings) {
                sb.append("• ")
                sb.append(pickLang(w.textEn, w.textHi))
                sb.append("\n")
            }
        }

        if (!med.contraindications.isNullOrBlank()) {
            sb.append("\n")
            val label = if (currentLanguage == "hi") "इन्हें न लें अगर:\n" else "Do NOT take if:\n"
            appendBold(sb, label)
            sb.append(med.contraindications)
            sb.append("\n")
        }

        sb.append("\n")
        val disclaimer = if (currentLanguage == "hi")
            "अस्वीकरण: यह जानकारी केवल शैक्षिक उद्देश्यों के लिए है। " +
                    "किसी भी दवा का सेवन करने से पहले डॉक्टर से परामर्श जरूर लें। " +
                    "स्व-चिकित्सा जोखिम भरा हो सकता है।"
        else
            "Disclaimer: This information is for educational purposes only. " +
                    "Always consult a doctor before taking any medicine. " +
                    "Self-medication can be dangerous."
        appendItalicGrey(sb, disclaimer)
    }

    private fun pickLang(en: String, hi: String?): String {
        return if (currentLanguage == "hi" && !hi.isNullOrBlank()) hi else en
    }

    private fun pregnancyMeaning(cat: String, lang: String): String {
        return if (lang == "hi") {
            when (cat) {
                "A" -> "सामान्य खुराक पर सुरक्षित"
                "B" -> "कोई ज्ञात खतरा नहीं"
                "C" -> "केवल जरूरत पर डॉक्टर की सलाह से"
                "D" -> "बच्चे को खतरा, केवल आवश्यक होने पर"
                "X" -> "गर्भावस्था में बिल्कुल न लें"
                else -> "जानकारी अनुपलब्ध"
            }
        } else {
            when (cat) {
                "A" -> "Safe at normal doses"
                "B" -> "No known risk"
                "C" -> "Use only if benefit outweighs risk"
                "D" -> "Known risk to baby, use only if essential"
                "X" -> "Do NOT use in pregnancy"
                else -> "Information not available"
            }
        }
    }

    // ============================================================
    // SPEAKABLE TEXT
    // ============================================================

    private fun buildSpeakableText(medicines: List<MedicineInfo>): String {
        if (medicines.isEmpty()) {
            return if (currentLanguage == "hi")
                "इस छवि में कोई ज्ञात दवा नहीं मिली। कृपया अपने फार्मासिस्ट से परामर्श करें।"
            else
                "No known medicine was found in this image. Please consult your pharmacist."
        }

        val parts = mutableListOf<String>()
        for (med in medicines) {
            val template = med.category?.let { medicineLookup.getTemplate(currentLanguage, it) }
            val sb = StringBuilder()

            if (med.criticalWarnings.isNotEmpty()) {
                val w = med.criticalWarnings.first()
                val text = pickLang(w.textEn, w.textHi)
                sb.append(
                    if (currentLanguage == "hi") "अत्यंत महत्वपूर्ण: " else "Critical warning: "
                )
                sb.append(text).append(". ")
            }

            val intro = template?.introTemplate?.replace("{GENERIC}", med.genericName)
                ?: if (currentLanguage == "hi")
                    "यह ${med.genericName} है।"
                else
                    "This is ${med.genericName}."
            sb.append(intro).append(" ")

            if (!med.dose.isNullOrBlank()) {
                val doseLine = template?.doseLabel?.replace("{DOSE}", med.dose)
                    ?: if (currentLanguage == "hi") "सामान्य खुराक: ${med.dose}"
                    else "Usual adult dose: ${med.dose}"
                sb.append(doseLine).append(". ")
            }

            val timing = if (currentLanguage == "hi" && !med.timingNoteHi.isNullOrBlank())
                med.timingNoteHi else med.timingNote
            if (!timing.isNullOrBlank()) {
                sb.append(timing).append(" ")
            }

            sb.append(
                if (currentLanguage == "hi")
                    "कृपया लेने से पहले डॉक्टर से परामर्श करें।"
                else
                    "Please consult a doctor before taking this medicine."
            )

            parts.add(sb.toString())
        }
        return parts.joinToString("\n\n")
    }

    // ============================================================
    // SPAN HELPERS
    // ============================================================

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
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        cameraExecutor.shutdown()
    }
}