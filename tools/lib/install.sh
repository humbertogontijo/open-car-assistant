#!/usr/bin/env bash
# Install helpers: /data install and privileged priv-app path (sourced).

oca_install_data() {
  [[ -f "$OCA_APK_SIGNED" ]] || die "Missing signed APK: $OCA_APK_SIGNED"
  # If a stale priv-app copy exists, prefer leaving it; data install may fail over it.
  log "Installing to /data (user $OCA_ANDROID_USER): $OCA_APK_SIGNED"
  if ! adb_s install -r --user "$OCA_ANDROID_USER" "$OCA_APK_SIGNED" 2>/dev/null; then
    # Some HUs reject --user on install; fall back
    adb_s install -r "$OCA_APK_SIGNED"
  fi
  ok "Data install done"
  oca_start
}

oca_uninstall() {
  local mode="${1:-all}"
  log "Uninstall $OCA_PACKAGE ($mode)"
  adb_s uninstall "$OCA_PACKAGE" 2>/dev/null || true
  if [[ "$mode" == "privileged" || "$mode" == "all" || "$mode" == "--privileged" ]]; then
    oca_connect
    adb_s root >/dev/null 2>&1 || true
    sleep 1
    adb_s remount >/dev/null 2>&1 || warn "remount failed — priv-app cleanup may need manual steps"
    adb_s shell "rm -rf '${OCA_PRIV_APP_DIR}' '${OCA_PRIVAPP_XML_DST}'" 2>/dev/null || true
    warn "If the app was priv-app, reboot to finish cleanup"
  fi
}

oca_install_privileged() {
  [[ -f "$OCA_APK_SIGNED" ]] || die "Missing signed APK: $OCA_APK_SIGNED"
  local xml_src="${OCA_PRIVAPP_XML_SRC:-$ROOT/tools/integrations/privapp-permissions-opencar.xml}"
  [[ -f "$xml_src" ]] || die "Missing privapp XML: $xml_src"

  log "Privileged install requires: unlocked/userdebug HU, adb root, remount, reboot"
  adb_s root >/dev/null
  sleep 1
  adb_s remount || die "adb remount failed — privileged install needs a remountable system"

  # Remove /data copy so PackageManager picks up the system one cleanly
  log "Removing /data package if present"
  adb_s uninstall "$OCA_PACKAGE" 2>/dev/null || true

  local remote_apk="${OCA_PRIV_APP_DIR}/$(basename "$OCA_PRIV_APP_DIR").apk"
  log "Pushing APK → $remote_apk"
  adb_s shell "mkdir -p '${OCA_PRIV_APP_DIR}'"
  adb_s push "$OCA_APK_SIGNED" "$remote_apk"
  adb_s shell "chmod 644 '$remote_apk'"

  local xml_dst="${OCA_PRIVAPP_XML_DST:-/system/etc/permissions/privapp-permissions-opencar.xml}"
  # Rewrite package attribute if integration uses a different applicationId
  local tmp
  tmp="$(mktemp)"
  sed "s/__OCA_PACKAGE__/${OCA_PACKAGE}/g" "$xml_src" >"$tmp"
  log "Pushing whitelist → $xml_dst"
  adb_s push "$tmp" "$xml_dst"
  adb_s shell "chmod 644 '$xml_dst'"
  rm -f "$tmp"

  ok "priv-app staged. Reboot the head unit now:"
  echo "    adb -s ${OCA_HOST}:${OCA_ADB_PORT} reboot"
  echo "  After boot:"
  echo "    $ROOT/tools/oca-setup -i ${INTEGRATION:-antora1000} -H $OCA_HOST grant"
  echo "    $ROOT/tools/oca-setup -i ${INTEGRATION:-antora1000} -H $OCA_HOST check"
  echo "    $ROOT/tools/oca-setup -i ${INTEGRATION:-antora1000} -H $OCA_HOST start"
}
