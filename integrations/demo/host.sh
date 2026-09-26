#!/usr/bin/env bash
# Demo / CI fake vehicle — no head unit required.
# Lab → override integration id to "demo", or match fingerprint containing "demo".

OAA_INTEGRATION_ID="demo"
OAA_HOST="${OAA_HOST:-}"
OAA_ADB_PORT="${OAA_ADB_PORT:-5555}"
OAA_ANDROID_USER="${OAA_ANDROID_USER:-0}"

OAA_PACKAGE="${OAA_PACKAGE:-cc.opencar.assistant.debug}"
OAA_ACTIVITY="${OAA_ACTIVITY:-cc.opencar.assistant.MainActivity}"

OAA_APK_DEBUG="${OAA_APK_DEBUG:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"
OAA_APK_SIGNED="${OAA_APK_SIGNED:-$ROOT/app/build/outputs/apk/debug/app-debug_signed.apk}"

OAA_RUNTIME_PERMS=""
OAA_INSTALL_PERMS=""
