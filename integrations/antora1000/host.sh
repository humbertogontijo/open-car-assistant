#!/usr/bin/env bash
# Antora 1000 / SE1000 head-unit defaults for oca-setup.
# Lives next to the Android module so new platforms copy one folder.
# Set OCA_HOST or pass -H CAR_IP (required for device commands).

OCA_INTEGRATION_ID="antora1000"
OCA_HOST="${OCA_HOST:-}"
OCA_ADB_PORT="${OCA_ADB_PORT:-5566}"
OCA_ANDROID_USER="${OCA_ANDROID_USER:-11}"

OCA_PACKAGE="${OCA_PACKAGE:-cc.opencar.assistant.debug}"
OCA_ACTIVITY="${OCA_ACTIVITY:-cc.opencar.assistant.MainActivity}"

OCA_APK_DEBUG="${OCA_APK_DEBUG:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"
OCA_APK_SIGNED="${OCA_APK_SIGNED:-$ROOT/app/build/outputs/apk/debug/app-debug_signed.apk}"

OCA_PRIV_APP_DIR="${OCA_PRIV_APP_DIR:-/system/priv-app/OpenCarAssistant}"
OCA_PRIVAPP_XML_SRC="${OCA_PRIVAPP_XML_SRC:-$ROOT/integrations/antora1000/privapp-permissions.xml}"
OCA_PRIVAPP_XML_DST="${OCA_PRIVAPP_XML_DST:-/system/etc/permissions/privapp-permissions-opencar.xml}"

OCA_RUNTIME_PERMS=""
OCA_PRIVILEGED_PERMS=""
OCA_INSTALL_PERMS=""
