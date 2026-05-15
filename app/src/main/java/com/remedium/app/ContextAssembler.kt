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

    /**
     * Builds a strict grounding prompt for drug interaction analysis.
     * Forces Gemma to output in parseable format for UI extraction.
     *
     * @param med1 First medicine
     * @param med2 Second medicine
     * @return Formatted prompt with exact output format instructions
     */
    fun buildInteractionPrompt(med1: MedicineInfo, med2: MedicineInfo): String {
        val warnings1 = (med1.criticalWarnings.map { it.textEn } +
                med1.highWarnings.map { it.textEn } +
                med1.mediumWarnings.map { it.textEn }).joinToString("; ")

        val warnings2 = (med2.criticalWarnings.map { it.textEn } +
                med2.highWarnings.map { it.textEn } +
                med2.mediumWarnings.map { it.textEn }).joinToString("; ")

        return """
You are a clinical AI pharmacist. Check for interactions between these two medicines using ONLY the provided data. Do not invent information.

MEDICINE 1: ${med1.genericName}
Uses: ${med1.uses ?: "N/A"}
Dose: ${med1.dose ?: "N/A"}
Warnings: ${if (warnings1.isNotBlank()) warnings1 else "None"}
Contraindications: ${med1.contraindications ?: "None"}
Side Effects: ${med1.sideEffects ?: "N/A"}

MEDICINE 2: ${med2.genericName}
Uses: ${med2.uses ?: "N/A"}
Dose: ${med2.dose ?: "N/A"}
Warnings: ${if (warnings2.isNotBlank()) warnings2 else "None"}
Contraindications: ${med2.contraindications ?: "None"}
Side Effects: ${med2.sideEffects ?: "N/A"}

Evaluate safety. Respond STRICTLY in this exact format, with no extra conversational text:
VERDICT: [SAFE TO COMBINE or NO or CONSULT DOCTOR]
REASON: [1-2 short sentences using ONLY the context above]
WATCH FOR: [Overlapping side effects or specific risks]
        """.trimIndent()
    }
}
