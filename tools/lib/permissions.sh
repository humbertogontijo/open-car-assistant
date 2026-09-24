#!/usr/bin/env bash
# Permission check / grant helpers (sourced). Compatible with macOS Bash 3.2.

OCA_RUNTIME_PERMS_DEFAULT="android.permission.CAMERA android.permission.RECORD_AUDIO android.car.permission.CAR_SPEED android.car.permission.CAR_ENERGY"
OCA_PRIVILEGED_PERMS_DEFAULT="android.car.permission.CAR_VENDOR_EXTENSION android.car.permission.CONTROL_CAR_CLIMATE"
OCA_INSTALL_PERMS_DEFAULT="android.car.permission.CAR_INFO android.car.permission.CAR_POWERTRAIN"

_runtime_perms() {
  if [[ -n "${OCA_RUNTIME_PERMS:-}" ]]; then echo "$OCA_RUNTIME_PERMS"; else echo "$OCA_RUNTIME_PERMS_DEFAULT"; fi
}
_privileged_perms() {
  if [[ -n "${OCA_PRIVILEGED_PERMS:-}" ]]; then echo "$OCA_PRIVILEGED_PERMS"; else echo "$OCA_PRIVILEGED_PERMS_DEFAULT"; fi
}
_install_perms() {
  if [[ -n "${OCA_INSTALL_PERMS:-}" ]]; then echo "$OCA_INSTALL_PERMS"; else echo "$OCA_INSTALL_PERMS_DEFAULT"; fi
}

oca_perm_granted() {
  local perm="$1"
  adb_s shell dumpsys package "$OCA_PACKAGE" 2>/dev/null \
    | tr -d '\r' \
    | grep -F "$perm" \
    | grep -q "granted=true"
}

oca_grant_runtime() {
  log "Granting runtime permissions (user $OCA_ANDROID_USER)"
  local p
  for p in $(_runtime_perms); do
    if adb_s shell pm grant --user "$OCA_ANDROID_USER" "$OCA_PACKAGE" "$p" 2>/dev/null; then
      ok "granted $p"
    else
      warn "could not grant $p (may already be granted or not changeable)"
    fi
  done
  # Float chip (WindowManager TYPE_APPLICATION_OVERLAY)
  if adb_s shell appops set --user "$OCA_ANDROID_USER" "$OCA_PACKAGE" SYSTEM_ALERT_WINDOW allow 2>/dev/null \
    || adb_s shell appops set "$OCA_PACKAGE" SYSTEM_ALERT_WINDOW allow 2>/dev/null; then
    ok "appops SYSTEM_ALERT_WINDOW allow"
  else
    warn "could not set SYSTEM_ALERT_WINDOW (float chip may stay hidden)"
  fi
}

oca_check() {
  echo
  echo "=== Package ==="
  local path=""
  path="$(adb_s shell pm path --user "$OCA_ANDROID_USER" "$OCA_PACKAGE" 2>/dev/null | tr -d '\r' | head -1)"
  if [[ -z "$path" ]]; then
    # Fall back: some images omit --user on pm path
    path="$(adb_s shell pm path "$OCA_PACKAGE" 2>/dev/null | tr -d '\r' | head -1)"
  fi
  if [[ -z "$path" ]]; then
    warn "Package not installed for user $OCA_ANDROID_USER: $OCA_PACKAGE"
    return 1
  fi
  echo "  $path (user $OCA_ANDROID_USER)"
  adb_s shell dumpsys package "$OCA_PACKAGE" 2>/dev/null | tr -d '\r' \
    | grep -E 'codePath=|pkgFlags=|flags=\[|PRIVILEGED' | head -8 | sed 's/^/  /'
  if echo "$path" | grep -qE '/system|/system_ext|/product|/vendor'; then
    ok "Installed on system partition (privileged candidate)"
  else
    ok "Installed under /data (default unprivileged path)"
  fi

  echo
  echo "=== Runtime ==="
  local p
  for p in $(_runtime_perms); do
    if oca_perm_granted "$p"; then ok "$p"; else warn "MISSING $p"; fi
  done

  echo
  echo "=== Install ==="
  for p in $(_install_perms); do
    if oca_perm_granted "$p"; then ok "$p"; else warn "MISSING $p"; fi
  done

  echo
  echo "=== Privileged (optional — formal VHAL / HVAC) ==="
  local priv_ok=1
  for p in $(_privileged_perms); do
    if oca_perm_granted "$p"; then ok "$p"; else warn "not granted: $p"; priv_ok=0; fi
  done

  echo
  echo "=== Overlay (float chip) ==="
  local ops
  ops="$(adb_s shell appops get "$OCA_PACKAGE" SYSTEM_ALERT_WINDOW 2>/dev/null | tr -d '\r' | head -1)"
  if echo "$ops" | grep -qiE 'allow|foreground'; then
    ok "SYSTEM_ALERT_WINDOW $ops"
  else
    warn "SYSTEM_ALERT_WINDOW: ${ops:-unknown} (float chip needs allow)"
  fi

  echo
  if [[ "$priv_ok" -eq 1 ]]; then
    ok "Formal vendor/HVAC privileges present"
  else
    ok "Running unprivileged (default). Vendor props often still work on Antora;"
    echo "    elevate only if Climate/vendor writes are denied:"
    echo "    ./tools/oca-setup -i ${INTEGRATION:-antora1000} -H $OCA_HOST setup --privileged"
    echo "    (see docs/safety.md)"
  fi

  if curl -sf --max-time 2 "http://${OCA_HOST}:8787/api/setup" >/dev/null 2>&1; then
    echo
    echo "=== Live /api/setup ==="
    curl -s "http://${OCA_HOST}:8787/api/setup" \
      | python3 -c 'import json,sys; d=json.load(sys.stdin); print("  complete=%s runtimeOk=%s privilegedOk=%s hasBasicTelemetry=%s" % (d.get("complete"), d.get("runtimeOk"), d.get("privilegedOk"), d.get("hasBasicTelemetry")))' \
      2>/dev/null || true
  fi
  return 0
}
