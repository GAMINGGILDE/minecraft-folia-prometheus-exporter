#!/usr/bin/env bash

set -Eeuo pipefail

server_directory="${1:?Usage: server-smoke-test.sh <server-directory>}"
activation_marker="FoliaPrometheusExporter started."
failure_pattern="(Could not load|Failed to load|Error occurred while (loading|enabling)|Could not pass event).*FoliaPrometheusExporter|Task .*FoliaPrometheusExporter.*exception|FoliaPrometheusExporter.*(Exception|ERROR|SEVERE)|Exception.*FoliaPrometheusExporter"
startup_timeout_seconds=240
shutdown_timeout_seconds=120
server_pid=""

server_directory="$(cd "$server_directory" && pwd)"
server_log="$server_directory/server.log"

if [[ ! -f "$server_directory/server.jar" ]]; then
  echo "Server JAR is missing: $server_directory/server.jar" >&2
  exit 1
fi

if [[ ! -f "$server_directory/plugins/FoliaPrometheusExporter.jar" ]]; then
  echo "Plugin JAR is missing from the server plugin directory." >&2
  exit 1
fi

cleanup() {
  cleanup_exit_code=$?

  if [[ -n "$server_pid" ]] && kill -0 "$server_pid" 2>/dev/null; then
    printf 'stop\n' >&3 2>/dev/null || true
    for ((attempt = 0; attempt < 30; attempt++)); do
      if ! kill -0 "$server_pid" 2>/dev/null; then
        break
      fi
      sleep 1
    done

    if kill -0 "$server_pid" 2>/dev/null; then
      kill -TERM "$server_pid" 2>/dev/null || true
    fi
    wait "$server_pid" 2>/dev/null || true
  fi

  exec 3>&- 2>/dev/null || true

  if [[ $cleanup_exit_code -ne 0 ]] && [[ -f "$server_log" ]]; then
    echo "Last 200 server log lines:" >&2
    tail -n 200 "$server_log" >&2 || true
  fi
}
trap cleanup EXIT

coproc SERVER_PROCESS {
  cd "$server_directory"
  exec java -Xms512M -Xmx1G -jar server.jar --nogui >"$server_log" 2>&1
}
server_pid=$SERVER_PROCESS_PID
exec 3>&"${SERVER_PROCESS[1]}"

activation_found=false
startup_deadline=$((SECONDS + startup_timeout_seconds))

while ((SECONDS < startup_deadline)); do
  if grep -Fq "$activation_marker" "$server_log" 2>/dev/null; then
    activation_found=true
    break
  fi

  if grep -Eiq "$failure_pattern" "$server_log" 2>/dev/null; then
    echo "Plugin load or activation failure detected." >&2
    grep -Ein "$failure_pattern" "$server_log" >&2 || true
    exit 1
  fi

  if ! kill -0 "$server_pid" 2>/dev/null; then
    wait "$server_pid" || true
    server_pid=""
    echo "Server exited before the plugin activation marker appeared." >&2
    exit 1
  fi

  sleep 1
done

if [[ "$activation_found" != true ]]; then
  echo "Timed out after ${startup_timeout_seconds}s waiting for: $activation_marker" >&2
  exit 1
fi

if grep -Eiq "$failure_pattern" "$server_log"; then
  echo "Plugin failure was logged during activation." >&2
  grep -Ein "$failure_pattern" "$server_log" >&2 || true
  exit 1
fi

exporter_base_url="http://127.0.0.1:9940"
health_response="$(curl --fail --silent --show-error --max-time 10 \
  "$exporter_base_url/health")"
ready_response="$(curl --fail --silent --show-error --max-time 10 \
  "$exporter_base_url/ready")"
metrics_response="$(curl --fail --silent --show-error --max-time 10 \
  "$exporter_base_url/metrics")"

if [[ "$health_response" != "ok" ]]; then
  echo "Unexpected /health response: $health_response" >&2
  exit 1
fi

if [[ "$ready_response" != "ready" ]]; then
  echo "Unexpected /ready response: $ready_response" >&2
  exit 1
fi

for required_metric in \
  minecraft_exporter_build_info \
  minecraft_exporter_ready \
  minecraft_exporter_scrapes_total; do
  if ! grep -Fq "$required_metric" <<<"$metrics_response"; then
    echo "Missing exporter metric: $required_metric" >&2
    exit 1
  fi
done

if [[ -n "${EXPECTED_GIT_COMMIT:-}" ]] \
  && ! grep -Fq "git_commit=\"${EXPECTED_GIT_COMMIT}\"" <<<"$metrics_response"; then
  echo "Exporter build_info does not contain expected Git commit ${EXPECTED_GIT_COMMIT}." >&2
  exit 1
fi

echo "Exporter HTTP endpoints confirmed."
echo "Plugin activation confirmed; requesting controlled server shutdown."
printf 'stop\n' >&3

shutdown_deadline=$((SECONDS + shutdown_timeout_seconds))
while kill -0 "$server_pid" 2>/dev/null && ((SECONDS < shutdown_deadline)); do
  sleep 1
done

if kill -0 "$server_pid" 2>/dev/null; then
  echo "Server did not stop within ${shutdown_timeout_seconds}s after the stop command." >&2
  exit 1
fi

set +e
wait "$server_pid"
server_exit_code=$?
set -e
server_pid=""

if [[ $server_exit_code -ne 0 ]]; then
  echo "Server exited with code $server_exit_code after the stop command." >&2
  exit 1
fi

if grep -Eiq "$failure_pattern" "$server_log"; then
  echo "Plugin failure was logged before shutdown completed." >&2
  grep -Ein "$failure_pattern" "$server_log" >&2 || true
  exit 1
fi

if curl --silent --max-time 2 "$exporter_base_url/health" >/dev/null 2>&1; then
  echo "Exporter HTTP server still responds after plugin shutdown." >&2
  exit 1
fi

grep -F "$activation_marker" "$server_log"
echo "Server stopped cleanly."
