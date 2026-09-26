#!/usr/bin/env bash
# IHU629G / EX2-class AdaptAPI head-unit defaults for oaa-setup.
# Set OAA_HOST or pass -H CAR_IP (required for device commands).

OAA_INTEGRATION_ID="ihu629g"
OAA_HOST="${OAA_HOST:-}"
OAA_ADB_PORT="${OAA_ADB_PORT:-5555}"
OAA_ANDROID_USER="${OAA_ANDROID_USER:-0}"

OAA_PACKAGE="${OAA_PACKAGE:-cc.opencar.assistant.debug}"
OAA_ACTIVITY="${OAA_ACTIVITY:-cc.opencar.assistant.MainActivity}"

OAA_APK_DEBUG="${OAA_APK_DEBUG:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"
OAA_APK_SIGNED="${OAA_APK_SIGNED:-$ROOT/app/build/outputs/apk/debug/app-debug_signed.apk}"

OAA_RUNTIME_PERMS=""
OAA_INSTALL_PERMS=""
