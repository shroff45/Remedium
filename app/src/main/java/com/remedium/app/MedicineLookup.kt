package com.remedium.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlin.math.min

data class WarningItem(
    val textEn: String,
    val textHi: String?
)

data class MedicineInfo(
    val matchedTerm: String,
    val genericName: String,
    val brandName: String?,
    val strength: String?,
    val formulation: String?,
    val manufacturer: String?,
    val dataQuality: String,
    val drugClass: String?,
    val category: String?,
    val uses: String?,
    val dose: String?,
    val maxDose: String?,
    val timingNote: String?,
    val timingNoteHi: String?,
    val alcoholWarning: Boolean,
    val legalSchedule: String?,
    val fdaPregnancyCat: String?,
    val contraindications: String?,
    val sideEffects: String?,
    val criticalWarnings: List<WarningItem>,
    val highWarnings: List<WarningItem>,
    val mediumWarnings: List<WarningItem>
)

data class TemplateInfo(
    val categoryLabel: String?,
    val introTemplate: String?,
    val doseLabel: String?,
    val timingLabel: String?,
    val warningLabel: String?,
    val alcoholLabel: String?,
    val scheduleLabel: String?,
    val disclaimer: String?
)

data class SearchResult(
    val confirmed: List<MedicineInfo>,
    val ambiguous: List<List<MedicineInfo>>
)

class MedicineLookup(context: Context) {

    private val helper = DatabaseHelper(context)

    init {
        helper.ensureDatabase()
    }

    // Backward-compatible flat API
    fun searchMedicines(ocrText: String): List<MedicineInfo> {
        return searchWithAmbiguity(ocrText).confirmed
    }

    fun searchWithAmbiguity(ocrText: String): SearchResult {
        val tokens = extractTokens(ocrText)
        val confirmed = mutableListOf<MedicineInfo>()
        val ambiguousSets = mutableListOf<List<MedicineInfo>>()
        val seenGenerics = mutableSetOf<String>()
        val db = helper.openDb()

        try {
            for (token in tokens) {
                if (token.length < 3) continue

                val aliasMatches = lookupAlias(db, token)

                if (aliasMatches.isNotEmpty()) {
                    val ambiguousByText = aliasMatches.any { it.isAmbiguous }
                    val resolved = aliasMatches.mapNotNull { resolveMatch(db, it, token) }
                        .filter { it.genericName !in seenGenerics }

                    if (resolved.isEmpty()) continue

                    val byGeneric = resolved.groupBy { it.genericName }
                    val distinctGenerics = byGeneric.keys.size

                    if (distinctGenerics == 1) {
                        val best = resolved.first()
                        confirmed.add(best)
                        seenGenerics.add(best.genericName)
                    } else {
                        if (ambiguousByText) {
                            val representatives = byGeneric.values.map { it.first() }
                            ambiguousSets.add(representatives)
                            for (r in representatives) seenGenerics.add(r.genericName)
                        } else {
                            // Combo brand: e.g. Lepit-MK -> both levocetirizine AND montelukast
                            for (r in resolved.distinctBy { it.genericName }) {
                                if (r.genericName !in seenGenerics) {
                                    confirmed.add(r)
                                    seenGenerics.add(r.genericName)
                                }
                            }
                        }
                    }
                    continue
                }

                val fuzzyMatch = fuzzyAliasMatch(db, token)
                if (fuzzyMatch != null) {
                    if (fuzzyMatch.isAmbiguous) {
                        // TODO(disambiguation-ui): surface to user.
                        // Currently logged but not displayed.
                        Log.i(
                            "RemediumSearch",
                            "Skipping ambiguous fuzzy match for token='$token' " +
                                    "(alias type=${fuzzyMatch.aliasType})"
                        )
                    } else {
                        val info = resolveMatch(db, fuzzyMatch, token)
                        if (info != null && info.genericName !in seenGenerics) {
                            confirmed.add(info)
                            seenGenerics.add(info.genericName)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Remedium", "Search error", e)
        } finally {
            db.close()
        }

        return SearchResult(confirmed = confirmed, ambiguous = ambiguousSets)
    }

    // ============================================================
    // TOKEN EXTRACTION
    // ============================================================

    private fun extractTokens(ocrText: String): List<String> {
        val cleaned = ocrText
            .replace(Regex("[^A-Za-z0-9\\s\\n.,;:/()\\-]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

        val words = cleaned.split(Regex("[\\s,;:.\\n/\\\\()\\[\\]]+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val singleTokens = words.map { it.lowercase() }

        val pairTokens = mutableListOf<String>()
        for (i in 0 until words.size - 1) {
            pairTokens.add("${words[i]} ${words[i + 1]}".lowercase())
        }

        val hyphenVariants = singleTokens.flatMap { token ->
            if (token.contains("-")) listOf(token, token.replace("-", "")) else listOf(token)
        }

        return (pairTokens + hyphenVariants).distinct()
    }

    // ============================================================
    // ALIAS LOOKUP
    // ============================================================

    private data class AliasMatch(
        val targetTable: String,
        val targetId: Int,
        val isAmbiguous: Boolean,
        val aliasType: String
    )

    private fun lookupAlias(db: SQLiteDatabase, token: String): List<AliasMatch> {
        val results = mutableListOf<AliasMatch>()
        val cursor = db.rawQuery(
            "SELECT target_table, target_id, is_ambiguous, alias_type " +
                    "FROM search_aliases WHERE alias_text = ?",
            arrayOf(token)
        )
        while (cursor.moveToNext()) {
            results.add(
                AliasMatch(
                    targetTable = cursor.getString(0),
                    targetId = cursor.getInt(1),
                    isAmbiguous = cursor.getInt(2) == 1,
                    aliasType = cursor.getString(3)
                )
            )
        }
        cursor.close()
        return results
    }

    // ============================================================
    // FUZZY MATCH (length-aware)
    // ============================================================

    private fun fuzzyAliasMatch(db: SQLiteDatabase, token: String): AliasMatch? {
        val threshold = similarityThreshold(token.length)
        if (threshold >= 1.0) return null

        val minLen = (token.length - 3).coerceAtLeast(3)
        val maxLen = token.length + 3
        val cursor = db.rawQuery(
            "SELECT alias_text, target_table, target_id, is_ambiguous, alias_type " +
                    "FROM search_aliases WHERE LENGTH(alias_text) BETWEEN ? AND ?",
            arrayOf(minLen.toString(), maxLen.toString())
        )

        var bestSimilarity = 0.0
        var bestMatch: AliasMatch? = null
        while (cursor.moveToNext()) {
            val candidate = cursor.getString(0)
            val similarity = calculateSimilarity(token, candidate.lowercase())
            if (similarity >= threshold && similarity > bestSimilarity) {
                bestSimilarity = similarity
                bestMatch = AliasMatch(
                    targetTable = cursor.getString(1),
                    targetId = cursor.getInt(2),
                    isAmbiguous = cursor.getInt(3) == 1,
                    aliasType = cursor.getString(4)
                )
            }
        }
        cursor.close()
        return bestMatch
    }

    private fun similarityThreshold(length: Int): Double = when {
        length <= 4 -> 1.0
        length <= 7 -> 0.90
        length <= 12 -> 0.85
        else -> 0.80
    }

    private fun calculateSimilarity(s1: String, s2: String): Double {
        val longer = if (s1.length >= s2.length) s1 else s2
        val shorter = if (s1.length < s2.length) s1 else s2
        if (longer.isEmpty()) return 1.0
        val distance = editDistance(longer, shorter)
        return (longer.length - distance).toDouble() / longer.length
    }

    private fun editDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost)
            }
        }
        return dp[s1.length][s2.length]
    }

    // ============================================================
    // RESOLVE
    // ============================================================

    private fun resolveMatch(
        db: SQLiteDatabase,
        match: AliasMatch,
        matchedTerm: String
    ): MedicineInfo? {
        return when (match.targetTable) {
            "brand_products" -> resolveBrandProduct(db, match.targetId, matchedTerm)
            "drugs" -> resolveDrug(db, match.targetId, matchedTerm)
            else -> null
        }
    }

    private fun resolveBrandProduct(
        db: SQLiteDatabase,
        brandProductId: Int,
        matchedTerm: String
    ): MedicineInfo? {
        val cursor = db.rawQuery(
            "SELECT brand_name, generic_name, strength, formulation, manufacturer, data_quality " +
                    "FROM brand_products WHERE id = ?",
            arrayOf(brandProductId.toString())
        )
        if (!cursor.moveToFirst()) {
            cursor.close()
            return null
        }
        val brand = cursor.getString(0)
        val generic = cursor.getString(1)
        val strength = cursor.getString(2)
        val formulation = cursor.getString(3)
        val manufacturer = cursor.getString(4)
        val brandQuality = cursor.getString(5)
        cursor.close()

        return fetchDrugDetails(db, generic, brand, strength, formulation, manufacturer, brandQuality, matchedTerm)
    }

    private fun resolveDrug(
        db: SQLiteDatabase,
        drugId: Int,
        matchedTerm: String
    ): MedicineInfo? {
        val cursor = db.rawQuery(
            "SELECT generic_name FROM drugs WHERE id = ?",
            arrayOf(drugId.toString())
        )
        if (!cursor.moveToFirst()) {
            cursor.close()
            return null
        }
        val generic = cursor.getString(0)
        cursor.close()
        return fetchDrugDetails(db, generic, null, null, null, null, null, matchedTerm)
    }

    private fun fetchDrugDetails(
        db: SQLiteDatabase,
        genericName: String,
        brandName: String?,
        strength: String?,
        formulation: String?,
        manufacturer: String?,
        brandQuality: String?,
        matchedTerm: String
    ): MedicineInfo? {
        val drugCursor = db.rawQuery(
            "SELECT drug_class, category, common_uses_simple, standard_adult_dose, " +
                    "maximum_daily_dose, timing_note, timing_note_hi, alcohol_warning, " +
                    "legal_schedule, fda_pregnancy_cat, contraindications, common_side_effects, data_quality " +
                    "FROM drugs WHERE generic_name = ?",
            arrayOf(genericName)
        )

        if (!drugCursor.moveToFirst()) {
            drugCursor.close()
            return null
        }

        val drugClass = drugCursor.getString(0)
        val category = drugCursor.getString(1)
        val uses = drugCursor.getString(2)
        val dose = drugCursor.getString(3)
        val maxDose = drugCursor.getString(4)
        val timingNote = drugCursor.getString(5)
        val timingNoteHi = drugCursor.getString(6)
        val alcoholWarning = drugCursor.getInt(7) == 1
        val legalSchedule = drugCursor.getString(8)
        val fdaPregnancyCat = drugCursor.getString(9)
        val contraindications = drugCursor.getString(10)
        val sideEffects = drugCursor.getString(11)
        val drugQuality = drugCursor.getString(12) ?: "TIER_1A_VERIFIED"
        drugCursor.close()

        val effectiveQuality = combineQuality(drugQuality, brandQuality)

        val critical = mutableListOf<WarningItem>()
        val high = mutableListOf<WarningItem>()
        val medium = mutableListOf<WarningItem>()

        val warnCursor = db.rawQuery(
            "SELECT warning_text_simple, warning_text_hi, severity FROM warnings WHERE generic_name = ?",
            arrayOf(genericName)
        )
        while (warnCursor.moveToNext()) {
            val item = WarningItem(warnCursor.getString(0), warnCursor.getString(1))
            when ((warnCursor.getString(2) ?: "MEDIUM").uppercase()) {
                "CRITICAL" -> critical.add(item)
                "HIGH" -> high.add(item)
                else -> medium.add(item)
            }
        }
        warnCursor.close()

        return MedicineInfo(
            matchedTerm = matchedTerm,
            genericName = genericName,
            brandName = brandName,
            strength = strength,
            formulation = formulation,
            manufacturer = manufacturer,
            dataQuality = effectiveQuality,
            drugClass = drugClass,
            category = category,
            uses = uses,
            dose = dose,
            maxDose = maxDose,
            timingNote = timingNote,
            timingNoteHi = timingNoteHi,
            alcoholWarning = alcoholWarning,
            legalSchedule = legalSchedule,
            fdaPregnancyCat = fdaPregnancyCat,
            contraindications = contraindications,
            sideEffects = sideEffects,
            criticalWarnings = critical,
            highWarnings = high,
            mediumWarnings = medium
        )
    }

    private fun combineQuality(drugQuality: String, brandQuality: String?): String {
        if (brandQuality.isNullOrBlank()) return drugQuality
        return if (drugQuality.contains("TIER_2") || brandQuality.contains("TIER_2")) {
            "TIER_2_BULK"
        } else {
            drugQuality
        }
    }

    // ============================================================
    // TEMPLATE & SCHEDULE LOOKUPS
    // ============================================================

    fun getTemplate(langCode: String, category: String): TemplateInfo? {
        val db = helper.openDb()
        var result: TemplateInfo? = null
        try {
            val cursor = db.rawQuery(
                "SELECT category_label, intro_template, dose_label, timing_label, " +
                        "warning_label, alcohol_label, schedule_label, disclaimer " +
                        "FROM language_templates WHERE language_code = ? AND category = ?",
                arrayOf(langCode, category)
            )
            if (cursor.moveToFirst()) {
                result = TemplateInfo(
                    categoryLabel = cursor.getString(0),
                    introTemplate = cursor.getString(1),
                    doseLabel = cursor.getString(2),
                    timingLabel = cursor.getString(3),
                    warningLabel = cursor.getString(4),
                    alcoholLabel = cursor.getString(5),
                    scheduleLabel = cursor.getString(6),
                    disclaimer = cursor.getString(7)
                )
            }
            cursor.close()
        } finally {
            db.close()
        }
        return result
    }

    fun getScheduleInfo(schedule: String, langCode: String): String? {
        val db = helper.openDb()
        var result: String? = null
        try {
            val column = if (langCode == "hi") "description_hi" else "description_en"
            val cursor = db.rawQuery(
                "SELECT $column FROM schedule_info WHERE schedule_name = ?",
                arrayOf(schedule)
            )
            if (cursor.moveToFirst()) {
                result = cursor.getString(0)
            }
            cursor.close()
        } finally {
            db.close()
        }
        return result
    }
}