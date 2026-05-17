#!/bin/bash
# Remedium Setup Script (Linux/macOS)
# Pushes the Gemma 4 E2B model to a connected Android device

set -e

MODEL_FILE="gemma-4-E2B-it.litertlm"
DEVICE_PATH="/data/local/tmp/gemma.litertlm"

echo "============================================"
echo "  Remedium — Gemma 4 E2B Model Setup"
echo "============================================"
echo ""

echo "[1/3] Checking ADB connection..."
if ! adb devices | grep -q "device$"; then
    echo "ERROR: No Android device found."
    echo "       Connect your phone via USB and enable USB debugging."
    exit 1
fi
echo "      Device connected."

echo "[2/3] Looking for model file..."
if [ ! -f "$MODEL_FILE" ]; then
    echo "ERROR: $MODEL_FILE not found in current directory."
    echo ""
    echo "Download from: https://github.com/shroff45/Remedium/releases/tag/v1.0"
    echo "Place the .litertlm file in this directory and re-run."
    exit 1
fi
echo "      Found: $MODEL_FILE ($(du -h "$MODEL_FILE" | cut -f1))"

echo "[3/3] Pushing model to device (2-5 min)..."
adb push "$MODEL_FILE" "$DEVICE_PATH"

echo ""
echo "============================================"
echo "  ✅ Setup complete!"
echo "  Model installed at: $DEVICE_PATH"
echo "============================================"