package com.remedium.app

import android.util.Log
import java.util.regex.Pattern

/**
 * Assembles context for AI (Gemma4) queries and enforces
 * the safety contract: AI suggests → Kotlin validates → DB wins if conflict.
 *
 * Called from MainActivity after Gemma4 returns a paediatric dose suggestion.
 */
object ContextAssembler {

    private const val TAG = "RemediumContext"

    /**
     * Extracts the first numeric mg value from a string.
     * Handles patterns like "250mg", "250 mg", "250 mg/kg", "dose is 250 mg".
     * Returns null if no numeric mg value is found.
     */
    fun extractMgValue(text: String): Double? {
        val matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*mg").matcher(text.lowercase())
        return if (matcher.find()) {
            matcher.group(1)?.toDoubleOrNull()
        } else {
            null
        }
    }

    /**
     * Safety gate for paediatric dose suggestions.
     *
     * After Gemma4 returns a dose recommendation:
     * 1. Extract the numeric mg value from the AI output
     * 2. Compare against the DB's maximum daily dose (capped at singleDoseCap = ceiling / 4)
     * 3. If AI suggests more than the cap, override with the safe maximum
     * 4. If either value can't be parsed, pass through the original output unchanged
     *
     * @param gemmaOutput The raw text output from Gemma4 (may contain dose suggestion)
     * @param maxDailyDose The maximum daily dose string from the DB (e.g. "4000mg")
     * @return Either the capped safe dose message or the original Gemma output
     */
    fun validatePaedDose(gemmaOutput: String, maxDailyDose: String?): String {
        val suggested = extractMgValue(gemmaOutput) ?: return gemmaOutput
        val ceiling = maxDailyDose?.let { extractMgValue(it) } ?: return gemmaOutput
        val singleDoseCap = ceiling / 4 // conservative: 4 doses/day

        return if (suggested > singleDoseCap) {
            val message = "Give ${singleDoseCap.toInt()}mg (maximum safe dose). ⚠ AI suggested higher — DB cap applied."
            Log.w(TAG, "Paed dose capped: AI suggested ${suggested}mg, DB ceiling=${ceiling}mg, cap=${singleDoseCap.toInt()}mg")
            message
        } else {
            gemmaOutput
        }
    }

    /**
     * Builds a grounded prompt for Gemma 4 using verified medicine data from DB.
     * Includes patient context and safety rules.
     *
     * @param medicines List of MedicineInfo from Tier 1A (verified) only
     * @param userQuestion The question user is asking
     * @param isPregnant Whether patient is pregnant
     * @param ageGroup Patient age group: "adult", "child", "elderly"
     * @param weightKg Patient weight in kg (optional)
     * @return Formatted prompt string with DB-verified data
     */
    fun buildGroundedPrompt(
        medicines: List<MedicineInfo>,
        userQuestion: String,
        isPregnant: Boolean = false,
        ageGroup: String = "adult",
        weightKg: Int? = null
    ): String {
        val medContext = medicines.joinToString("\n\n") { med ->
            buildString {
                appendLine("MEDICINE: ${med.genericName}")
                appendLine("USES: ${med.uses ?: "N/A"}")
                appendLine("DOSE: ${med.dose ?: "N/A"}")
                appendLine("MAX_DAILY: ${med.maxDose ?: "N/A"}")
                appendLine("SCHEDULE: ${med.legalSchedule ?: "N/A"}")
                appendLine("PREGNANCY_CAT: ${med.fdaPregnancyCat ?: "N/A"}")
                appendLine("ALCOHOL_WARNING: ${if (med.alcoholWarning) "dangerous" else "none"}")
                if (med.criticalWarnings.isNotEmpty() || med.highWarnings.isNotEmpty() || med.mediumWarnings.isNotEmpty()) {
                    val allWarnings = med.criticalWarnings.map { it.textEn } +
                            med.highWarnings.map { it.textEn } +
                            med.mediumWarnings.map { it.textEn }
                    appendLine("WARNINGS: ${allWarnings.joinToString("; ")}")
                }
                if (!med.contraindications.isNullOrBlank()) {
                    appendLine("CONTRAINDICATIONS: ${med.contraindications}")
                }
            }
        }

        val patientContext = buildString {
            appendLine("PATIENT_AGE_GROUP: $ageGroup")
            if (weightKg != null) appendLine("PATIENT_WEIGHT: ${weightKg}kg")
            if (isPregnant) appendLine("PREGNANT: yes")
        }

        return """
You are Remedium's medicine assistant. Answer questions using ONLY
the verified information provided below.

RULES:
- If the answer is not in the provided information, say EXACTLY:
  "I don't have verified information about that. Please ask your pharmacist or doctor."
- NEVER invent medical facts
- NEVER recommend changing doses
- NEVER diagnose conditions
- NEVER answer questions about medicines not listed below
- Answer in the same language as the QUESTION field

VERIFIED MEDICINE DATA:
$medContext

PATIENT CONTEXT:
$patientContext

QUESTION: $userQuestion
""".trimIndent()
    }
}
