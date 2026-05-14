package com.remedium.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import kotlin.math.min

// ─────────────────────────────────────────────────────────────
// DATA CLASSES
// ─────────────────────────────────────────────────────────────

data class WarningItem(
    val textEn: String,
    val textHi: String?
)

// ← NEW: which tier produced this result
enum class ResultTier {
    TIER_1A,    // verified — full clinical data available
    TIER_2,     // identified — brand info only, no clinical data
    NOT_FOUND   // no match
}

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
    val mediumWarnings: List<WarningItem>,
    val tier: ResultTier = ResultTier.TIER_1A   // ← NEW: default keeps old callers safe
)

// ← NEW: Tier 2 result — brand info only, no clinical fields
data class Tier2Info(
    val matchedTerm: String,
    val brandName: String,
    val saltComposition: String?,
    val manufacturer: String?,
    val mrp: String?,
    val subCategory: String?,
    val tier: ResultTier = ResultTier.TIER_2
)

// ← NEW: alternatives for the "same medicine, same dose" feature
data class AlternativeBrand(
    val brandName: String,
    val manufacturer: String?,
    val strength: String?,
    val formulation: String?
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

// ← CHANGED: SearchResult now carries Tier 2 results separately
data class SearchResult(
    val confirmed: List<MedicineInfo>,
    val ambiguous: List<List<MedicineInfo>>,
    val tier2Results: List<Tier2Info> = emptyList()   // ← NEW
)

// ─────────────────────────────────────────────────────────────
// MAIN LOOKUP CLASS
// ─────────────────────────────────────────────────────────────

class MedicineLookup(context: Context) {

    private val helper = DatabaseHelper(context)

    init {
        helper.ensureDatabase()
    }

    // ── Backward-compatible flat API ──────────────────────────
    fun searchMedicines(ocrText: String): List<MedicineInfo> {
        return searchWithAmbiguity(ocrText).confirmed
    }

    fun searchWithAmbiguity(ocrText: String): SearchResult {
        val tokens = extractTokens(ocrText)
        val confirmed = mutableListOf<MedicineInfo>()
        val ambiguousSets = mutableListOf<List<MedicineInfo>>()
        val tier2Results = mutableListOf<Tier2Info>()   // ← NEW
        val seenGenerics = mutableSetOf<String>()
        val seenTier2Brands = mutableSetOf<String>()    // ← NEW: dedup Tier 2 hits
        val db = helper.openDb()

        try {
            for (token in tokens) {
                if (token.length < 3) continue

                // ── PRIMARY PATH: Tier 1A ─────────────────────
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
                            for (r in resolved.distinctBy { it.genericName }) {
                                if (r.genericName !in seenGenerics) {
                                    confirmed.add(r)
                                    seenGenerics.add(r.genericName)
                                }
                            }
                        }
                    }
                    continue   // Tier 1A hit — skip Tier 2 for this token
                }

                // ── FUZZY (still Tier 1A) ─────────────────────
                val fuzzyMatch = fuzzyAliasMatch(db, token)
                if (fuzzyMatch != null) {
                    if (fuzzyMatch.isAmbiguous) {
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
                    continue   // fuzzy hit found — skip Tier 2 for this token
                }

                // ── FALLBACK PATH: Tier 2 ← NEW ──────────────
                // Only reached if Tier 1A exact + fuzzy both missed.
                val t2 = lookupTier2(db, token)
                if (t2 != null && t2.brandName !in seenTier2Brands) {
                    tier2Results.add(t2)
                    seenTier2Brands.add(t2.brandName)
                }
            }
        } catch (e: Exception) {
            Log.e("Remedium", "Search error", e)
        } finally {
            db.close()
        }

        return SearchResult(
            confirmed = confirmed,
            ambiguous = ambiguousSets,
            tier2Results = tier2Results   // ← NEW
        )
    }

    // ─────────────────────────────────────────────────────────
    // TIER 2 LOOKUP  ← NEW SECTION
    // ─────────────────────────────────────────────────────────

    private fun lookupTier2(db: SQLiteDatabase, token: String): Tier2Info? {
        // Step 1: find brand_id via tier2_search_aliases
        // Priority: FULL_NORMALIZED > BRAND_STRIPPED > BRAND_FIRST
        val aliasCursor = db.rawQuery(
            """
            SELECT tier2_brand_id
            FROM tier2_search_aliases
            WHERE alias_text = ?
            ORDER BY CASE alias_type
                WHEN 'FULL_NORMALIZED' THEN 1
                WHEN 'BRAND_STRIPPED'  THEN 2
                WHEN 'BRAND_FIRST'     THEN 3
                ELSE 99
            END
            LIMIT 1
            """.trimIndent(),
            arrayOf(token)
        )
        if (!aliasCursor.moveToFirst()) {
            aliasCursor.close()
            return null
        }
        val brandId = aliasCursor.getInt(0)
        aliasCursor.close()

        // Step 2: fetch brand row from tier2_brand_products
        val brandCursor = db.rawQuery(
            """
            SELECT brand_name, salt_composition, manufacturer, price_inr, sub_category
            FROM tier2_brand_products
            WHERE id = ?
            """.trimIndent(),
            arrayOf(brandId.toString())
        )
        if (!brandCursor.moveToFirst()) {
            brandCursor.close()
            return null
        }

        val price = brandCursor.getString(3)
        val priceStr = if (price != null && price.isNotBlank()) {
            try {
                val p = price.toDouble()
                if (p > 0) String.format("%.2f", p) else null
            } catch (e: NumberFormatException) { null }
        } else null

        val result = Tier2Info(
            matchedTerm = token,
            brandName   = brandCursor.getString(0) ?: "Unknown Brand",
            saltComposition = brandCursor.getString(1),
            manufacturer    = brandCursor.getString(2),
            mrp             = priceStr,
            subCategory     = brandCursor.getString(4)
        )
        brandCursor.close()
        return result
    }

    // ─────────────────────────────────────────────────────────
    // ALTERNATIVES QUERY  ← NEW SECTION (Part 3)
    // ─────────────────────────────────────────────────────────

    /**
     * Returns up to 10 alternative brands from Tier 1A brand_products
     * that share the same generic name, strength, and formulation.
     *
     * SAFETY RULES enforced here (not in the UI layer):
     *   - Schedule H1 drugs -> empty list, no alternatives shown
     *   - Only Tier 1A brand_products is queried (Tier 2 generic
     *     mapping is not verified enough to suggest substitutes)
     *   - Matched brand itself is excluded
     *   - All three of generic / strength / formulation must match exactly
     */
    fun getAlternatives(med: MedicineInfo): List<AlternativeBrand> {
        // SAFETY GATE 1: Schedule H1 — never show alternatives
        if (med.legalSchedule?.uppercase() == "SCHEDULE H1" ||
            med.legalSchedule?.uppercase() == "H1") {
            return emptyList()
        }

        // SAFETY GATE 2: must have all three match keys
        val generic = med.genericName.ifBlank { return emptyList() }
        val strength = med.strength ?: return emptyList()
        val formulation = med.formulation ?: return emptyList()
        val excludeBrand = med.brandName ?: return emptyList()

        // SAFETY GATE 3: only Tier 1A results get alternatives
        if (med.tier != ResultTier.TIER_1A) return emptyList()

        val db = helper.openDb()
        val results = mutableListOf<AlternativeBrand>()
        try {
            val cursor = db.rawQuery(
                """
                SELECT brand_name, manufacturer, strength, formulation
                FROM brand_products
                WHERE generic_name = ?
                  AND strength = ?
                  AND formulation = ?
                  AND brand_name != ?
                ORDER BY brand_name
                LIMIT 10
                """.trimIndent(),
                arrayOf(generic, strength, formulation, excludeBrand)
            )
            while (cursor.moveToNext()) {
                results.add(
                    AlternativeBrand(
                        brandName    = cursor.getString(0),
                        manufacturer = cursor.getString(1),
                        strength     = cursor.getString(2),
                        formulation  = cursor.getString(3)
                    )
                )
            }
            cursor.close()
        } catch (e: Exception) {
            Log.e("Remedium", "getAlternatives error: ${e.message}")
        } finally {
            db.close()
        }
        return results
    }

    // ─────────────────────────────────────────────────────────
    // TOKEN EXTRACTION  (unchanged)
    // ─────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────
    // ALIAS LOOKUP  (unchanged)
    // ─────────────────────────────────────────────────────────

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
                    targetId    = cursor.getInt(1),
                    isAmbiguous = cursor.getInt(2) == 1,
                    aliasType   = cursor.getString(3)
                )
            )
        }
        cursor.close()
        return results
    }

    // ─────────────────────────────────────────────────────────
    // FUZZY MATCH  (unchanged)
    // ─────────────────────────────────────────────────────────

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
                    targetId    = cursor.getInt(2),
                    isAmbiguous = cursor.getInt(3) == 1,
                    aliasType   = cursor.getString(4)
                )
            }
        }
        cursor.close()
        return bestMatch
    }

    private fun similarityThreshold(length: Int): Double = when {
        length <= 4  -> 1.0
        length <= 7  -> 0.85
        length <= 12 -> 0.80
        else         -> 0.75
    }

    private fun calculateSimilarity(s1: String, s2: String): Double {
        val longer  = if (s1.length >= s2.length) s1 else s2
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
                dp[i][j] = min(
                    min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    // ─────────────────────────────────────────────────────────
    // RESOLVE  (tier field added to MedicineInfo construction)
    // ─────────────────────────────────────────────────────────

    private fun resolveMatch(
        db: SQLiteDatabase,
        match: AliasMatch,
        matchedTerm: String
    ): MedicineInfo? {
        return when (match.targetTable) {
            "brand_products" -> resolveBrandProduct(db, match.targetId, matchedTerm)
            "drugs"          -> resolveDrug(db, match.targetId, matchedTerm)
            else             -> null
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
        if (!cursor.moveToFirst()) { cursor.close(); return null }
        val brand        = cursor.getString(0)
        val generic      = cursor.getString(1)
        val strength     = cursor.getString(2)
        val formulation  = cursor.getString(3)
        val manufacturer = cursor.getString(4)
        val brandQuality = cursor.getString(5)
        cursor.close()

        return fetchDrugDetails(
            db, generic, brand, strength, formulation,
            manufacturer, brandQuality, matchedTerm
        )
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
        if (!cursor.moveToFirst()) { cursor.close(); return null }
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
                    "legal_schedule, fda_pregnancy_cat, contraindications, " +
                    "common_side_effects, data_quality " +
                    "FROM drugs WHERE generic_name = ?",
            arrayOf(genericName)
        )
        if (!drugCursor.moveToFirst()) { drugCursor.close(); return null }

        val drugClass    = drugCursor.getString(0)
        val category     = drugCursor.getString(1)
        val uses         = drugCursor.getString(2)
        val dose         = drugCursor.getString(3)
        val maxDose      = drugCursor.getString(4)
        val timingNote   = drugCursor.getString(5)
        val timingNoteHi = drugCursor.getString(6)
        val alcoholWarning = drugCursor.getInt(7) == 1
        val legalSchedule  = drugCursor.getString(8)
        val fdaPregnancyCat = drugCursor.getString(9)
        val contraindications = drugCursor.getString(10)
        val sideEffects  = drugCursor.getString(11)
        val drugQuality  = drugCursor.getString(12) ?: "TIER_1A_VERIFIED"
        drugCursor.close()

        val effectiveQuality = combineQuality(drugQuality, brandQuality)

        val critical = mutableListOf<WarningItem>()
        val high     = mutableListOf<WarningItem>()
        val medium   = mutableListOf<WarningItem>()

        val warnCursor = db.rawQuery(
            "SELECT warning_text_simple, warning_text_hi, severity " +
                    "FROM warnings WHERE generic_name = ?",
            arrayOf(genericName)
        )
        while (warnCursor.moveToNext()) {
            val item = WarningItem(warnCursor.getString(0), warnCursor.getString(1))
            when ((warnCursor.getString(2) ?: "MEDIUM").uppercase()) {
                "CRITICAL" -> critical.add(item)
                "HIGH"     -> high.add(item)
                else       -> medium.add(item)
            }
        }
        warnCursor.close()

        return MedicineInfo(
            matchedTerm   = matchedTerm,
            genericName   = genericName,
            brandName     = brandName,
            strength      = strength,
            formulation   = formulation,
            manufacturer  = manufacturer,
            dataQuality   = effectiveQuality,
            drugClass     = drugClass,
            category      = category,
            uses          = uses,
            dose          = dose,
            maxDose       = maxDose,
            timingNote    = timingNote,
            timingNoteHi  = timingNoteHi,
            alcoholWarning = alcoholWarning,
            legalSchedule  = legalSchedule,
            fdaPregnancyCat = fdaPregnancyCat,
            contraindications = contraindications,
            sideEffects   = sideEffects,
            criticalWarnings = critical,
            highWarnings  = high,
            mediumWarnings = medium,
            tier          = ResultTier.TIER_1A   // ← NEW: all paths here are Tier 1A
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

    // ─────────────────────────────────────────────────────────
    // TEMPLATE & SCHEDULE LOOKUPS  (unchanged)
    // ─────────────────────────────────────────────────────────

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
                    categoryLabel  = cursor.getString(0),
                    introTemplate  = cursor.getString(1),
                    doseLabel      = cursor.getString(2),
                    timingLabel    = cursor.getString(3),
                    warningLabel   = cursor.getString(4),
                    alcoholLabel   = cursor.getString(5),
                    scheduleLabel  = cursor.getString(6),
                    disclaimer     = cursor.getString(7)
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
            if (cursor.moveToFirst()) result = cursor.getString(0)
            cursor.close()
        } finally {
            db.close()
        }
        return result
    }
}
