#!/bin/bash

# Junkcoin Wallet - Build & Install Script
# Usage:
#   ./build.sh           Build debug APK
#   ./build.sh install   Build and install to connected device

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
APP_DIR="$PROJECT_DIR/android"
APK_PATH="$APP_DIR/app/build/outputs/apk/debug/app-debug.apk"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

print_header() {
    echo ""
    echo -e "${CYAN}╔══════════════════════════════════════════╗${NC}"
    echo -e "${CYAN}║     JUNKCOIN WALLET - BUILD TOOL         ║${NC}"
    echo -e "${CYAN}╚══════════════════════════════════════════╝${NC}"
    echo ""
}

check_prerequisites() {
    echo -e "${YELLOW}[1/4] Checking prerequisites...${NC}"

    if ! command -v java &> /dev/null; then
        echo -e "${RED}✗ Java not found. Install JDK 17+.${NC}"
        exit 1
    fi
    echo -e "${GREEN}  ✓ Java: $(java -version 2>&1 | head -1)${NC}"

    if [ ! -f "$APP_DIR/gradlew" ]; then
        echo -e "${RED}✗ gradlew not found in $APP_DIR${NC}"
        exit 1
    fi
    echo -e "${GREEN}  ✓ Gradle wrapper found${NC}"

    if [ ! -f "$APP_DIR/local.properties" ]; then
        if [ -n "$ANDROID_HOME" ]; then
            echo "sdk.dir=$ANDROID_HOME" > "$APP_DIR/local.properties"
            echo -e "${GREEN}  ✓ Created local.properties (sdk.dir=$ANDROID_HOME)${NC}"
        elif [ -d "$HOME/Android/Sdk" ]; then
            echo "sdk.dir=$HOME/Android/Sdk" > "$APP_DIR/local.properties"
            echo -e "${GREEN}  ✓ Created local.properties (sdk.dir=$HOME/Android/Sdk)${NC}"
        else
            echo -e "${RED}✗ Android SDK not found. Set ANDROID_HOME or install SDK.${NC}"
            exit 1
        fi
    else
        echo -e "${GREEN}  ✓ local.properties exists${NC}"
    fi
}

build_debug() {
    echo ""
    echo -e "${YELLOW}[2/4] Building debug APK...${NC}"
    cd "$APP_DIR"
    chmod +x gradlew
    ./gradlew assembleDebug --no-daemon --stacktrace 2>&1

    if [ $? -eq 0 ] && [ -f "$APK_PATH" ]; then
        APK_SIZE=$(du -h "$APK_PATH" | cut -f1)
        echo -e "${GREEN}  ✓ Build successful!${NC}"
        echo -e "${GREEN}  ✓ APK: $APK_PATH ($APK_SIZE)${NC}"
    else
        echo -e "${RED}  ✗ Build failed.${NC}"
        exit 1
    fi
}

install_app() {
    echo ""
    echo -e "${YELLOW}[3/4] Installing to device...${NC}"

    if ! command -v adb &> /dev/null; then
        if [ -n "$ANDROID_HOME" ]; then
            export PATH="$ANDROID_HOME/platform-tools:$PATH"
        fi
    fi

    if ! command -v adb &> /dev/null; then
        echo -e "${RED}✗ adb not found. Install Android SDK platform-tools.${NC}"
        exit 1
    fi

    DEVICES=$(adb devices | grep -v "List" | grep "device$" | wc -l)
    if [ "$DEVICES" -eq 0 ]; then
        echo -e "${RED}✗ No device connected.${NC}"
        echo -e "${YELLOW}  Connect a device via USB or start an emulator.${NC}"
        exit 1
    fi
    echo -e "${GREEN}  ✓ $DEVICES device(s) connected${NC}"

    adb install -r --user 0 "$APK_PATH" 2>&1
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}  ✓ App installed successfully!${NC}"
    else
        echo -e "${RED}  ✗ Installation failed.${NC}"
        exit 1
    fi
}

launch_app() {
    echo ""
    echo -e "${YELLOW}[4/4] Launching app...${NC}"
    adb shell am start --user 0 -n junkwallet.debug/junkwallet.MainActivity 2>&1
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}  ✓ App launched!${NC}"
    else
        echo -e "${YELLOW}  ⚠ App installed but launch failed. Open manually.${NC}"
    fi
}

print_summary() {
    echo ""
    echo -e "${CYAN}════════════════════════════════════════════${NC}"
    echo -e "${GREEN}Done!${NC}"
    echo ""
    echo "  APK:  $APK_PATH"
    echo "  Size: $(du -h "$APK_PATH" | cut -f1)"
    echo ""
}

# Main
print_header
check_prerequisites
build_debug

if [ "$1" = "install" ] || [ "$1" = "-install" ] || [ "$1" = "--install" ]; then
    install_app
    launch_app
fi

print_summary
