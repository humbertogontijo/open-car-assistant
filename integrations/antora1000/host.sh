#!/usr/bin/env bash
# Antora 1000 / SE1000 head-unit defaults for oaa-setup.
# Lives next to the Android module so new platforms copy one folder.
# Set OAA_HOST or pass -H CAR_IP (required for device commands).

OAA_INTEGRATION_ID="antora1000"
OAA_HOST="${OAA_HOST:-}"
OAA_ADB_PORT="${OAA_ADB_PORT:-5566}"
OAA_ANDROID_USER="${OAA_ANDROID_USER:-11}"

OAA_PACKAGE="${OAA_PACKAGE:-cc.opencar.assistant.debug}"
OAA_ACTIVITY="${OAA_ACTIVITY:-cc.opencar.assistant.MainActivity}"

OAA_APK_DEBUG="${OAA_APK_DEBUG:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"
OAA_APK_SIGNED="${OAA_APK_SIGNED:-$ROOT/app/build/outputs/apk/debug/app-debug_signed.apk}"

OAA_RUNTIME_PERMS=""
OAA_INSTALL_PERMS=""
