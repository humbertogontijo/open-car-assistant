#!/usr/bin/env bash
# Shared helpers for oca-setup (sourced).

log()  { printf '➜ %s\n' "$*"; }
ok()   { printf '✓ %s\n' "$*"; }
warn() { printf '⚠ %s\n' "$*" >&2; }
die()  { printf '✗ %s\n' "$*" >&2; exit 1; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "Missing command: $1"
}

gradlew() {
  if [[ -x "$ROOT/gradlew" ]]; then
    "$ROOT/gradlew" "$@"
  else
    require_cmd gradle
    gradle "$@"
  fi
}

load_integration() {
  local id="$1"
  local file=""
  if [[ -f "$ROOT/integrations/${id}/host.sh" ]]; then
    file="$ROOT/integrations/${id}/host.sh"
  else
    die "Unknown integration '$id' (expected integrations/${id}/host.sh)"
  fi
  # shellcheck source=/dev/null
  source "$file"
  : "${OCA_ADB_PORT:?integration must set OCA_ADB_PORT}"
  : "${OCA_ANDROID_USER:?}"
  : "${OCA_PACKAGE:?}"
  : "${OCA_ACTIVITY:?}"
  : "${OCA_PRIV_APP_DIR:?}"
  if [[ -z "${OCA_PRIVAPP_XML_SRC:-}" && -f "$ROOT/integrations/${id}/privapp-permissions.xml" ]]; then
    OCA_PRIVAPP_XML_SRC="$ROOT/integrations/${id}/privapp-permissions.xml"
  fi
  local host_disp="${OCA_HOST:-<set -H or OCA_HOST>}"
  ok "Integration: $id  host=$host_disp:$OCA_ADB_PORT  user=$OCA_ANDROID_USER  pkg=$OCA_PACKAGE"
}

require_host() {
  [[ -n "${OCA_HOST:-}" ]] || die "Set the HU address: --host / -H CAR_IP  (or export OCA_HOST)"
}

adb_s() {
  require_host
  adb -s "${OCA_HOST}:${OCA_ADB_PORT}" "$@"
}

oca_connect() {
  require_cmd adb
  require_host
  local serial="${OCA_HOST}:${OCA_ADB_PORT}"
  if ! adb devices | grep -q "^${serial}[[:space:]]"; then
    log "adb connect $serial"
    adb connect "$serial" >/dev/null
    sleep 1
  fi
  adb devices | grep -q "^${serial}[[:space:]]device" || die "Device not connected: $serial"
  ok "Connected $serial"
}

oca_build() {
  log "Building :app:assembleDebug"
  (
    cd "$ROOT"
    export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || true)}"
    gradlew :app:assembleDebug --quiet
  )
  ok "Built $OCA_APK_DEBUG"
}

oca_sign() {
  local debug_apk="${OCA_APK_DEBUG:?}"
  local signed_apk="${OCA_APK_SIGNED:?}"
  [[ -f "$debug_apk" ]] || die "APK missing — run build first: $debug_apk"
  log "Signing with :signing:signApk (community testkey)"
  (
    cd "$ROOT"
    export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 17 2>/dev/null || true)}"
    gradlew -q :signing:signApk \
      "-Pin=$debug_apk" \
      "-Pout=$signed_apk" \
      "-Pkey=$ROOT/libs/signing/community.pk8" \
      "-Pcert=$ROOT/libs/signing/community.pem"
  ) || die "Gradle signApk failed"
  [[ -f "$signed_apk" ]] || die "Signed APK not produced: $signed_apk"
  ok "Signed $signed_apk"
}

oca_ensure_apk() {
  if [[ ! -f "$OCA_APK_SIGNED" ]]; then
    if [[ -f "$OCA_APK_DEBUG" ]]; then
      oca_sign
    else
      oca_build
      oca_sign
    fi
  fi
}

oca_start() {
  log "Starting $OCA_PACKAGE/$OCA_ACTIVITY (user $OCA_ANDROID_USER)"
  adb_s shell am force-stop --user "$OCA_ANDROID_USER" "$OCA_PACKAGE" 2>/dev/null || true
  adb_s shell am start --user "$OCA_ANDROID_USER" -n "${OCA_PACKAGE}/${OCA_ACTIVITY}"
}
