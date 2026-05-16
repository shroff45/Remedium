# 🔬 Remedium

**Your pocket AI pharmacist. Offline. Bilingual. Safe.**

Point your phone at a medicine strip → know what it is → hear it explained in Hindi → ask questions → get safe answers.

Built for 150 million elderly, low-literacy, rural Indian patients who cannot read their own medicine labels.

> ⚠️ Remedium is not a doctor. It helps you understand what you're holding. Always consult a pharmacist or doctor.

---

## 🎯 Why This Exists

Mrs. Sharma, 68, lives in a village in Rajasthan. She takes 4 medicines daily. She cannot read the tiny English text on any of them. She cannot tell which one is for blood pressure and which is for pain. Her nearest pharmacist is 12 km away.

**She is not alone.** 150 million Indians over 60 face this daily. Medicine packaging is inconsistent, text is tiny, and information is locked in a language they don't read.

Remedium gives her a voice — literally. She points, scans, and **hears** her medicine explained in Hindi. No internet needed.

---

## ✨ What It Does

| Feature | How |
|---------|-----|
| 📸 Scan medicine strip | CameraX + ML Kit OCR (on-device) |
| 🔍 Identify the drug | SQLite lookup with fuzzy matching |
| 🟢 Show verified info | Tier 1A: 28 drugs, 94 brands, full clinical data |
| 🟡 Show brand info | Tier 2: 7,477 brands, identification only |
| 🗣️ Speak in Hindi/English | Bilingual TTS with one-tap playback |
| 🤖 Ask follow-up questions | Gemma 4 E2B on-device, grounded in DB facts |
| ⚡ Check drug interactions | Scan two medicines → Gemma reasons about safety |
| 🛡️ Refuse unsafe questions | 24/25 grounding score (96%) |
| ✈️ Work offline | Zero internet. Ever. |

---

## 📱 Screenshots

| Scanner | Verified (English) | Verified (Hindi) |
|---------|-------------------|------------------|
| ![Scanner](docs/screenshots/scanner.png) | ![Verified EN](docs/screenshots/result-verified.png) | ![Verified HI](docs/screenshots/result-hi.png) |

| Drug Interaction | Safety Refusal | Unknown Medicine |
|------------------|----------------|------------------|
| ![Interaction](docs/screenshots/interaction.png) | ![Refusal](docs/screenshots/refusal.png) | ![Unknown](docs/screenshots/unknown.png) |

---

## 🎬 Demo Video

[Watch the 3-minute demo on YouTube](https://youtube.com/REPLACE_WITH_YOUR_LINK)

---

## 🏗️ Architecture

```
┌─────────────┐
│   CameraX   │  Tap to scan
└──────┬──────┘
       │
┌──────▼──────┐
│  ML Kit OCR │  On-device, Latin text
└──────┬──────┘
       │
┌──────▼──────────┐
│ Token extraction │  Normalize + deduplicate
│ + fuzzy match    │  Length-aware Levenshtein
└──────┬──────────┘
       │
  ┌────┴─────┐
  │          │
  ▼          ▼
┌────┐   ┌────┐
│T1A │   │ T2 │   Tier-based trust
│28  │   │7477│   Verified vs Identified
│drugs│   │brands│  Different data, different UI
└─┬──┘   └──┬─┘
  │         │
  ▼         ▼
┌──────────────────┐
│  Result Card     │  🟢 Verified / 🟡 Identified / ⚫ Not Found
│  + Warnings      │  Critical highlighted, Schedule H1 dialog
│  + Bilingual TTS │  Hindi / English with one tap
└────────┬─────────┘
         │  (Tier 1A only)
         ▼
┌──────────────────┐
│  Gemma 4 E2B     │  On-device via LiteRT-LM
│  Grounded QA     │  ONLY answers from DB context
│  Interactions    │  NEVER invents medical facts
└──────────────────┘
```

**Key principle:** Gemma does NOT identify medicines. The database does. Gemma ONLY reasons about already-identified Tier 1A medicines using verified context. This prevents hallucination at the architecture level.

---

## 🛡️ Safety Model

This is not an afterthought. Safety is the architecture.

### Tier-Based Trust

| Tier | Count | Data | Chatbot | Rationale |
|------|-------|------|---------|-----------|
| **Tier 1A** 🟢 | 28 drugs, 94 brands | Full clinical: dose, warnings, side effects, contraindications, Hindi translations | ✅ Yes | Pharmacist-verified |
| **Tier 2** 🟡 | 7,477 brands | Brand name, manufacturer, composition | ❌ No | Unverified bulk data |
| **Unknown** ⚫ | — | Raw OCR text only | ❌ No | No match = no claims |

### Grounding Score: 24/25 (96%)

We tested Gemma with 25 questions across 3 categories:

| Category | Score | Description |
|----------|-------|-------------|
| 🔴 Must refuse | **10/10** | "Can I take 8 tablets?" → Refused |
| 🟢 Must answer | **10/10** | "When should I take this?" → Answered from DB |
| 🟡 Borderline | **4/5** | Edge cases with partial info |

### Safety Rules (Non-Negotiable)

1. Never invent medical facts
2. Tier 1A always wins over Tier 2 (enforced via "First T1A Match Wins" logic)
3. Tier 2 cards show brand info ONLY — no dose, no warnings, no chatbot
4. Chatbot button appears ONLY on Tier 1A green VERIFIED cards
5. Alternatives shown ONLY for OTC / Schedule H, never H1
6. All clinical info traces to verified DB source
7. Paediatric doses validated against DB ceiling
8. Schedule H1 prescription dialog fires on every H1 scan
9. If context is insufficient, Gemma says: *"I don't have verified info. Please ask your pharmacist or doctor."*

---

## 🤖 Gemma 4 Integration

**Model:** Gemma 4 E2B (`gemma-4-E2B-it.litertlm`, 2.59 GB)
**Runtime:** LiteRT-LM v0.11.0, CPU-only
**Device tested:** POCO X6 5G (Snapdragon 7s Gen 2, 11.5 GB RAM, Android 16)

| Metric | Value |
|--------|-------|
| Model load | 25.66s (one-time) |
| Time to first token | 1,164ms |
| Total inference | ~7s (34 chunks) |
| Decode speed | ~5 tokens/sec |

### How Gemma Is Used

1. **Grounded QA:** User asks a question → `ContextAssembler.kt` builds a prompt with ALL verified DB data for that drug → Gemma answers ONLY from that context → if context is insufficient, it refuses

2. **Drug Interactions:** User scans two medicines → both T1A drugs' clinical data injected into prompt → Gemma reasons about interactions → verdict: SAFE / CONSULT_DOCTOR / NO

3. **Paediatric Safety Gate:** If Gemma suggests a dose → `validatePaedDose()` extracts mg value → compares against DB max daily dose / 4 → if AI exceeds cap → override with DB ceiling

### Why Gemma Specifically

- Runs fully on-device (privacy for medical data)
- Instruction-following is strong enough for reliable refusal
- LiteRT-LM integration makes Android deployment seamless
- E2B size fits in RAM on mid-range phones with 8GB+

---

## 🌐 Bilingual Support

All 28 verified drugs include Hindi translations for:
- Common uses
- Standard adult dose
- Common side effects
- Contraindications
- Alcohol warnings
- Timing notes
- Severity-ranked warnings

Plus on-device TTS reads everything aloud in Hindi or English with one tap. No cloud calls.

---

## 📂 Repository Structure

```
Remedium/
├── app/
│   ├── src/main/java/com/remedium/app/
│   │   ├── MainActivity.kt           — Camera, OCR, result rendering
│   │   ├── MedicineLookup.kt       — Search pipeline + fuzzy matching + T1A supremacy
│   │   ├── DatabaseHelper.kt        — DB copy + version management (v12)
│   │   ├── GemmaReasoner.kt         — LiteRT-LM engine, 35s timeout
│   │   ├── ContextAssembler.kt      — Grounded prompt builder + paed safety
│   │   ├── AskQuestionActivity.kt   — Chat UI, voice input, TTS toggle
│   │   └── ChatAdapter.kt           — Chat history RecyclerView
│   ├── src/main/assets/
│   │   └── remedium.db              — SQLite DB v12 (~3.5 MB)
│   └── src/main/res/layout/
│       ├── activity_main.xml        — Camera + viewfinder overlay
│       ├── dialog_ask_question.xml  — Chat dialog
│       └── item_chat_message.xml    — Chat bubble
└── README.md
```

---

## 🚀 How to Build

### Prerequisites
- Android Studio (Flamingo or later)
- Android device with USB debugging (API 24+, tested on API 36)
- ~3 GB free storage for Gemma model

### Steps
1. Clone this repo
2. Open in Android Studio
3. Sync Gradle
4. Push the Gemma model to your device:
   ```bash
   adb push gemma-4-E2B-it.litertlm /data/local/tmp/gemma.litertlm
   ```
5. Run on connected device
6. Grant camera + microphone permissions
7. Scan a medicine strip

---

## 📊 Database

| Table | Records | Purpose |
|-------|---------|---------|
| `drugs` | 28 | Verified generic drugs with clinical data (English + Hindi) |
| `brand_products` | 94 | Brand-to-generic mapping |
| `search_aliases` | 222 | OCR-tolerant lookup entries |
| `warnings` | 23 | Severity-ranked safety warnings |
| `tier2_brand_products` | 7,477 | Bulk brand identification |
| `tier2_search_aliases` | ~17,600 | Tier 2 alias lookup |

**DB_VERSION:** 12 — auto-migrated on launch.

---

## ⚠️ Limitations

- **28 verified drugs** — India has thousands. This covers the most common; expansion is ongoing
- **2.59 GB model** — won't fit on budget phones (₹8,000 range). Needs quantized variant
- **Latin OCR only** — Hindi/Devanagari text on strips is not yet parsed
- **7s inference** — acceptable but not instant. Quantization would help
- **No scan history** — forgotten between sessions
- **Not a doctor** — always verify with a healthcare professional

---

## 🔮 Roadmap

| Version | Plan |
|---------|------|
| **V2** | 100+ verified drugs, OCR confidence warnings, Room scan history |
| **V3** | Hindi OCR (Devanagari), LoRA fine-tuning on PubMedQA, quantized model < 1GB |
| **V4** | Multilingual (Tamil, Telugu, Bengali, Marathi), prescription OCR, medicine reminders, Bluetooth health monitoring |

---

## 🏆 Hackathon

Built for **The Gemma 4 Good Hackathon** (Google DeepMind / Kaggle)

**Tracks targeted:**
- 🎯 Main Track ($50K)
- 🛡️ Safety & Trust ($10K)
- ⚡ LiteRT ($10K)

---

## 📄 License

MIT

---

## 🙏 Acknowledgements

- Google DeepMind — Gemma 4 model + LiteRT-LM runtime
- Google ML Kit — On-device text recognition
- Android CameraX — Camera pipeline
- SQLite — Local-first data
- Every pharmacist who verified our drug data