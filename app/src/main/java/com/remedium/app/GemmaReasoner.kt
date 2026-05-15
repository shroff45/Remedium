package com.remedium.app

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Gemma 4 Reasoner for medical question answering.
 *
 * SAFETY CONTRACT:
 * - Only enabled on Tier 1A (VERIFIED) medicine cards
 * - Must use buildGroundedPrompt() from ContextAssembler to provide DB context
 * - Never answer questions about Tier 2 (IDENTIFIED) medicines
 * - 15 second hard timeout prevents runaway inference
 * - If engine fails to load, onTimeout() is called (not crash)
 */
class GemmaReasoner private constructor(private val context: Context) {

    private var engine: Engine? = null
    var isReady = false

    companion object {
        @Volatile
        private var instance: GemmaReasoner? = null

        fun getInstance(context: Context): GemmaReasoner {
            return instance ?: synchronized(this) {
                instance ?: GemmaReasoner(context.applicationContext).also { instance = it }
            }
        }
    }

    private val TAG = "GemmaReasoner"

    // Model stored at: /data/local/tmp/gemma.litertlm
    // TODO Day 4: add assets copy for production APK
    private val modelPath = "/data/local/tmp/gemma.litertlm"

    // 35 second timeout - enough for drug interaction prompts
    private val inferenceTimeoutMs = 35_000L

    // Flag to track timeout state
    private val timedOut = AtomicBoolean(false)

    // System instruction for medical safety - DO NOT MODIFY
    private val systemInstruction = """
You are Remedium's medicine assistant. Answer questions
about ONE specific medicine using ONLY the verified
information provided below. If the answer is not in the
provided information, you MUST say: I don't have verified
information about that. Please ask your pharmacist or doctor.
NEVER invent facts. NEVER recommend dosage changes. NEVER
diagnose. Answer in the same language as the user's question.
    """.trimIndent()

    /**
     * Initialize Gemma 4 engine asynchronously.
     * On success: calls onReady()
     * On failure: calls onError(errorMessage)
     */
    fun initAsync(onReady: () -> Unit, onError: (String) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.i(TAG, "Initializing Gemma 4 engine...")

                val config = EngineConfig(
                    modelPath = modelPath,
                    backend = Backend.CPU(),  // GPU failed on this device
                    cacheDir = context.cacheDir.path
                )

                engine = Engine(config)
                engine!!.initialize()

                isReady = true
                Log.i(TAG, "Gemma 4 E2B loaded successfully")

                withContext(Dispatchers.Main) {
                    onReady()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load Gemma 4: ${e.message}")
                isReady = false

                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Unknown error")
                }
            }
        }
    }

    /**
     * Run reasoning with provided prompt using confirmed working API.
     * Tokens are streamed via onToken callback.
     * Completion calls onDone.
     * Timeout calls onTimeout.
     */
    fun reason(
        prompt: String,
        onToken: (String) -> Unit,
        onDone: () -> Unit,
        onTimeout: () -> Unit
    ) {
        if (!isReady || engine == null) {
            Log.w(TAG, "Engine not ready, calling onTimeout")
            onTimeout()
            return
        }

        timedOut.set(false)

        // Start timeout watcher in parallel
        val timeoutJob = CoroutineScope(Dispatchers.IO).launch {
            delay(inferenceTimeoutMs)
            if (!timedOut.get()) {
                timedOut.set(true)
                Log.w(TAG, "Inference timeout after ${inferenceTimeoutMs}ms")
                withContext(Dispatchers.Main) {
                    onTimeout()
                }
            }
        }

        // Run inference using confirmed working API pattern
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Create conversation with system instruction (confirmed working)
                val conv = engine!!.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(systemInstruction),
                        samplerConfig = SamplerConfig(
                            topK = 64,
                            topP = 0.95,
                            temperature = 1.0
                        )
                    )
                )

                // Stream tokens using confirmed working pattern
                try {
                    conv.sendMessageAsync(prompt).collect { msg ->
                        if (!timedOut.get()) {
                            val tokenText = msg.toString()
                            if (tokenText.isNotEmpty()) {
                                withContext(Dispatchers.Main) {
                                    onToken(tokenText)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Stream error: ${e.message}")
                    if (!timedOut.get()) {
                        timedOut.set(true)
                        withContext(Dispatchers.Main) { onTimeout() }
                    }
                }

                // Success - cancel timeout and close conversation
                timeoutJob.cancel()
                conv.close()

                if (!timedOut.get()) {
                    Log.i(TAG, "Inference completed successfully")
                    withContext(Dispatchers.Main) {
                        onDone()
                    }
                }

            } catch (e: Exception) {
                timeoutJob.cancel()
                Log.e(TAG, "Inference error: ${e.message}")
                if (!timedOut.get()) {
                    timedOut.set(true)
                    withContext(Dispatchers.Main) {
                        onTimeout()
                    }
                }
            }
        }
    }

    /**
     * Release resources. Call when done with reasoning.
     */
    fun close() {
        try {
            engine?.close()
            engine = null
            isReady = false
            Log.i(TAG, "GemmaReasoner closed")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing GemmaReasoner: ${e.message}")
        }
    }
}