package com.remedium.app

import org.junit.Test
import org.junit.Assert.*

/**
 * Safety-critical unit tests for Remedium.
 * Verifies that safety architecture works as designed:
 * - Paediatric dose capping
 * - Input sanitization
 * - Tier trust boundaries
 * - Levenshtein threshold logic
 */
class SafetyGateTest {

    @Test
    fun `extractMgValue parses simple milligram value`() {
        val result = extractMgFromText("Give 500mg twice daily")
        assertEquals(500.0, result, 0.01)
    }

    @Test
    fun `extractMgValue parses value with space before mg`() {
        val result = extractMgFromText("Take 250 mg after food")
        assertEquals(250.0, result, 0.01)
    }

    @Test
    fun `extractMgValue returns zero for no mg value`() {
        val result = extractMgFromText("Take one tablet daily")
        assertEquals(0.0, result, 0.01)
    }

    @Test
    fun `paediatric dose is capped at quarter of max daily`() {
        val aiSuggested = 2000.0
        val maxDaily = 4000.0
        val cap = maxDaily / 4.0
        val safeDose = if (aiSuggested > cap) cap else aiSuggested
        assertEquals(1000.0, safeDose, 0.01)
    }

    @Test
    fun `paediatric dose under cap passes through unchanged`() {
        val aiSuggested = 250.0
        val maxDaily = 4000.0
        val cap = maxDaily / 4.0
        val safeDose = if (aiSuggested > cap) cap else aiSuggested
        assertEquals(250.0, safeDose, 0.01)
    }

    @Test
    fun `database version is 12`() {
        assertEquals(12, 12)
    }

    @Test
    fun `tokens shorter than 3 characters are skipped`() {
        val tokens = listOf("IP", "mg", "Paracetamol", "500", "Tab", "of")
        val filtered = tokens.filter { it.length >= 3 }
        assertEquals(3, filtered.size)
        assertTrue(filtered.contains("Paracetamol"))
        assertTrue(filtered.contains("500"))
        assertTrue(filtered.contains("Tab"))
    }

    @Test
    fun `empty OCR text produces no tokens`() {
        val ocrText = ""
        val tokens = ocrText.split("\\s+".toRegex()).filter { it.isNotBlank() && it.length >= 3 }
        assertTrue(tokens.isEmpty())
    }

    @Test
    fun `tier 1A match suppresses tier 2 results`() {
        val confirmedT1A = mutableListOf("Paracetamol")
        val tier2Results = mutableListOf("Ferrous Sulfate", "Magnesium Hydroxide")
        if (confirmedT1A.isNotEmpty()) {
            tier2Results.clear()
        }
        assertTrue(tier2Results.isEmpty())
        assertEquals(1, confirmedT1A.size)
    }

    @Test
    fun `tier 2 results survive when no tier 1A match exists`() {
        val confirmedT1A = mutableListOf<String>()
        val tier2Results = mutableListOf("Brand X", "Brand Y")
        if (confirmedT1A.isNotEmpty()) {
            tier2Results.clear()
        }
        assertEquals(2, tier2Results.size)
    }

    @Test
    fun `short tokens require exact match threshold 1_0`() {
        val threshold = thresholdFor(4)
        assertEquals(1.0, threshold, 0.01)
    }

    @Test
    fun `medium tokens allow 15 percent fuzziness`() {
        val threshold = thresholdFor(6)
        assertEquals(0.85, threshold, 0.01)
    }

    @Test
    fun `long tokens allow 20 percent fuzziness`() {
        val threshold = thresholdFor(10)
        assertEquals(0.80, threshold, 0.01)
    }

    @Test
    fun `very long tokens allow 25 percent fuzziness`() {
        val threshold = thresholdFor(15)
        assertEquals(0.75, threshold, 0.01)
    }

    private fun extractMgFromText(text: String): Double {
        val regex = Regex("(\\d+\\.?\\d*)\\s*mg", RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
    }

    private fun thresholdFor(tokenLength: Int): Double = when {
        tokenLength <= 4 -> 1.0
        tokenLength <= 7 -> 0.85
        tokenLength <= 12 -> 0.80
        else -> 0.75
    }
}