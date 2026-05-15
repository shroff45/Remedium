# Remedium - AI-Powered Medicine Verification for Rural India

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-green" alt="Platform">
  <img src="https://img.shields.io/badge/AI-Gemma%204-blue" alt="AI">
  <img src="https://img.shields.io/badge/License-MIT-orange" alt="License">
</p>

A mobile-first medicine verification system designed for rural India, leveraging on-device AI (Google Gemma 4) to verify medicines, check drug interactions, and provide bilingual health information.

## The Problem

In rural India:
- **85%** of pharmacists lack formal training
- **70%** of village pharmacies dispense Schedule H/H1 drugs without prescriptions
- Counterfeit medicines account for **25%** of drugs in circulation
- 67% of deaths from adverse drug reactions occur in low-resource settings

Remedium addresses this by putting a pharmacologist in every pocket.

## Demo

*Add your demo GIF/video here*

## Features

### Core Capabilities
- 📷 **OCR Medicine Scanning** - Instantly identify medicines from strip photos
- ✅ **Tiered Verification System** - Green (VERIFIED) / Yellow (IDENTIFIED) / Red (NOT FOUND)
- 💊 **Drug Interaction Detection** - AI-powered safety check for combining medicines
- 🗣️ **Voice I/O** - Speak questions in Hindi/English, hear responses read aloud
- 🏥 **Bilingual Support** - Full Hindi + English interface

### Safety Features
- **24/25 (96%) Grounding Score** on safety refusal tests
- Database-first: AI suggests → Kotlin validates → DB wins on conflict
- Schedule H1 warning dialogs for regulated drugs
- Zero hallucinations on medical contraindications

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    REMEDIUM APP (Android)                   │
├─────────────────────────────────────────────────────────────┤
│  Camera → ML Kit OCR → SQLite DB Matching                   │
│                                           ↓                 │
│  Gemma 4 (LiteRT-LM) ← ContextAssembler ← MedicineInfo     │
│                                           ↓                 │
│  Chat UI (RecyclerView) ← TTS/SpeechRecognizer             │
└─────────────────────────────────────────────────────────────┘
```

### Tier System
| Tier | Color | Data Quality | Example |
|------|-------|--------------|---------|
| TIER_1A | 🟢 GREEN | Full clinical (dose, warnings,contra) | Paracetamol |
| TIER_2 | 🟡 YELLOW | Brand name only | Most supplements |
| NONE | 🔴 RED | Not in database | Unknown |

## Tech Stack

- **AI**: Google Gemma 4 (on-device, LiteRT-LM 0.11.0)
- **OCR**: ML Kit Text Recognition
- **Database**: SQLite with 15,000+ Indian medicines
- **Frontend**: Kotlin + Jetpack Compose + Material Design
- **Voice**: Android SpeechRecognizer + TextToSpeech
- **Build**: Gradle + Kotlin DSL

## Safety Model

Remedium enforces safety through a **human-in-the-loop** architecture:

1. **Context Assembly** - Verified DB data is injected into every prompt
2. **Strict Output Format** - Gemma must output parseable format (VERDICT/REASON/WATCH FOR)
3. **Kotlin Validation** - Dose suggestions capped against DB maximums
4. **Timeout Protection** - 35-second inference limit prevents runaway

### Grounding Test Results
```
Refusals (Q1-10):   10/10 ✅ - Perfect safety
Allowed (Q11-20):   10/10 ✅ - Accurate from context
Borderline (Q21-25): 4/5  ✅ - Safe without diagnosing
─────────────────────────────
TOTAL:              24/25 (96%)
```

## Setup

### Prerequisites
- Android Studio (Arctic Fox or later)
- Android SDK 24+ (Android 7.0)
- 4GB RAM minimum (for Gemma 4 on-device)

### Build
```bash
# Clone the repo
git clone https://github.com/shroff45/Remedium.git
cd Remedium

# Open in Android Studio
# Build → Run on device/emulator

# Or command line
./gradlew assembleDebug
```

### Model Setup
Gemma 4 model file must be placed at:
```
/data/local/tmp/gemma.litertlm
```

## Limitations

- **Offline-first**: Works without internet (Gemma runs on-device)
- **Language**: Hindi + English only (Tamil/Telugu coming)
- **Scope**: Indian medicines only (CDSCO database)
- **Accuracy**: Tier 2 (IDENTIFIED) lacks clinical data

## Data Source

Medicine data sourced from:
- CDSCO (Central Drugs Standard Control Organization)
- Indian Pharmacopoeia Commission
- National Formulary of India

## Future Work

- Drug interaction database expansion
- Tamil/Telugu language support
- Side effect severity prediction
- Pharmacist chat integration

## License

MIT License - See LICENSE file

## Credits

- Google for Gemma 4 and LiteRT
- ML Kit team for text recognition
- Indian Pharmacopoeia Commission for drug data